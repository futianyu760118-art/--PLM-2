package com.hjgd.plm.rd.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.rd.service.ChangeLogService;
import com.hjgd.plm.rd.service.NodeRollupService;
import com.hjgd.plm.rd.service.RdSheetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 项目工作表 (规格书/配置表/样品单/评审单/测试报告/试产报告/出货)。
 * 填写工作表 -> 统计到项目明细表 -> 汇总到研发项目跟踪。
 */
@Tag(name = "研发自治中心-项目工作表")
@RestController
@RequestMapping({"/v1/rd/sheets", "/rd/sheets"})
@RequiredArgsConstructor
public class RdSheetController {

    private final RdSheetService sheetService;
    private final ChangeLogService changeLogService;
    private final NodeRollupService rollupService;

    /** 变更留痕 + 自动同步引用(汇总到节点/跟踪) */
    private void afterChange(String type, String projectNo, Object objectId, String action) {
        changeLogService.record(null, projectNo, type.toUpperCase(), objectId, null, action,
                null, null, null, "UI");
        if (StringUtils.hasText(projectNo)) {
            try { rollupService.rollupByProjectNo(projectNo); } catch (Exception ignore) {}
        }
    }

    @Operation(summary = "工作表定义(类型/列)")
    @GetMapping("/meta")
    public Result<List<Map<String, Object>>> meta() {
        return Result.success(sheetService.meta());
    }

    @Operation(summary = "工作表上下文(自动引用项目明细表所选项目)")
    @GetMapping("/context")
    public Result<Map<String, Object>> context(@RequestParam(required = false) String projectNo) {
        return Result.success(sheetService.context(projectNo));
    }

    @Operation(summary = "列表")
    @GetMapping("/{type}")
    public Result<Map<String, Object>> list(@PathVariable String type,
                                            @RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String projectNo) {
        return Result.success(sheetService.list(type, pageNum, pageSize, keyword, projectNo));
    }

    @Operation(summary = "详情")
    @GetMapping("/{type}/{id}")
    public Result<Map<String, Object>> get(@PathVariable String type, @PathVariable Long id) {
        return Result.success(sheetService.get(type, id));
    }

    @Operation(summary = "新增")
    @PostMapping("/{type}")
    public Result<Map<String, Object>> create(@PathVariable String type, @RequestBody Map<String, Object> body) {
        Map<String, Object> r = sheetService.create(type, body);
        afterChange(type, str(body.get("project_no")), body.get("id"), "CREATE");
        return Result.success(r);
    }

    @Operation(summary = "修改")
    @PutMapping("/{type}")
    public Result<Map<String, Object>> update(@PathVariable String type, @RequestBody Map<String, Object> body) {
        Map<String, Object> r = sheetService.update(type, body);
        afterChange(type, str(body.get("project_no")), body.get("id"), "UPDATE");
        return Result.success(r);
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{type}/{id}")
    public Result<Void> delete(@PathVariable String type, @PathVariable Long id) {
        String projectNo = null;
        try { projectNo = str(sheetService.get(type, id).get("project_no")); } catch (Exception ignore) {}
        sheetService.delete(type, id);
        afterChange(type, projectNo, id, "DELETE");
        return Result.success();
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }

    @Operation(summary = "导出 Excel")
    @GetMapping("/{type}/export")
    public void export(@PathVariable String type, HttpServletResponse response) throws Exception {
        byte[] bytes = sheetService.export(type);
        String name = URLEncoder.encode("rd-sheet-" + type + ".xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + name);
        try (OutputStream os = response.getOutputStream()) {
            os.write(bytes);
        }
    }

    @Operation(summary = "导入 Excel")
    @PostMapping("/{type}/import")
    public Result<Map<String, Object>> importExcel(@PathVariable String type,
                                                   @RequestParam("file") MultipartFile file) {
        int n = sheetService.importData(type, file);
        return Result.success(Map.of("imported", n));
    }
}
