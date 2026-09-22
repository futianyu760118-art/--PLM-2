package com.hjgd.plm.process.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.event.service.DomainEventService;
import com.hjgd.plm.process.entity.ProcessRoute;
import com.hjgd.plm.process.entity.ProcessStep;
import com.hjgd.plm.process.mapper.ProcessRouteMapper;
import com.hjgd.plm.process.mapper.ProcessStepMapper;
import com.hjgd.plm.system.service.SequenceService;
import com.hjgd.plm.workitem.entity.WorkItem;
import com.hjgd.plm.workitem.mapper.WorkItemMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工序自动化流转: 上一道工序完成 → 自动激活下一道并向其负责人推送待办,
 * 全部完成后路线自动闭环。
 */
@Service
@RequiredArgsConstructor
public class ProcessFlowService {

    private final ProcessRouteMapper routeMapper;
    private final ProcessStepMapper stepMapper;
    private final WorkItemMapper workItemMapper;
    private final SequenceService sequenceService;
    private final DomainEventService eventService;

    @Data
    public static class StepReq {
        private String name;
        private Long ownerId;
        private Integer slaHours;
    }

    @Data
    public static class CreateReq {
        private String routeName;
        private String refType;
        private String refId;
        private List<StepReq> steps;
    }

    @Data
    public static class RouteDetail {
        private ProcessRoute route;
        private List<ProcessStep> steps;
        private int doneCount;
        private int totalCount;
    }

    @Transactional
    public ProcessRoute createRoute(CreateReq req) {
        if (req.getSteps() == null || req.getSteps().isEmpty()) {
            throw new BusinessException("工序路线至少需要一道工序");
        }
        Long userId = null;
        try { userId = com.hjgd.plm.auth.security.SecurityUtils.getCurrentUserId(); } catch (Exception ignored) {}

        ProcessRoute route = new ProcessRoute();
        route.setRouteNo(sequenceService.nextNo("PROCESS_NO"));
        route.setRouteName(req.getRouteName());
        route.setRefType(req.getRefType());
        route.setRefId(req.getRefId());
        route.setStatus("RUNNING");
        route.setCreatedBy(userId);
        route.setCreatedAt(LocalDateTime.now());
        routeMapper.insert(route);

        int order = 0;
        for (StepReq s : req.getSteps()) {
            if (s.getName() == null || s.getName().isBlank()) {
                throw new BusinessException("第" + (order + 1) + "道工序名称不能为空");
            }
            ProcessStep step = new ProcessStep();
            step.setRouteId(route.getId());
            step.setStepOrder(++order);
            step.setName(s.getName().trim());
            step.setOwnerId(s.getOwnerId());
            step.setSlaHours(s.getSlaHours());
            step.setStatus("PENDING");
            stepMapper.insert(step);
        }

        // 自动激活第一道工序
        ProcessStep first = stepMapper.selectOne(new LambdaQueryWrapper<ProcessStep>()
                .eq(ProcessStep::getRouteId, route.getId())
                .orderByAsc(ProcessStep::getStepOrder)
                .last("LIMIT 1"));
        activateStep(route, first);
        eventService.publish("PROCESS_ROUTE_STARTED", "PROCESS_ROUTE", route.getRouteNo(),
                Map.of("routeName", route.getRouteName(), "firstStep", first.getName()));
        return route;
    }

    /** 完成当前工序, 自动推送下一道成为其负责人待办; 最后一道完成则路线闭环。 */
    @Transactional
    public RouteDetail completeStep(Long stepId, String remark) {
        return advance(stepId, "DONE", remark);
    }

    /** 异常跳过当前工序(留痕), 同样自动推送下一道。 */
    @Transactional
    public RouteDetail skipStep(Long stepId, String remark) {
        return advance(stepId, "SKIPPED", remark);
    }

    /** 待办完成联动: 我的待办中完成 PROCESS_STEP 类型待办时自动流转。 */
    @Transactional
    public RouteDetail onWorkItemCompleted(Long stepId) {
        return advance(stepId, "DONE", "待办完成, 自动流转下一道工序");
    }

    private RouteDetail advance(Long stepId, String resultStatus, String remark) {
        ProcessStep step = stepMapper.selectById(stepId);
        if (step == null) throw new BusinessException("工序不存在: " + stepId);
        if (!"ACTIVE".equals(step.getStatus())) throw new BusinessException("仅进行中的工序可操作, 当前状态: " + step.getStatus());
        ProcessRoute route = routeMapper.selectById(step.getRouteId());
        if (route == null) throw new BusinessException("工序路线不存在");
        if (!"RUNNING".equals(route.getStatus())) throw new BusinessException("路线已" + route.getStatus() + ", 不可流转");

        // 1. 关闭本道工序及其待办
        step.setStatus(resultStatus);
        step.setCompletedAt(LocalDateTime.now());
        if (remark != null && !remark.isBlank()) step.setRemark(remark);
        stepMapper.updateById(step);
        closeWorkItem(step);

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeNo", route.getRouteNo());
        payload.put("stepOrder", step.getStepOrder());
        payload.put("stepName", step.getName());
        eventService.publish("PROCESS_STEP_" + resultStatus, "PROCESS_STEP", String.valueOf(step.getId()), payload);

        // 2. 找下一道待处理工序, 自动推送
        ProcessStep next = stepMapper.selectOne(new LambdaQueryWrapper<ProcessStep>()
                .eq(ProcessStep::getRouteId, route.getId())
                .eq(ProcessStep::getStatus, "PENDING")
                .gt(ProcessStep::getStepOrder, step.getStepOrder())
                .orderByAsc(ProcessStep::getStepOrder)
                .last("LIMIT 1"));

        if (next == null) {
            route.setStatus("COMPLETED");
            route.setCompletedAt(LocalDateTime.now());
            route.setCurrentStepId(null);
            routeMapper.updateById(route);
            eventService.publish("PROCESS_ROUTE_COMPLETED", "PROCESS_ROUTE", route.getRouteNo(),
                    Map.of("routeName", route.getRouteName()));
        } else {
            activateStep(route, next);
        }
        return detail(route.getId());
    }

    /** 激活工序: 置为进行中 + 向负责人推送待办。 */
    private void activateStep(ProcessRoute route, ProcessStep step) {
        LocalDateTime now = LocalDateTime.now();
        WorkItem item = new WorkItem();
        item.setItemNo(sequenceService.nextNo("WORK_ITEM"));
        item.setType("PROCESS");
        item.setTitle("【工序】" + route.getRouteName() + " · " + step.getName());
        item.setRefType("PROCESS_STEP");
        item.setRefId(String.valueOf(step.getId()));
        item.setPriority(2);
        item.setOwnerId(step.getOwnerId());
        item.setStatus("OPEN");
        item.setCreatedAt(now);
        if (step.getSlaHours() != null && step.getSlaHours() > 0) {
            item.setSlaDueAt(now.plusHours(step.getSlaHours()));
        }
        workItemMapper.insert(item);

        step.setStatus("ACTIVE");
        step.setStartedAt(now);
        step.setWorkItemId(item.getId());
        stepMapper.updateById(step);

        route.setCurrentStepId(step.getId());
        routeMapper.updateById(route);

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeNo", route.getRouteNo());
        payload.put("stepOrder", step.getStepOrder());
        payload.put("stepName", step.getName());
        payload.put("ownerId", step.getOwnerId());
        payload.put("workItemNo", item.getItemNo());
        eventService.publish("PROCESS_STEP_PUSHED", "PROCESS_STEP", String.valueOf(step.getId()), payload);
    }

    private void closeWorkItem(ProcessStep step) {
        if (step.getWorkItemId() == null) return;
        WorkItem item = workItemMapper.selectById(step.getWorkItemId());
        if (item != null && !"DONE".equals(item.getStatus())) {
            item.setStatus("DONE");
            item.setCompletedAt(LocalDateTime.now());
            workItemMapper.updateById(item);
        }
    }

    @Transactional
    public RouteDetail cancelRoute(Long routeId) {
        ProcessRoute route = routeMapper.selectById(routeId);
        if (route == null) throw new BusinessException("路线不存在");
        if (!"RUNNING".equals(route.getStatus())) throw new BusinessException("仅进行中的路线可取消");
        ProcessStep current = route.getCurrentStepId() == null ? null : stepMapper.selectById(route.getCurrentStepId());
        if (current != null && "ACTIVE".equals(current.getStatus())) closeWorkItem(current);
        route.setStatus("CANCELLED");
        route.setCurrentStepId(null);
        routeMapper.updateById(route);
        eventService.publish("PROCESS_ROUTE_CANCELLED", "PROCESS_ROUTE", route.getRouteNo(),
                Map.of("routeName", route.getRouteName()));
        return detail(routeId);
    }

    public RouteDetail detail(Long routeId) {
        ProcessRoute route = routeMapper.selectById(routeId);
        if (route == null) throw new BusinessException("路线不存在");
        List<ProcessStep> steps = stepMapper.selectList(new LambdaQueryWrapper<ProcessStep>()
                .eq(ProcessStep::getRouteId, routeId)
                .orderByAsc(ProcessStep::getStepOrder));
        RouteDetail d = new RouteDetail();
        d.setRoute(route);
        d.setSteps(steps);
        d.setTotalCount(steps.size());
        d.setDoneCount((int) steps.stream().filter(s -> "DONE".equals(s.getStatus()) || "SKIPPED".equals(s.getStatus())).count());
        return d;
    }

    /** 我的当前工序(进行中且负责人是我)。 */
    public List<ProcessStep> myActiveSteps(Long userId) {
        return stepMapper.selectList(new LambdaQueryWrapper<ProcessStep>()
                .eq(ProcessStep::getOwnerId, userId)
                .eq(ProcessStep::getStatus, "ACTIVE")
                .orderByAsc(ProcessStep::getStartedAt));
    }
}
