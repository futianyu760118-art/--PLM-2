package com.hjgd.plm.dataio.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.dataio.model.ColumnDef;
import com.hjgd.plm.dataio.model.ImportResult;
import com.hjgd.plm.dataio.model.ModuleDef;
import com.hjgd.plm.dataio.model.SelfCheckResult;
import com.hjgd.plm.dataio.registry.ModuleRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 通用数据导入 / 导出 / 自检 引擎(注册表驱动)。
 * 导出/模板: EasyExcel(.xlsx, 中文表头 + 填写说明);
 * 导入: 按模板表头匹配字段, 逐行校验(必填/类型/枚举/唯一/引用/日期先后), 支持 dryRun 预检;
 * 自检: 字段完整率 + 必填/唯一/枚举/类型/引用/日期 规则扫描, 输出健康分与问题清单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataIoService {

    private final JdbcTemplate jdbc;
    private final ModuleRegistry registry;

    private static final int MAX_ISSUES = 500;
    private static final int EXPORT_LIMIT = 50000;
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}[-/\\.]\\d{1,2}[-/\\.]\\d{1,2}$");

    public ModuleDef def(String code) {
        ModuleDef d = registry.get(code);
        if (d == null) throw new BusinessException("未知模块: " + code);
        return d;
    }

    public List<Map<String, Object>> listModules() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ModuleDef d : registry.all().values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", d.getCode());
            m.put("name", d.getName());
            m.put("table", d.getTable());
            m.put("columnCount", d.getColumns().size());
            m.put("keyField", d.getKeyField());
            out.add(m);
        }
        return out;
    }

    // ==================== 导出 / 模板 ====================

    public byte[] export(String code) {
        ModuleDef d = def(code);
        String cols = String.join(",", d.getColumns().stream().map(ColumnDef::getField).toList());
        String sql = "SELECT " + cols + " FROM " + d.getTable() + whereClause(d) + " ORDER BY id DESC LIMIT " + EXPORT_LIMIT;
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        return writeWorkbook(d, rows, false);
    }

    public byte[] template(String code) {
        ModuleDef d = def(code);
        return writeWorkbook(d, List.of(), true);
    }

    private byte[] writeWorkbook(ModuleDef d, List<Map<String, Object>> rows, boolean template) {
        List<List<String>> head = d.getColumns().stream().map(c -> List.of(c.getLabel())).toList();
        List<List<Object>> data = new ArrayList<>();
        if (template) {
            List<Object> sample = new ArrayList<>();
            for (ColumnDef c : d.getColumns()) {
                if ("ENUM".equals(c.getType()) && c.getEnums() != null && !c.getEnums().isEmpty()) {
                    sample.add(String.join(" / ", c.getEnums()));
                } else if (c.isRequired()) {
                    sample.add("(必填)");
                } else {
                    sample.add("");
                }
            }
            data.add(sample);
        } else {
            for (Map<String, Object> r : rows) {
                List<Object> line = new ArrayList<>();
                for (ColumnDef c : d.getColumns()) line.add(fmt(r.get(c.getField())));
                data.add(line);
            }
        }

        List<List<String>> noteHead = List.of(List.of("字段"), List.of("中文名"), List.of("类型"),
                List.of("必填"), List.of("唯一"), List.of("枚举候选"), List.of("关联引用"));
        List<List<Object>> noteData = new ArrayList<>();
        for (ColumnDef c : d.getColumns()) {
            noteData.add(List.of(c.getField(), c.getLabel(), typeLabel(c),
                    c.isRequired() ? "是" : "", c.isUnique() ? "是" : "",
                    c.getEnums() == null ? "" : String.join(", ", c.getEnums()),
                    c.getRefTable() == null ? "" : c.getRefTable() + "." + c.getRefColumn() + "(" + safe(c.getRefLabel()) + ")"));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ExcelWriter writer = EasyExcel.write(out).build()) {
            WriteSheet dataSheet = EasyExcel.writerSheet(0, "数据").head(head).build();
            writer.write(data, dataSheet);
            WriteSheet noteSheet = EasyExcel.writerSheet(1, "填写说明").head(noteHead).build();
            writer.write(noteData, noteSheet);
        }
        return out.toByteArray();
    }

    // ==================== 导入 ====================

    public ImportResult importExcel(String code, MultipartFile file, boolean dryRun) {
        ModuleDef d = def(code);
        List<Map<Integer, String>> raw;
        try {
            raw = EasyExcel.read(file.getInputStream()).sheet(0).headRowNumber(0).doReadSync();
        } catch (Exception e) {
            throw new BusinessException("文件解析失败, 请使用系统模板(.xlsx): " + e.getMessage());
        }
        if (raw == null || raw.isEmpty()) throw new BusinessException("文件无数据");

        ImportResult res = new ImportResult();
        res.setModule(code);
        res.setName(d.getName());
        res.setDryRun(dryRun);

        // 表头 -> 字段映射(按中文名或字段名匹配)
        Map<Integer, ColumnDef> indexMap = new LinkedHashMap<>();
        Map<Integer, String> headerRow = raw.get(0);
        List<String> headers = new ArrayList<>();
        for (Map.Entry<Integer, String> e : new TreeMap<>(headerRow).entrySet()) {
            String h = e.getValue() == null ? "" : e.getValue().trim();
            headers.add(h);
            ColumnDef c = findColumn(d, h);
            if (c != null) {
                indexMap.put(e.getKey(), c);
                res.getMapping().put(h, c.getField());
            }
        }
        res.setHeaders(headers);
        if (indexMap.isEmpty()) {
            throw new BusinessException("未识别到有效列, 请先下载并使用系统模板(表头需与模板一致)");
        }

        // 唯一键现有值(用于查重)
        Set<String> existingKeys = new HashSet<>();
        ColumnDef keyCol = d.getColumns().stream().filter(ColumnDef::isUnique).findFirst().orElse(null);
        if (keyCol != null) {
            jdbc.queryForList("SELECT " + keyCol.getField() + " FROM " + d.getTable() + whereClause(d))
                    .forEach(m -> existingKeys.add(String.valueOf(m.values().iterator().next()).trim()));
        }
        Set<String> seenKeys = new HashSet<>();
        Map<String, Boolean> refCache = new HashMap<>();

        List<ColumnDef> writeCols = new ArrayList<>();
        for (ColumnDef c : indexMap.values()) if (!writeCols.contains(c)) writeCols.add(c);
        List<Object[]> batchArgs = new ArrayList<>();

        int rowNo = 0;
        for (int i = 1; i < raw.size(); i++) {
            Map<Integer, String> row = raw.get(i);
            if (row == null || row.values().stream().allMatch(v -> v == null || v.isBlank())) continue;
            rowNo++;
            int excelRow = i + 1;
            Map<ColumnDef, String> vals = new LinkedHashMap<>();
            boolean ok = true;

            for (Map.Entry<Integer, ColumnDef> e : indexMap.entrySet()) {
                ColumnDef c = e.getValue();
                if (vals.containsKey(c)) continue;
                String v = row.get(e.getKey());
                v = v == null ? "" : v.trim();
                String err = validateCell(d, c, v, existingKeys, seenKeys, refCache);
                if (err != null) {
                    res.getErrors().add(new ImportResult.ErrorItem(excelRow, c.getField(), c.getLabel(), err));
                    ok = false;
                }
                vals.put(c, normalize(c, v));
            }
            // 日期先后
            if (ok) {
                String de = crossDateError(d, vals);
                if (de != null) {
                    res.getErrors().add(new ImportResult.ErrorItem(excelRow, d.getEndField(), "日期顺序", de));
                    ok = false;
                }
            }
            if (!ok) { res.setFailed(res.getFailed() + 1); continue; }

            res.setSuccess(res.getSuccess() + 1);
            if (!dryRun) {
                Object[] args = writeCols.stream().map(c -> blankToNull(vals.get(c))).toArray();
                batchArgs.add(args);
            }
        }
        res.setTotal(rowNo);

        if (!dryRun && !batchArgs.isEmpty()) {
            String colSql = String.join(",", writeCols.stream().map(ColumnDef::getField).toList());
            String ph = String.join(",", Collections.nCopies(writeCols.size(), "?"));
            String sql = "INSERT INTO " + d.getTable() + " (" + colSql + ") VALUES (" + ph + ")";
            int[] r = jdbc.batchUpdate(sql, batchArgs);
            res.setInserted(r.length);
        }
        if (res.getErrors().size() > MAX_ISSUES) {
            res.getErrors().subList(MAX_ISSUES, res.getErrors().size()).clear();
        }
        return res;
    }

    private String validateCell(ModuleDef d, ColumnDef c, String v,
                                Set<String> existingKeys, Set<String> seenKeys, Map<String, Boolean> refCache) {
        if (c.isRequired() && v.isBlank()) return "必填项不能为空";
        if (v.isBlank()) return null;
        switch (c.getType()) {
            case "NUMBER" -> {
                try { new BigDecimal(v); } catch (Exception e) { return "必须为数字: " + v; }
            }
            case "DATE" -> {
                if (!DATE_PATTERN.matcher(v).matches()) return "日期格式应为 YYYY-MM-DD: " + v;
            }
            case "ENUM" -> {
                if (c.getEnums() != null && c.getEnums().stream().noneMatch(x -> x.equalsIgnoreCase(v))) {
                    return "枚举值非法(可选: " + String.join("/", c.getEnums()) + "): " + v;
                }
            }
            default -> { }
        }
        if (c.isUnique()) {
            if (existingKeys.contains(v)) return "唯一键已存在: " + v;
            if (!seenKeys.add(v)) return "文件内重复: " + v;
        }
        if (c.getRefTable() != null) {
            String cacheKey = c.getRefTable() + "." + c.getRefColumn() + "." + v;
            Boolean exists = refCache.computeIfAbsent(cacheKey, k -> {
                List<Map<String, Object>> rs = jdbc.queryForList(
                        "SELECT 1 FROM " + c.getRefTable() + " WHERE " + c.getRefColumn() + "=? LIMIT 1", v);
                return !rs.isEmpty();
            });
            if (Boolean.FALSE.equals(exists)) return "关联" + safe(c.getRefLabel()) + "不存在: " + v;
        }
        return null;
    }

    private String crossDateError(ModuleDef d, Map<ColumnDef, String> vals) {
        if (d.getStartField() == null || d.getEndField() == null) return null;
        String s = valueOf(vals, d.getStartField());
        String e = valueOf(vals, d.getEndField());
        if (s == null || e == null || s.isBlank() || e.isBlank()) return null;
        return e.compareTo(s) < 0 ? "目标日期早于开始日期(" + s + " > " + e + ")" : null;
    }

    private String valueOf(Map<ColumnDef, String> vals, String field) {
        for (Map.Entry<ColumnDef, String> e : vals.entrySet()) {
            if (e.getKey().getField().equals(field)) return e.getValue();
        }
        return null;
    }

    private String normalize(ColumnDef c, String v) {
        if (v == null) return null;
        String s = v.trim();
        if (s.isEmpty()) return s;
        if ("ENUM".equals(c.getType()) && c.getEnums() != null) {
            for (String e : c.getEnums()) if (e.equalsIgnoreCase(s)) return e;
        }
        if ("DATE".equals(c.getType())) {
            return s.replace('/', '-').replace('.', '-');
        }
        return s;
    }

    private Object blankToNull(String v) { return (v == null || v.isBlank()) ? null : v; }

    // ==================== 自检 ====================

    public SelfCheckResult selfCheck(String code) {
        ModuleDef d = def(code);
        String cols = "id," + String.join(",", d.getColumns().stream().map(ColumnDef::getField).toList());
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + cols + " FROM " + d.getTable() + whereClause(d) + " ORDER BY id");
        SelfCheckResult res = new SelfCheckResult();
        res.setModule(code);
        res.setName(d.getName());
        res.setTable(d.getTable());
        res.setTotalRows(rows.size());
        int[] sev = new int[3]; // HIGH, MEDIUM, LOW
        List<SelfCheckResult.Issue> issues = new ArrayList<>();

        ColumnDef keyCol = d.getColumns().stream().filter(ColumnDef::isUnique).findFirst().orElse(null);

        // 字段完整率
        List<SelfCheckResult.FieldStat> fieldStats = new ArrayList<>();
        for (ColumnDef c : d.getColumns()) {
            int filled = 0;
            for (Map<String, Object> r : rows) if (notBlank(r.get(c.getField()))) filled++;
            double rate = rows.isEmpty() ? 1.0 : (double) filled / rows.size();
            fieldStats.add(new SelfCheckResult.FieldStat(c.getField(), c.getLabel(), c.isRequired(), filled, rows.size(), round(rate)));
        }
        res.setFields(fieldStats);
        double completeness = fieldStats.isEmpty() ? 1.0
                : fieldStats.stream().mapToDouble(SelfCheckResult.FieldStat::rate).average().orElse(1.0);
        res.setCompleteness(round(completeness));

        // 唯一键重复
        Map<String, Integer> keyCount = new HashMap<>();
        if (keyCol != null) {
            for (Map<String, Object> r : rows) {
                Object kv = r.get(keyCol.getField());
                if (kv != null) keyCount.merge(String.valueOf(kv), 1, Integer::sum);
            }
        }

        Map<String, Boolean> refCache = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Long id = ((Number) r.get("id")).longValue();
            String keyValue = keyCol == null ? String.valueOf(id) : String.valueOf(r.get(keyCol.getField()));
            for (ColumnDef c : d.getColumns()) {
                Object raw = r.get(c.getField());
                String v = raw == null ? "" : String.valueOf(raw).trim();
                if (c.isRequired() && v.isBlank()) {
                    addIssue(issues, sev, id, keyValue, c, "HIGH", "必填字段为空");
                    continue;
                }
                if (v.isBlank()) continue;
                if ("NUMBER".equals(c.getType())) {
                    try { new BigDecimal(v); } catch (Exception e) {
                        addIssue(issues, sev, id, keyValue, c, "MEDIUM", "非数字值: " + v);
                    }
                } else if ("DATE".equals(c.getType()) && !DATE_PATTERN.matcher(v).matches()) {
                    addIssue(issues, sev, id, keyValue, c, "MEDIUM", "日期格式异常: " + v);
                } else if ("ENUM".equals(c.getType()) && c.getEnums() != null
                        && c.getEnums().stream().noneMatch(x -> x.equalsIgnoreCase(v))) {
                    addIssue(issues, sev, id, keyValue, c, "MEDIUM", "枚举值非法: " + v);
                }
                if (c.isUnique() && keyCol != null && c.getField().equals(keyCol.getField())
                        && keyCount.getOrDefault(v, 0) > 1) {
                    addIssue(issues, sev, id, keyValue, c, "HIGH", "唯一键重复(" + keyCount.get(v) + " 条)");
                }
                if (c.getRefTable() != null) {
                    String cacheKey = c.getRefTable() + "." + c.getRefColumn() + "." + v;
                    Boolean exists = refCache.computeIfAbsent(cacheKey, k ->
                            !jdbc.queryForList("SELECT 1 FROM " + c.getRefTable() + " WHERE " + c.getRefColumn() + "=? LIMIT 1", v).isEmpty());
                    if (Boolean.FALSE.equals(exists)) {
                        addIssue(issues, sev, id, keyValue, c, "HIGH", "关联" + safe(c.getRefLabel()) + "不存在: " + v);
                    }
                }
            }
            // 日期先后
            if (d.getStartField() != null && d.getEndField() != null) {
                String s = str(r.get(d.getStartField()));
                String e = str(r.get(d.getEndField()));
                if (!s.isBlank() && !e.isBlank() && DATE_PATTERN.matcher(s).matches() && DATE_PATTERN.matcher(e).matches()
                        && e.replace('/', '-').replace('.', '-').compareTo(s.replace('/', '-').replace('.', '-')) < 0) {
                    ColumnDef endCol = d.getColumns().stream().filter(c -> c.getField().equals(d.getEndField())).findFirst().orElse(null);
                    addIssue(issues, sev, id, keyValue, endCol, "MEDIUM", "目标日期早于开始日期(" + s + " > " + e + ")");
                }
            }
        }

        res.setBySeverity(Map.of("HIGH", sev[0], "MEDIUM", sev[1], "LOW", sev[2]));
        res.setIssues(issues);
        int score = (int) Math.round(completeness * 100) - sev[0] * 3 - sev[1] - sev[2];
        res.setScore(Math.max(0, Math.min(100, score)));
        return res;
    }

    private void addIssue(List<SelfCheckResult.Issue> issues, int[] sev, Long id, String keyValue,
                          ColumnDef c, String severity, String message) {
        if (severity.equals("HIGH")) sev[0]++;
        else if (severity.equals("MEDIUM")) sev[1]++;
        else sev[2]++;
        if (issues.size() < MAX_ISSUES) {
            issues.add(new SelfCheckResult.Issue(id, keyValue,
                    c == null ? "" : c.getField(), c == null ? "" : c.getLabel(), severity, message));
        }
    }

    // ==================== 工具 ====================

    private ColumnDef findColumn(ModuleDef d, String header) {
        if (header == null || header.isBlank()) return null;
        String h = header.trim();
        for (ColumnDef c : d.getColumns()) {
            if (h.equalsIgnoreCase(c.getField()) || h.equals(c.getLabel())) return c;
        }
        return null;
    }

    private String whereClause(ModuleDef d) { return d.isLogicDelete() ? " WHERE deleted=0" : ""; }

    private boolean notBlank(Object o) { return o != null && !String.valueOf(o).isBlank(); }

    private String str(Object o) { return o == null ? "" : String.valueOf(o); }

    private String fmt(Object o) {
        if (o == null) return "";
        if (o instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toString().replace('T', ' ');
        if (o instanceof LocalDate ld) return ld.toString();
        return String.valueOf(o);
    }

    private String typeLabel(ColumnDef c) {
        return switch (c.getType()) {
            case "NUMBER" -> "数字";
            case "DATE" -> "日期(YYYY-MM-DD)";
            case "ENUM" -> "枚举";
            case "BOOL" -> "布尔";
            default -> "文本";
        };
    }

    private double round(double v) { return Math.round(v * 1000.0) / 1000.0; }

    private String safe(String s) { return s == null ? "" : s; }
}
