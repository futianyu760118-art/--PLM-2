package com.hjgd.plm.subscription.controller;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.subscription.dto.ReportDetailVO;
import com.hjgd.plm.subscription.dto.ReportVO;
import com.hjgd.plm.subscription.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 我的报告（能力点 B5）。
 *
 * <p>报告只对订阅人本人开放：AC-B5-3 要求报告链接带权限校验，
 * 校验点在 {@code ReportService.requireMine}，非订阅人拿到的是「无权限」而不是内容。
 */
@Tag(name = "订阅报告")
@RestController
@RequestMapping("/subscription/report")
@RequiredArgsConstructor
public class ReportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reportService;

    @Operation(summary = "我的报告列表（最近 12 期，AC-B5-7）")
    @GetMapping("")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<PageResult<ReportVO>> page(@RequestParam(required = false) Integer pageNum,
                                             @RequestParam(required = false) Integer pageSize,
                                             @RequestParam(required = false) String type) {
        return Result.success(reportService.page(pageNum, pageSize, type));
    }

    @Operation(summary = "报告详情（含资料明细与投递记录，AC-B5-6）")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<ReportDetailVO> detail(@PathVariable Long id) {
        return Result.success(reportService.detail(id));
    }

    @Operation(summary = "报告 HTML 正文（站内通知/邮件里链接到的页面）")
    @GetMapping("/{id}/html")
    @PreAuthorize("hasAuthority('report:view')")
    public Result<String> html(@PathVariable Long id) {
        return Result.success(reportService.htmlOf(id));
    }

    @Operation(summary = "下载报告 Excel 明细（AC-B5-7、AC-B5-8）")
    @GetMapping("/{id}/excel")
    @PreAuthorize("hasAuthority('report:download')")
    public ResponseEntity<byte[]> excel(@PathVariable Long id) {
        ReportDetailVO detail = reportService.detail(id);
        byte[] bytes = reportService.excelOf(id);
        String name = detail.getReport().getReportNo() + ".xlsx";
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(XLSX)
                .body(bytes);
    }
}
