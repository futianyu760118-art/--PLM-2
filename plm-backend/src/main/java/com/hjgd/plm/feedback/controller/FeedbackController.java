package com.hjgd.plm.feedback.controller;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.feedback.dto.FeedbackQueryDTO;
import com.hjgd.plm.feedback.dto.FeedbackReceiptVO;
import com.hjgd.plm.feedback.dto.FeedbackSubmitDTO;
import com.hjgd.plm.feedback.dto.FeedbackTransitionDTO;
import com.hjgd.plm.feedback.dto.FeedbackVO;
import com.hjgd.plm.feedback.service.FeedbackService;
import com.hjgd.plm.log.annotation.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "全局反馈")
@RestController
@RequestMapping("/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    /**
     * 唯一提交入口(AC F9)。刻意不加 @OperationLog:
     * 切面会把入参 DTO 序列化进操作日志,而 contact 属隐私字段不得留痕(AC F7)。
     */
    @Operation(summary = "提交反馈(唯一入口)")
    @PostMapping
    public Result<FeedbackReceiptVO> submit(@RequestBody FeedbackSubmitDTO dto) {
        return Result.success(feedbackService.submit(dto));
    }

    @Operation(summary = "凭编号查询反馈状态(本人或总控)")
    @GetMapping("/{feedbackNo}")
    public Result<FeedbackVO> getByNo(@PathVariable String feedbackNo) {
        return Result.success(feedbackService.getByNo(feedbackNo));
    }

    @Operation(summary = "总控反馈分页列表")
    @PreAuthorize("hasAuthority('feedback:manage')")
    @GetMapping("/page")
    public Result<PageResult<FeedbackVO>> page(FeedbackQueryDTO query) {
        return Result.success(feedbackService.page(query));
    }

    @Operation(summary = "总控反馈状态流转")
    @OperationLog(value = "反馈状态流转")
    @PreAuthorize("hasAuthority('feedback:manage')")
    @PostMapping("/{id}/transition")
    public Result<Void> transition(@PathVariable Long id, @RequestBody FeedbackTransitionDTO dto) {
        feedbackService.transition(id, dto);
        return Result.success();
    }
}
