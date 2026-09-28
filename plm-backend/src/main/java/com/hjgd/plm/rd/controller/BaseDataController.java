package com.hjgd.plm.rd.controller;

import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.rd.BaseDataDefs;
import com.hjgd.plm.rd.RdSheetDefs;
import com.hjgd.plm.rd.service.GenericTableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 系统管理 - 基础数据 (M_客户/产品/物料/供应商/人员/部门)。
 */
@Tag(name = "系统管理-基础数据")
@RestController
@RequestMapping({"/system/base", "/v1/system/base"})
@RequiredArgsConstructor
public class BaseDataController {

    private final GenericTableService tableService;

    private RdSheetDefs.Def def(String type) {
        RdSheetDefs.Def d = BaseDataDefs.get(type);
        if (d == null) throw new BusinessException("未知基础表: " + type);
        return d;
    }

    @Operation(summary = "基础表定义")
    @GetMapping("/meta")
    public Result<List<Map<String, Object>>> meta() {
        return Result.success(tableService.meta(BaseDataDefs.DEFS.values()));
    }

    @Operation(summary = "列表")
    @GetMapping("/{type}")
    public Result<Map<String, Object>> list(@PathVariable String type,
                                            @RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String keyword) {
        return Result.success(tableService.list(def(type), pageNum, pageSize, keyword, null, null));
    }

    @Operation(summary = "详情")
    @GetMapping("/{type}/{id}")
    public Result<Map<String, Object>> get(@PathVariable String type, @PathVariable Long id) {
        return Result.success(tableService.get(def(type), id));
    }

    @Operation(summary = "新增")
    @PostMapping("/{type}")
    public Result<Map<String, Object>> create(@PathVariable String type, @RequestBody Map<String, Object> body) {
        return Result.success(tableService.create(def(type), body));
    }

    @Operation(summary = "修改")
    @PutMapping("/{type}")
    public Result<Map<String, Object>> update(@PathVariable String type, @RequestBody Map<String, Object> body) {
        return Result.success(tableService.update(def(type), body));
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{type}/{id}")
    public Result<Void> delete(@PathVariable String type, @PathVariable Long id) {
        tableService.delete(def(type), id);
        return Result.success();
    }

    @Operation(summary = "导出 Excel")
    @GetMapping("/{type}/export")
    public void export(@PathVariable String type, HttpServletResponse response) throws Exception {
        byte[] bytes = tableService.export(def(type));
        String name = URLEncoder.encode("base-" + type + ".xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + name);
        try (OutputStream os = response.getOutputStream()) { os.write(bytes); }
    }

    @Operation(summary = "导入 Excel")
    @PostMapping("/{type}/import")
    public Result<Map<String, Object>> importExcel(@PathVariable String type,
                                                   @RequestParam("file") MultipartFile file) {
        return Result.success(Map.of("imported", tableService.importData(def(type), file)));
    }
}
