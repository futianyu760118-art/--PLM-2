package com.hjgd.plm.rd.service;

import com.alibaba.excel.EasyExcel;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.rd.RdSheetDefs;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 通用表服务 (白名单列, 动态 SQL)。用于项目工作表与系统管理基础数据。
 */
@Service
@RequiredArgsConstructor
public class GenericTableService {

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> meta(Collection<RdSheetDefs.Def> defs) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RdSheetDefs.Def d : defs) {
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

    public Map<String, Object> list(RdSheetDefs.Def d, int pageNum, int pageSize, String keyword,
                                    String projectNo, String projectField) {
        List<String> keys = d.cols().stream().map(RdSheetDefs.Col::key).toList();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(projectField) && StringUtils.hasText(projectNo)) {
            where.append(" AND ").append(projectField).append(" = ? ");
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
        long total = Optional.ofNullable(jdbc.queryForObject(
                "SELECT count(*) FROM " + d.table() + where, Long.class, args.toArray())).orElse(0L);
        String select = "SELECT id," + String.join(",", keys) + ",created_at,updated_at FROM " + d.table() + where
                + " ORDER BY id DESC LIMIT " + pageSize + " OFFSET " + ((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(select, args.toArray());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records);
        out.put("total", total);
        out.put("pageNum", pageNum);
        out.put("pageSize", pageSize);
        return out;
    }

    public Map<String, Object> get(RdSheetDefs.Def d, Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM " + d.table() + " WHERE id=?", id);
        if (rows.isEmpty()) throw new BusinessException("记录不存在");
        return rows.get(0);
    }

    public Map<String, Object> create(RdSheetDefs.Def d, Map<String, Object> body) {
        List<String> cols = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (RdSheetDefs.Col col : d.cols()) {
            if (body.containsKey(col.key())) { cols.add(col.key()); vals.add(norm(body.get(col.key()))); }
        }
        cols.add("created_at"); vals.add(LocalDateTime.now());
        cols.add("updated_at"); vals.add(LocalDateTime.now());
        String ph = String.join(",", Collections.nCopies(cols.size(), "?"));
        jdbc.update("INSERT INTO " + d.table() + " (" + String.join(",", cols) + ") VALUES (" + ph + ")", vals.toArray());
        return body;
    }

    public Map<String, Object> update(RdSheetDefs.Def d, Map<String, Object> body) {
        Object id = body.get("id");
        if (id == null) throw new BusinessException("id 必填");
        List<String> sets = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (RdSheetDefs.Col col : d.cols()) {
            if (body.containsKey(col.key())) { sets.add(col.key() + "=?"); vals.add(norm(body.get(col.key()))); }
        }
        sets.add("updated_at=?"); vals.add(LocalDateTime.now());
        vals.add(id);
        jdbc.update("UPDATE " + d.table() + " SET " + String.join(",", sets) + " WHERE id=?", vals.toArray());
        return body;
    }

    public void delete(RdSheetDefs.Def d, Long id) {
        jdbc.update("DELETE FROM " + d.table() + " WHERE id=?", id);
    }

    public byte[] export(RdSheetDefs.Def d) {
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
        EasyExcel.write(os).head(head).sheet(d.label()).doWrite(data);
        return os.toByteArray();
    }

    @SuppressWarnings("unchecked")
    public int importData(RdSheetDefs.Def d, MultipartFile file) {
        int ok = 0;
        try {
            List<Map<Integer, String>> rows = EasyExcel.read(file.getInputStream())
                    .headRowNumber(0).sheet().doReadSync();
            if (rows.isEmpty()) return 0;
            Map<Integer, String> header = rows.get(0);
            Map<Integer, String> idxToKey = new HashMap<>();
            for (Map.Entry<Integer, String> e : header.entrySet()) {
                String lbl = e.getValue() == null ? "" : e.getValue().trim();
                for (RdSheetDefs.Col col : d.cols()) {
                    if (col.label().equals(lbl) || col.key().equalsIgnoreCase(lbl)) {
                        idxToKey.put(e.getKey(), col.key()); break;
                    }
                }
            }
            for (int i = 1; i < rows.size(); i++) {
                Map<Integer, String> r = rows.get(i);
                Map<String, Object> body = new HashMap<>();
                for (Map.Entry<Integer, String> e : idxToKey.entrySet()) {
                    String v = r.get(e.getKey());
                    if (StringUtils.hasText(v)) body.put(e.getValue(), v.trim());
                }
                if (body.isEmpty()) continue;
                create(d, body);
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
