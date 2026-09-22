package com.hjgd.plm.process.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.log.annotation.OperationLog;
import com.hjgd.plm.process.entity.ProcessRoute;
import com.hjgd.plm.process.entity.ProcessStep;
import com.hjgd.plm.process.mapper.ProcessRouteMapper;
import com.hjgd.plm.process.service.ProcessFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "工序自动化流转")
@RestController
@RequestMapping("/process")
@RequiredArgsConstructor
public class ProcessFlowController {

    private final ProcessFlowService service;
    private final ProcessRouteMapper routeMapper;

    @Operation(summary = "工序路线列表")
    @GetMapping("/routes")
    public Result<PageResult<ProcessRoute>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "20") int pageSize,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<ProcessRoute> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) w.eq(ProcessRoute::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(ProcessRoute::getRouteName, keyword)
                    .or().like(ProcessRoute::getRouteNo, keyword)
                    .or().like(ProcessRoute::getRefId, keyword));
        }
        w.orderByDesc(ProcessRoute::getId);
        return Result.success(PageResult.of(routeMapper.selectPage(new Page<>(pageNum, pageSize), w)));
    }

    @Operation(summary = "路线详情(含全部工序及进度)")
    @GetMapping("/routes/{id}")
    public Result<ProcessFlowService.RouteDetail> detail(@PathVariable Long id) {
        return Result.success(service.detail(id));
    }

    @Operation(summary = "我的当前工序")
    @GetMapping("/my-steps")
    public Result<List<Map<String, Object>>> mySteps() {
        Long userId = com.hjgd.plm.auth.security.SecurityUtils.getCurrentUserId();
        List<Map<String, Object>> out = service.myActiveSteps(userId).stream().<Map<String, Object>>map(step -> {
            ProcessRoute route = routeMapper.selectById(step.getRouteId());
            return Map.of(
                    "step", (Object) step,
                    "routeNo", route == null ? "" : route.getRouteNo(),
                    "routeName", route == null ? "" : route.getRouteName());
        }).toList();
        return Result.success(out);
    }

    @Operation(summary = "创建工序路线(自动激活第一道并推送待办)")
    @OperationLog(value = "创建工序路线")
    @PostMapping("/routes")
    public Result<ProcessRoute> create(@RequestBody ProcessFlowService.CreateReq req) {
        return Result.success(service.createRoute(req));
    }

    @Operation(summary = "完成当前工序(自动推送下一道)")
    @OperationLog(value = "完成工序")
    @PostMapping("/steps/{id}/complete")
    public Result<ProcessFlowService.RouteDetail> complete(@PathVariable Long id,
                                                           @RequestBody(required = false) RemarkReq body) {
        return Result.success(service.completeStep(id, body == null ? null : body.getRemark()));
    }

    @Operation(summary = "跳过当前工序(留痕, 自动推送下一道)")
    @OperationLog(value = "跳过工序")
    @PostMapping("/steps/{id}/skip")
    public Result<ProcessFlowService.RouteDetail> skip(@PathVariable Long id,
                                                       @RequestBody(required = false) RemarkReq body) {
        return Result.success(service.skipStep(id, body == null ? null : body.getRemark()));
    }

    @Operation(summary = "取消工序路线")
    @OperationLog(value = "取消工序路线")
    @PostMapping("/routes/{id}/cancel")
    public Result<ProcessFlowService.RouteDetail> cancel(@PathVariable Long id) {
        return Result.success(service.cancelRoute(id));
    }

    @Data
    public static class RemarkReq {
        private String remark;
    }
}
