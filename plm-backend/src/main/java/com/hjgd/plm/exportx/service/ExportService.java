package com.hjgd.plm.exportx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.exportx.entity.ExportTask;
import com.hjgd.plm.exportx.mapper.ExportTaskMapper;
import com.hjgd.plm.system.service.SequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final ExportTaskMapper exportTaskMapper;
    private final SequenceService sequenceService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${plm.file.upload-dir:D:/PLM-2/files}/exports")
    private String exportDir;

    public ExportTask create(String exportType, String format, String name, Object spec, String requestedBy) {
        try {
            Files.createDirectories(Path.of(exportDir));
        } catch (IOException e) {
            log.warn("export dir create failed: {}", e.getMessage());
        }
        ExportTask task = new ExportTask();
        task.setExportNo(sequenceService.nextNo("EXPORT_NO"));
        task.setExportType(exportType);
        task.setFormat(format == null ? "csv" : format);
        task.setName(name);
        try {
            task.setSpecJson(objectMapper.writeValueAsString(spec));
        } catch (Exception e) {
            task.setSpecJson("{}");
        }
        task.setStatus("QUEUED");
        task.setRequestedBy(requestedBy);
        task.setCreatedAt(LocalDateTime.now());
        exportTaskMapper.insert(task);
        return task;
    }

    @SuppressWarnings("unchecked")
    public void execute(Long taskId) {
        ExportTask task = exportTaskMapper.selectById(taskId);
        if (task == null) return;
        task.setStatus("RUNNING");
        exportTaskMapper.updateById(task);

        try {
            Map<String, Object> spec = objectMapper.readValue(
                    task.getSpecJson() == null ? "{}" : task.getSpecJson(), Map.class);
            List<Map<String, Object>> rows;
            String[] headers;

            if ("QUERY".equals(task.getExportType()) || "RESOURCE".equals(task.getExportType())) {
                String resource = (String) spec.getOrDefault("resource", "part");
                rows = jdbcTemplate.queryForList(buildQuerySql(resource, spec));
                headers = extractHeaders(rows);
            } else if ("METRIC".equals(task.getExportType())) {
                rows = jdbcTemplate.queryForList(
                        "SELECT metric_code, grain_time, value_calc FROM plm_metric_value ORDER BY metric_code, grain_time DESC LIMIT 5000");
                headers = new String[]{"metric_code", "grain_time", "value_calc"};
            } else {
                rows = List.of();
                headers = new String[0];
            }

            String fileName = task.getExportNo() + ".csv";
            Path filePath = Path.of(exportDir, fileName);
            int count = writeCsv(filePath, rows, headers);

            task.setStatus("DONE");
            task.setFilePath(filePath.toString());
            task.setRowCount(count);
            task.setCompletedAt(LocalDateTime.now());
        } catch (Exception e) {
            log.error("export {} failed", task.getExportNo(), e);
            task.setStatus("FAILED");
            task.setErrorMsg(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
        }
        exportTaskMapper.updateById(task);
    }

    public ExportTask getById(Long id) {
        return exportTaskMapper.selectById(id);
    }

    public List<ExportTask> list(String status, int limit) {
        LambdaQueryWrapper<ExportTask> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            w.eq(ExportTask::getStatus, status);
        }
        w.orderByDesc(ExportTask::getCreatedAt).last("LIMIT " + Math.min(limit, 100));
        return exportTaskMapper.selectList(w);
    }

    static String buildQuerySql(String resource, Map<String, Object> spec) {
        if ("part".equalsIgnoreCase(resource) || "parts".equalsIgnoreCase(resource)) {
            return buildPartQuery(spec);
        }
        if ("ecn".equalsIgnoreCase(resource)) {
            return "SELECT id, ecn_no, part_no, change_type, version_before, version_after, status, " +
                    "applicant, apply_time, effective_time FROM plm_ecn WHERE deleted=0 ORDER BY created_at DESC LIMIT 10000";
        }
        if ("bom".equalsIgnoreCase(resource)) {
            return buildBomQuery(spec);
        }
        return "SELECT 1 LIMIT 0";
    }

    /**
     * BOM 多级爆炸导出(物料 BOM 全量清单 + 子件递归到叶子)。
     * spec.rootPartNo 必填;spec.includeSubBom=true 时递归到子BOM。
     * 列: 层级 / 料号 / 子件料号 / 子件名称 / 用量 / 单位 / 制造类型 / 版本 / 图纸号 / 备注。
     */
    static String buildBomQuery(Map<String, Object> spec) {
        StringBuilder sql = new StringBuilder();
        boolean sub = spec != null && Boolean.TRUE.equals(spec.get("includeSubBom"));
        if (sub) {
            // 递归 WITH: plm_bom_item 全树平铺
            sql.append("WITH RECURSIVE bom_tree AS ( ")
                    .append("SELECT bi.id, bi.part_no AS parent_part, bi.part_no AS child_part, ")
                    .append("bi.quantity, bi.unit, bi.make_type, 1 AS level_no, bi.remark ")
                    .append("FROM plm_bom_item bi WHERE bi.bom_id=(SELECT id FROM plm_bom WHERE root_part_no='").append(safeRoot((String) spec.get("rootPartNo"))).append("' AND status='RELEASED' AND deleted=0 ORDER BY created_at DESC LIMIT 1) ")
                    .append("AND bi.parent_item_id>0 ")
                    .append("UNION ALL ")
                    .append("SELECT ci.id, bt.child_part AS parent_part, ci.part_no AS child_part, ")
                    .append("ci.quantity, ci.unit, ci.make_type, bt.level_no+1, ci.remark ")
                    .append("FROM plm_bom_item ci JOIN bom_tree bt ON ci.bom_id=(SELECT id FROM plm_bom WHERE root_part_no=bt.child_part AND status='RELEASED' AND deleted=0 ORDER BY created_at DESC LIMIT 1) ")
                    .append("AND ci.parent_item_id>0) ")
                    .append("SELECT bt.level_no AS level_no, bt.parent_part AS parent_part, bt.child_part AS part_no, ")
                    .append("m.material_name, bt.quantity, bt.unit, ")
                    .append("CASE WHEN bt.make_type=0 THEN '自制' ELSE '外购' END AS make_type, ")
                    .append("m.version_no, m.drawing_no, m.drawing_revision, ")
                    .append("bt.remark ")
                    .append("FROM bom_tree bt LEFT JOIN plm_material m ON m.part_no=bt.child_part AND m.deleted=0 ")
                    .append("ORDER BY bt.level_no, bt.child_part LIMIT 50000");
        } else {
            // 单层:plm_bom_item 直接平铺(RELEASED)
            sql.append("SELECT bi.level_no, bi.part_no AS parent_part, ci.part_no AS part_no, ")
                    .append("m.material_name, ci.quantity, ci.unit, ")
                    .append("CASE WHEN ci.make_type=0 THEN '自制' ELSE '外购' END AS make_type, ")
                    .append("m.version_no, m.drawing_no, m.drawing_revision, ")
                    .append("ci.remark ")
                    .append("FROM plm_bom_item ci ")
                    .append("JOIN plm_bom_item bi ON bi.id=ci.parent_item_id ")
                    .append("JOIN plm_bom b ON b.id=ci.bom_id AND b.status='RELEASED' AND b.deleted=0 ")
                    .append("LEFT JOIN plm_material m ON m.part_no=ci.part_no AND m.deleted=0 ")
                    .append("WHERE ci.parent_item_id>0 ");
            if (spec != null && spec.get("rootPartNo") != null) {
                sql.append("AND b.root_part_no='").append(safeRoot((String) spec.get("rootPartNo"))).append("' ");
            }
            sql.append("ORDER BY ci.sort_order, ci.id LIMIT 50000");
        }
        return sql.toString();
    }

    private static String safeRoot(String root) {
        if (root == null) return "";
        // 限定料号字符,防注入
        return root.replaceAll("[^A-Za-z0-9_-]", "");
    }

    /** 物料清单导出 SQL，支持按 status / materialType / productType 过滤(已发布成品清单等场景) */
    static String buildPartQuery(Map<String, Object> spec) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, part_no, material_name, name_en, material_type, status, version_no, " +
                        "product_type, unit, project_no, phase, created_at, updated_at " +
                        "FROM plm_material WHERE deleted=0");
        if (spec != null) {
            appendFilter(sql, "status", spec.get("status"));
            appendFilter(sql, "material_type", spec.get("materialType"));
            appendFilter(sql, "product_type", spec.get("productType"));
        }
        sql.append(" ORDER BY updated_at DESC LIMIT 10000");
        return sql.toString();
    }

    /** 追加等值过滤；仅取首个合法 token(字母数字下划线)，防注入(导出值均为枚举码) */
    private static void appendFilter(StringBuilder sql, String column, Object value) {
        if (value == null) {
            return;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[A-Za-z0-9_]+").matcher(value.toString());
        String s = m.find() ? m.group() : "";
        if (s.isEmpty()) {
            return;
        }
        sql.append(" AND ").append(column).append("='").append(s).append("'");
    }

    private String[] extractHeaders(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return new String[0];
        return rows.get(0).keySet().toArray(new String[0]);
    }

    private int writeCsv(Path path, List<Map<String, Object>> rows, String[] headers) throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            bw.write('\uFEFF');
            if (headers.length > 0) {
                bw.write(String.join(",", headers));
                bw.newLine();
            }
            int count = 0;
            for (Map<String, Object> row : rows) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < headers.length; i++) {
                    if (i > 0) sb.append(",");
                    Object v = row.get(headers[i]);
                    if (v != null) {
                        String s = v.toString().replace("\"", "\"\"").replace("\n", " ");
                        if (s.contains(",") || s.contains("\"")) {
                            sb.append("\"").append(s).append("\"");
                        } else {
                            sb.append(s);
                        }
                    }
                }
                bw.write(sb.toString());
                bw.newLine();
                count++;
            }
            return count;
        }
    }

    public Path resolveFilePath(String stored) {
        return Path.of(stored);
    }
}
