package com.hjgd.plm.rd.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.FuzzyMatcher;
import com.hjgd.plm.event.service.DomainEventService;
import com.hjgd.plm.rd.RdSheetDefs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 项目工作表通用服务 (规格书/配置表/样品单/评审单/测试报告/试产报告/出货)。
 * 表名与列名来自 RdSheetDefs 白名单，动态 SQL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RdSheetService {

    private final JdbcTemplate jdbc;
    private final DomainEventService domainEventService;

    /** AEOS: 自动引用项目明细表所选项目 (object_ref) */
    public Map<String, Object> context(String projectNo) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!StringUtils.hasText(projectNo)) { out.put("objectRef", null); return out; }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, project_no, project_name, customer_name, owner, part_no FROM plm_project WHERE project_no=? LIMIT 1",
                projectNo);
        if (rows.isEmpty()) { out.put("objectRef", null); return out; }
        Map<String, Object> p = rows.get(0);
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("object_type", "PROJECT");
        ref.put("object_id", p.get("id"));
        ref.put("external_key", p.get("project_no"));
        out.put("tenant_id", "hjgd");
        out.put("source_system", "PLM2");
        out.put("objectRef", ref);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("project_no", p.get("project_no"));
        fields.put("project_name", p.get("project_name"));
        fields.put("customer_name", p.get("customer_name"));
        fields.put("owner", p.get("owner"));
        fields.put("part_no", p.get("part_no"));
        out.put("fields", fields);
        return out;
    }

    private Long resolveProjectId(String projectNo) {
        if (!StringUtils.hasText(projectNo)) return null;
        try {
            List<Long> ids = jdbc.queryForList(
                    "SELECT id FROM plm_project WHERE project_no=? LIMIT 1", Long.class, projectNo);
            return ids.isEmpty() ? null : ids.get(0);
        } catch (Exception e) { return null; }
    }

    private String currentUser() {
        try { return SecurityUtils.getCurrentRealName(); } catch (Exception e) { return null; }
    }

    private void publishEvent(RdSheetDefs.Def d, String projectNo, Long projectId, String action, String correlationId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("object_type", "PROJECT");
            ref.put("object_id", projectId);
            ref.put("external_key", projectNo);
            payload.put("object_ref", ref);
            payload.put("worksheet_type", d.type());
            payload.put("correlation_id", correlationId);
            payload.put("action", action);
            domainEventService.publish("m04.worksheet." + action, d.type().toUpperCase(),
                    projectNo == null ? "" : projectNo, payload);
        } catch (Exception ignored) { }
    }

    public List<Map<String, Object>> meta() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RdSheetDefs.Def d : RdSheetDefs.DEFS.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", d.type());
            m.put("label", d.label());
            List<Map<String, Object>> cols = new ArrayList<>();
            for (RdSheetDefs.Col col : d.cols()) {
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("key", col.key());
                cm.put("label", col.label());
                cm.put("type", col.type());
                cm.put("options", col.options());
                cols.add(cm);
            }
            m.put("cols", cols);
            out.add(m);
        }
        return out;
    }

    private RdSheetDefs.Def def(String type) {
        RdSheetDefs.Def d = RdSheetDefs.get(type);
        if (d == null) throw new BusinessException("未知工作表类型: " + type);
        return d;
    }

    public Map<String, Object> list(String type, int pageNum, int pageSize, String keyword, String projectNo) {
        RdSheetDefs.Def d = def(type);
        List<String> keys = d.cols().stream().map(RdSheetDefs.Col::key).toList();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(projectNo)) {
            where.append(" AND project_no = ? ");
            args.add(projectNo);
        }
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (");
            boolean first = true;
            for (RdSheetDefs.Col col : d.cols()) {
                if (!"text".equals(col.type()) && !"textarea".equals(col.type())) continue;
                if (!first) where.append(" OR ");
                where.append(col.key()).append("::text ILIKE ?");
                args.add("%" + keyword + "%");
                first = false;
            }
            where.append(")");
        }
        String select = "SELECT id," + String.join(",", keys) + ",created_at,updated_at FROM " + d.table() + where;
        long total = Optional.ofNullable(jdbc.queryForObject(
                "SELECT count(*) FROM " + d.table() + where, Long.class, args.toArray())).orElse(0L);
        String order = " ORDER BY id DESC LIMIT " + pageSize + " OFFSET " + ((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(select + order, args.toArray());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records);
        out.put("total", total);
        out.put("pageNum", pageNum);
        out.put("pageSize", pageSize);
        return out;
    }

    public Map<String, Object> get(String type, Long id) {
        RdSheetDefs.Def d = def(type);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM " + d.table() + " WHERE id=?", id);
        if (rows.isEmpty()) throw new BusinessException("记录不存在");
        return rows.get(0);
    }

    public Map<String, Object> create(String type, Map<String, Object> body) {
        RdSheetDefs.Def d = def(type);
        List<String> cols = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (RdSheetDefs.Col col : d.cols()) {
            if (body.containsKey(col.key())) {
                cols.add(col.key());
                vals.add(norm(body.get(col.key())));
            }
        }
        cols.add("created_at"); vals.add(LocalDateTime.now());
        cols.add("updated_at"); vals.add(LocalDateTime.now());
        String ph = String.join(",", Collections.nCopies(cols.size(), "?"));
        Long id = jdbc.queryForObject("INSERT INTO " + d.table() + " (" + String.join(",", cols) +
                ") VALUES (" + ph + ") RETURNING id", Long.class, vals.toArray());
        body.put("id", id);
        applyEnvelope(d, body, id, "created");
        return body;
    }

    /** AEOS Common Envelope + 对象引用 */
    private void applyEnvelope(RdSheetDefs.Def d, Map<String, Object> body, Long id, String action) {
        String projectNo = body.get("project_no") == null ? null : String.valueOf(body.get("project_no"));
        Long projectId = resolveProjectId(projectNo);
        String corr = UUID.randomUUID().toString();
        try {
            jdbc.update("UPDATE " + d.table() + " SET tenant_id='hjgd', source_system='PLM2', " +
                            "revision=COALESCE(revision,0)+1, created_by=COALESCE(?,created_by), " +
                            "correlation_id=?, project_id=COALESCE(?,project_id) WHERE id=?",
                    currentUser(), corr, projectId, id);
        } catch (Exception ignore) { }
        publishEvent(d, projectNo, projectId, action, corr);
    }

    public Map<String, Object> update(String type, Map<String, Object> body) {
        RdSheetDefs.Def d = def(type);
        Object id = body.get("id");
        if (id == null) throw new BusinessException("id 必填");
        List<String> sets = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (RdSheetDefs.Col col : d.cols()) {
            if (body.containsKey(col.key())) {
                sets.add(col.key() + "=?");
                vals.add(norm(body.get(col.key())));
            }
        }
        sets.add("updated_at=?");
        vals.add(LocalDateTime.now());
        vals.add(id);
        jdbc.update("UPDATE " + d.table() + " SET " + String.join(",", sets) + " WHERE id=?", vals.toArray());
        applyEnvelope(d, body, ((Number) id).longValue(), "updated");
        return body;
    }

    public void delete(String type, Long id) {
        jdbc.update("DELETE FROM " + def(type).table() + " WHERE id=?", id);
    }

    public byte[] export(String type) {
        RdSheetDefs.Def d = def(type);
        List<String> keys = d.cols().stream().map(RdSheetDefs.Col::key).toList();
        List<List<String>> head = new ArrayList<>();
        for (RdSheetDefs.Col col : d.cols()) head.add(List.of(col.label()));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + String.join(",", keys) + " FROM " + d.table() + " ORDER BY id DESC");
        List<List<Object>> data = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            List<Object> row = new ArrayList<>();
            for (String k : keys) row.add(r.get(k) == null ? "" : String.valueOf(r.get(k)));
            data.add(row);
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        // 标准文件格式: Sheet1 数据 + Sheet2 填写说明
        ExcelWriter writer = EasyExcel.write(os).build();
        writer.write(data, EasyExcel.writerSheet(0, d.label()).head(head).build());
        writer.write(instructions(d), EasyExcel.writerSheet(1, "填写说明").head(
                List.of(List.of("字段"), List.of("类型"), List.of("取值/说明"))).build());
        writer.finish();
        return os.toByteArray();
    }

    private List<List<String>> instructions(RdSheetDefs.Def d) {
        List<List<String>> rows = new ArrayList<>();
        for (RdSheetDefs.Col c : d.cols()) {
            String note = switch (c.type() == null ? "text" : c.type()) {
                case "select" -> c.options() == null ? "" : "枚举: " + c.options();
                case "date" -> "日期(yyyy-MM-dd)";
                case "number" -> "数字";
                case "textarea" -> "长文本";
                default -> "文本";
            };
            rows.add(List.of(c.label(), c.type() == null ? "text" : c.type(), note));
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    public int importData(String type, MultipartFile file) {
        RdSheetDefs.Def d = def(type);
        int ok = 0;
        try {
            List<Map<Integer, String>> rows = EasyExcel.read(file.getInputStream())
                    .headRowNumber(0).sheet().doReadSync();
            if (rows.isEmpty()) return 0;
            Map<Integer, String> header = rows.get(0);
            Map<Integer, String> idxToKey = new HashMap<>();
            for (Map.Entry<Integer, String> e : header.entrySet()) {
                String lbl = e.getValue() == null ? "" : e.getValue();
                if (lbl.trim().isEmpty()) continue;
                String bestKey = null;
                int bestScore = 0;
                for (RdSheetDefs.Col col : d.cols()) {
                    int sc = Math.max(FuzzyMatcher.score(lbl, col.label()), FuzzyMatcher.score(lbl, col.key()));
                    if (sc > bestScore && !idxToKey.containsValue(col.key())) {
                        bestScore = sc; bestKey = col.key();
                    }
                }
                if (bestKey != null) idxToKey.put(e.getKey(), bestKey);
            }
            for (int i = 1; i < rows.size(); i++) {
                Map<Integer, String> r = rows.get(i);
                Map<String, Object> body = new HashMap<>();
                for (Map.Entry<Integer, String> e : idxToKey.entrySet()) {
                    String v = r.get(e.getKey());
                    if (StringUtils.hasText(v)) body.put(e.getValue(), v.trim());
                }
                if (body.isEmpty()) continue;
                create(type, body);
                ok++;
            }
        } catch (Exception ex) {
            throw new BusinessException("导入失败: " + ex.getMessage());
        }
        return ok;
    }

    private Object norm(Object v) {
        if (v == null) return null;
        if (v instanceof String s) return s.trim().isEmpty() ? null : s.trim();
        return v;
    }
}
