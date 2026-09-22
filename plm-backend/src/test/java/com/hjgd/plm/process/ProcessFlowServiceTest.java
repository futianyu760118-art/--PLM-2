package com.hjgd.plm.process;

import com.hjgd.plm.event.service.DomainEventService;
import com.hjgd.plm.process.entity.ProcessRoute;
import com.hjgd.plm.process.entity.ProcessStep;
import com.hjgd.plm.process.mapper.ProcessRouteMapper;
import com.hjgd.plm.process.mapper.ProcessStepMapper;
import com.hjgd.plm.process.service.ProcessFlowService;
import com.hjgd.plm.system.service.SequenceService;
import com.hjgd.plm.workitem.entity.WorkItem;
import com.hjgd.plm.workitem.mapper.WorkItemMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 工序自动化流转: 完成上一道 → 自动推送下一道待办 → 末道完成自动闭环。
 */
@DisplayName("工序自动化流转")
@ExtendWith(MockitoExtension.class)
class ProcessFlowServiceTest {

    @Mock private ProcessRouteMapper routeMapper;
    @Mock private ProcessStepMapper stepMapper;
    @Mock private WorkItemMapper workItemMapper;
    @Mock private SequenceService sequenceService;
    @Mock private DomainEventService eventService;
    @InjectMocks private ProcessFlowService service;

    private ProcessRoute runningRoute(long id) {
        ProcessRoute r = new ProcessRoute();
        r.setId(id);
        r.setRouteNo("PR202609160001");
        r.setRouteName("投光灯试制工序");
        r.setStatus("RUNNING");
        return r;
    }

    private ProcessStep step(long id, long routeId, int order, String status) {
        ProcessStep s = new ProcessStep();
        s.setId(id);
        s.setRouteId(routeId);
        s.setStepOrder(order);
        s.setName("工序" + order);
        s.setOwnerId(7L);
        s.setSlaHours(24);
        s.setStatus(status);
        return s;
    }

    @Test
    @DisplayName("创建路线: 自动激活第一道工序并向负责人推送待办")
    void shouldActivateFirstStepOnCreate() {
        when(sequenceService.nextNo(anyString())).thenReturn("PR1", "WI1");
        when(routeMapper.insert(any(ProcessRoute.class))).thenAnswer(inv -> {
            inv.getArgument(0, ProcessRoute.class).setId(1L);
            return 1;
        });
        when(workItemMapper.insert(any(WorkItem.class))).thenAnswer(inv -> {
            inv.getArgument(0, WorkItem.class).setId(100L);
            return 1;
        });
        when(stepMapper.insert(any(ProcessStep.class))).thenAnswer(inv -> {
            ProcessStep s = inv.getArgument(0);
            s.setId(s.getStepOrder() * 10L);
            return 1;
        });
        ProcessStep firstStep = step(10L, 1L, 1, "PENDING");
        firstStep.setName("结构设计");
        when(stepMapper.selectOne(any())).thenReturn(firstStep);

        ProcessFlowService.CreateReq req = new ProcessFlowService.CreateReq();
        req.setRouteName("投光灯试制工序");
        req.setRefType("PROJECT");
        req.setRefId("NPI-001");
        ProcessFlowService.StepReq s1 = new ProcessFlowService.StepReq();
        s1.setName("结构设计"); s1.setOwnerId(7L); s1.setSlaHours(48);
        ProcessFlowService.StepReq s2 = new ProcessFlowService.StepReq();
        s2.setName("模具开发"); s2.setOwnerId(8L); s2.setSlaHours(72);
        req.setSteps(List.of(s1, s2));

        ProcessRoute route = service.createRoute(req);

        assertEquals("RUNNING", route.getStatus());
        // 第一道工序被激活
        ArgumentCaptor<ProcessStep> stepCap = ArgumentCaptor.forClass(ProcessStep.class);
        verify(stepMapper, atLeastOnce()).updateById(stepCap.capture());
        ProcessStep activated = stepCap.getAllValues().stream()
                .filter(s -> s.getId() != null && s.getId() == 10L && "ACTIVE".equals(s.getStatus()))
                .findFirst().orElseThrow();
        assertNotNull(activated.getStartedAt());
        assertNotNull(activated.getWorkItemId());
        // 待办已推送给负责人
        ArgumentCaptor<WorkItem> wiCap = ArgumentCaptor.forClass(WorkItem.class);
        verify(workItemMapper).insert(wiCap.capture());
        WorkItem item = wiCap.getValue();
        assertEquals("PROCESS", item.getType());
        assertEquals("PROCESS_STEP", item.getRefType());
        assertEquals("10", item.getRefId());
        assertEquals(7L, item.getOwnerId());
        assertEquals("OPEN", item.getStatus());
        assertNotNull(item.getSlaDueAt());
        assertTrue(item.getTitle().contains("结构设计"));
    }

    @Test
    @DisplayName("完成中间工序: 本道闭环 + 自动激活下一道并生成其待办")
    void shouldPushNextStepOnComplete() {
        ProcessStep step1 = step(10L, 1L, 1, "ACTIVE");
        step1.setWorkItemId(100L);
        ProcessStep step2 = step(20L, 1L, 2, "PENDING");
        ProcessRoute route = runningRoute(1L);
        when(stepMapper.selectById(10L)).thenReturn(step1);
        when(routeMapper.selectById(1L)).thenReturn(route);
        WorkItem oldItem = new WorkItem();
        oldItem.setId(100L);
        oldItem.setStatus("OPEN");
        when(workItemMapper.selectById(100L)).thenReturn(oldItem);
        when(sequenceService.nextNo(anyString())).thenReturn("WI2");
        when(workItemMapper.insert(any(WorkItem.class))).thenAnswer(inv -> {
            inv.getArgument(0, WorkItem.class).setId(200L);
            return 1;
        });
        when(stepMapper.selectOne(any())).thenReturn(step2);
        when(stepMapper.selectList(any())).thenReturn(List.of(step1, step2));

        ProcessFlowService.RouteDetail d = service.completeStep(10L, "结构图纸完成");

        assertEquals("DONE", step1.getStatus());
        assertNotNull(step1.getCompletedAt());
        assertEquals("结构图纸完成", step1.getRemark());
        // 旧待办被闭环
        assertEquals("DONE", oldItem.getStatus());
        // 下一道被激活并收到新待办
        assertEquals("ACTIVE", step2.getStatus());
        assertNotNull(step2.getWorkItemId());
        ArgumentCaptor<WorkItem> wiCap = ArgumentCaptor.forClass(WorkItem.class);
        verify(workItemMapper).insert(wiCap.capture());
        assertEquals("20", wiCap.getValue().getRefId());
        // 路线仍在进行中, 指向第二道
        assertEquals("RUNNING", route.getStatus());
        assertEquals(20L, route.getCurrentStepId());
        assertEquals(2, d.getTotalCount());
        assertEquals(1, d.getDoneCount());
        verify(eventService).publish(eq("PROCESS_STEP_PUSHED"), eq("PROCESS_STEP"), eq("20"), anyMap());
    }

    @Test
    @DisplayName("完成末道工序: 路线自动闭环 COMPLETED")
    void shouldCompleteRouteOnLastStep() {
        ProcessStep last = step(30L, 1L, 3, "ACTIVE");
        last.setWorkItemId(300L);
        ProcessRoute route = runningRoute(1L);
        when(stepMapper.selectById(30L)).thenReturn(last);
        when(routeMapper.selectById(1L)).thenReturn(route);
        when(stepMapper.selectOne(any())).thenReturn(null);
        when(stepMapper.selectList(any())).thenReturn(List.of(last));

        service.completeStep(30L, null);

        assertEquals("COMPLETED", route.getStatus());
        assertNotNull(route.getCompletedAt());
        assertNull(route.getCurrentStepId());
        verify(eventService).publish(eq("PROCESS_ROUTE_COMPLETED"), eq("PROCESS_ROUTE"), anyString(), anyMap());
        verify(workItemMapper, never()).insert(any());
    }

    @Test
    @DisplayName("待办完成联动: onWorkItemCompleted 自动流转")
    void shouldAdvanceFromWorkItemCompletion() {
        ProcessStep step1 = step(10L, 1L, 1, "ACTIVE");
        step1.setWorkItemId(100L);
        ProcessStep step2 = step(20L, 1L, 2, "PENDING");
        ProcessRoute route = runningRoute(1L);
        when(stepMapper.selectById(10L)).thenReturn(step1);
        when(routeMapper.selectById(1L)).thenReturn(route);
        when(sequenceService.nextNo(anyString())).thenReturn("WI2");
        when(stepMapper.selectOne(any())).thenReturn(step2);
        when(stepMapper.selectList(any())).thenReturn(List.of(step1, step2));

        service.onWorkItemCompleted(10L);

        assertEquals("DONE", step1.getStatus());
        assertEquals("ACTIVE", step2.getStatus());
        verify(workItemMapper).insert(any(WorkItem.class));
    }

    @Test
    @DisplayName("非进行中的工序不可流转")
    void shouldRejectNonActiveStep() {
        ProcessStep pending = step(20L, 1L, 2, "PENDING");
        when(stepMapper.selectById(20L)).thenReturn(pending);

        assertThrows(com.hjgd.plm.common.BusinessException.class,
                () -> service.completeStep(20L, null));
        verify(workItemMapper, never()).insert(any());
    }
}
