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

    /**
     * 操作日志须记录这次流转「流到哪、为什么」: 该接口首个入参是路径变量 id,
     * 默认取 args[0] 只会留下无意义的 "1", 无法还原变更轨迹(AC F16a)。
     * note 不含联系方式的隐私问题, 但它可能很长, 故截断到 500 字符。
     */
    @Operation(summary = "总控反馈状态流转")
    @OperationLog(value = "反馈状态流转",
            params = "'id=' + #id + ', targetStatus=' + (#dto.targetStatus ?: '')"
                    + " + ', relatedIssueNo=' + (#dto.relatedIssueNo ?: '')"
                    + " + ', note=' + (#dto.note != null && #dto.note.length() > 500 ? #dto.note.substring(0, 500) : (#dto.note ?: ''))")
    @PreAuthorize("hasAuthority('feedback:manage')")
    @PostMapping("/{id}/transition")
    public Result<Void> transition(@PathVariable Long id, @RequestBody FeedbackTransitionDTO dto) {
        feedbackService.transition(id, dto);
        return Result.success();
    }
}
