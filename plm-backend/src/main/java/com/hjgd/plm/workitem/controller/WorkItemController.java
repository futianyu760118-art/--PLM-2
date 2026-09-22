package com.hjgd.plm.workitem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.system.service.SequenceService;
import com.hjgd.plm.workitem.entity.WorkItem;
import com.hjgd.plm.workitem.mapper.WorkItemMapper;
import com.hjgd.plm.process.service.ProcessFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "V1 待办拉动")
@RestController
@RequestMapping("/v1/work-items")
@RequiredArgsConstructor
public class WorkItemController {

    private final WorkItemMapper mapper;
    private final SequenceService sequenceService;
    private final ProcessFlowService processFlowService;

    @Operation(summary = "我的待办")
    @GetMapping("/my")
    public Result<List<WorkItem>> my(@RequestParam(required = false) String status) {
        Long userId = null;
        try { userId = SecurityUtils.getCurrentUserId(); } catch (Exception ignored) {}
        LambdaQueryWrapper<WorkItem> w = new LambdaQueryWrapper<>();
        if (userId != null) w.eq(WorkItem::getOwnerId, userId);
        if (status != null) w.eq(WorkItem::getStatus, status);
        else w.in(WorkItem::getStatus, List.of("OPEN", "ESCALATED"));
        w.orderByAsc(WorkItem::getPriority).orderByAsc(WorkItem::getSlaDueAt);
        return Result.success(mapper.selectList(w));
    }

    @Operation(summary = "待办列表")
    @GetMapping
    public Result<PageResult<WorkItem>> list(@RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "20") int pageSize,
                                            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<WorkItem> w = new LambdaQueryWrapper<>();
        if (status != null) w.eq(WorkItem::getStatus, status);
        w.orderByAsc(WorkItem::getSlaDueAt);
        return Result.success(PageResult.of(mapper.selectPage(new Page<>(pageNum, pageSize), w)));
    }

    @Operation(summary = "创建待办")
    @PostMapping
    public Result<WorkItem> create(@RequestBody WorkItem item) {
        if (item.getItemNo() == null) item.setItemNo(sequenceService.nextNo("WORK_ITEM"));
        if (item.getStatus() == null) item.setStatus("OPEN");
        if (item.getPriority() == null) item.setPriority(3);
        item.setCreatedAt(LocalDateTime.now());
        mapper.insert(item);
        return Result.success(item);
    }

    @Operation(summary = "完成待办")
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        WorkItem item = mapper.selectById(id);
        if (item == null) return Result.failed(404, "不存在");
        if ("DONE".equals(item.getStatus())) return Result.success();
        item.setStatus("DONE");
        item.setCompletedAt(LocalDateTime.now());
        mapper.updateById(item);
        // 工序待办联动: 完成 PROCESS_STEP 待办 → 自动流转下一道工序
        if ("PROCESS_STEP".equals(item.getRefType()) && item.getRefId() != null) {
            processFlowService.onWorkItemCompleted(Long.valueOf(item.getRefId()));
        }
        return Result.success();
    }

    @Data
    public static class CreateReq {
        private String type;
        private String title;
        private String refType;
        private String refId;
        private Integer priority;
        private Long ownerId;
        private Integer slaHours;
    }
}
