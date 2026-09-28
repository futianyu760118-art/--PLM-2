package com.hjgd.plm.material.controller;

import com.alibaba.excel.EasyExcel;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.material.dto.MaterialDTO;
import com.hjgd.plm.material.enums.MaterialType;
import com.hjgd.plm.material.service.MaterialService;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Tag(name = "V1 物料导入")
@RestController
@RequestMapping("/v1/parts")
@RequiredArgsConstructor
public class MaterialImportController {

    private final MaterialService materialService;

    @Operation(summary = "批量导入物料(JSON)")
    @PreAuthorize("hasAuthority('material:add')")
    @PostMapping("/import")
    public Result<Map<String, Object>> importParts(@RequestBody ImportBody body) {
        return Result.success(importRows(body.getProducts() == null ? List.of() : body.getProducts()));
    }

    @Operation(summary = "批量导入物料(文件: xlsx/xls/csv/pdf)")
    @PreAuthorize("hasAuthority('material:add')")
    @PostMapping("/import-file")
    public Result<Map<String, Object>> importFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("请上传文件");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        List<List<String>> grid;
        try {
            if (name.endsWith(".pdf")) grid = parsePdf(file.getInputStream());
            else if (name.endsWith(".csv")) grid = parseCsv(file.getInputStream());
            else if (name.endsWith(".xlsx") || name.endsWith(".xls")) grid = parseExcel(file.getInputStream());
            else throw new BusinessException("不支持的文件格式(支持 xlsx/xls/csv/pdf)");
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("解析失败: " + e.getMessage());
        }
        Map<String, Object> out = new LinkedHashMap<>(importRows(gridToRows(grid)));
        out.put("parsed", grid.isEmpty() ? 0 : grid.size() - 1);
        return Result.success(out);
    }

    // ---------- 导入 ----------
    private Map<String, Object> importRows(List<ImportRow> rows) {
        int success = 0, skip = 0;
        List<String> errors = new ArrayList<>();
        for (ImportRow row : rows) {
            try {
                if (row.getPartNo() != null && materialService.checkPartNoDuplicate(row.getPartNo(), null)) {
                    skip++;
                    continue;
                }
                MaterialDTO dto = new MaterialDTO();
                dto.setPartNo(row.getPartNo());
                dto.setMaterialName(row.getMaterialName() != null ? row.getMaterialName() : row.getNameZh());
                dto.setNameEn(row.getNameEn());
                dto.setMaterialType(parseType(row.getMaterialType(), row.getProductType()));
                dto.setProductType(row.getProductType());
                dto.setSpecification(row.getSpecification());
                dto.setUnit(row.getUnit() != null ? row.getUnit() : "PCS");
                dto.setProjectNo(row.getProjectNo());
                materialService.create(dto);
                success++;
            } catch (Exception e) {
                errors.add((row.getPartNo() != null ? row.getPartNo() : "?") + ": " + e.getMessage());
                log.warn("import row failed: {}", e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", success);
        out.put("skipped", skip);
        out.put("errors", errors);
        return out;
    }

    // ---------- 表格 -> ImportRow ----------
    private static final Map<String, String> HEADER_ALIAS = new LinkedHashMap<>();

    private static void alias(String field, String... names) {
        for (String n : names) HEADER_ALIAS.put(n.toLowerCase(Locale.ROOT), field);
    }

    static {
        alias("partNo", "料号", "物料编码", "物料代码", "零件号", "编码", "partno", "part_no", "part");
        alias("materialName", "物料名称", "名称", "品名", "物料名称(中文)", "中文名称", "materialname");
        alias("nameEn", "英文名称", "英文名", "name_en", "englishname", "英文");
        alias("materialType", "物料类型", "类型", "物料类别", "materialtype");
        alias("productType", "产品类型", "产品类别", "品类", "producttype");
        alias("specification", "规格", "规格型号", "规格描述", "specification", "spec");
        alias("unit", "单位", "基本单位", "unit");
        alias("projectNo", "项目号", "项目编号", "projectno", "project_no");
    }

    private List<ImportRow> gridToRows(List<List<String>> grid) {
        List<ImportRow> out = new ArrayList<>();
        if (grid == null || grid.isEmpty()) return out;
        List<String> header = grid.get(0);
        Map<Integer, String> colField = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i) == null ? "" : header.get(i).trim().toLowerCase(Locale.ROOT);
            if (h.isEmpty()) continue;
            String f = HEADER_ALIAS.get(h);
            if (f != null && !colField.containsValue(f)) colField.put(i, f);
        }
        if (colField.isEmpty()) throw new BusinessException("未识别到有效表头(需含 料号/物料名称 等)");
        for (int r = 1; r < grid.size(); r++) {
            List<String> line = grid.get(r);
            ImportRow row = new ImportRow();
            boolean any = false;
            for (Map.Entry<Integer, String> e : colField.entrySet()) {
                String v = e.getKey() < line.size() ? line.get(e.getKey()) : null;
                if (v == null || v.trim().isEmpty()) continue;
                v = v.trim();
                any = true;
                switch (e.getValue()) {
                    case "partNo" -> row.setPartNo(v);
                    case "materialName" -> row.setMaterialName(v);
                    case "nameEn" -> row.setNameEn(v);
                    case "materialType" -> row.setMaterialType(v);
                    case "productType" -> row.setProductType(v);
                    case "specification" -> row.setSpecification(v);
                    case "unit" -> row.setUnit(v);
                    case "projectNo" -> row.setProjectNo(v);
                    default -> { }
                }
            }
            if (any) out.add(row);
        }
        return out;
    }

    // ---------- 解析器 ----------
    private List<List<String>> parseExcel(InputStream is) {
        List<Map<Integer, String>> raw = EasyExcel.read(is).headRowNumber(0).sheet().doReadSync();
        List<List<String>> grid = new ArrayList<>();
        for (Map<Integer, String> r : raw) {
            int max = r.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
            List<String> line = new ArrayList<>();
            for (int i = 0; i <= max; i++) line.add(r.getOrDefault(i, ""));
            grid.add(line);
        }
        return grid;
    }

    private List<List<String>> parseCsv(InputStream is) throws Exception {
        List<List<String>> grid = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                grid.add(new ArrayList<>(List.of(line.split(",", -1))));
            }
        }
        return grid;
    }

    private List<List<String>> parsePdf(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (PdfDocument pdf = new PdfDocument(new PdfReader(is))) {
            for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
                sb.append(PdfTextExtractor.getTextFromPage(pdf.getPage(i))).append('\n');
            }
        }
        List<List<String>> grid = new ArrayList<>();
        for (String raw : sb.toString().split("\\r?\\n")) {
            String line = raw.replace("\u00A0", " ").trim();
            if (line.isEmpty()) continue;
            String[] cols = line.split("\\s{2,}|\\t");
            List<String> row = new ArrayList<>();
            for (String c : cols) row.add(c.trim());
            grid.add(row);
        }
        if (grid.isEmpty()) throw new BusinessException("PDF 未解析到文本内容(可能为扫描件)");
        return grid;
    }

    private MaterialType parseType(String type, String productType) {
        if (type != null) {
            try {
                return MaterialType.valueOf(type.toUpperCase());
            } catch (Exception ignored) {
            }
        }
        if (productType != null && !productType.isBlank()) {
            return MaterialType.FINISHED;
        }
        return MaterialType.STANDARD;
    }

    @Data
    public static class ImportBody {
        private List<ImportRow> products;
    }

    @Data
    public static class ImportRow {
        private String partNo;
        private String materialName;
        private String nameZh;
        private String nameEn;
        private String materialType;
        private String productType;
        private String specification;
        private String unit;
        private String projectNo;
    }
}
