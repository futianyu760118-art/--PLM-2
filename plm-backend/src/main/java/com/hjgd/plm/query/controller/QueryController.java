package com.hjgd.plm.query.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.mapper.MaterialMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "V1 通用查询/搜索/关系图")
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class QueryController {

    private static final Map<String, ResourceDef> RESOURCES = new LinkedHashMap<>();
    static {
        RESOURCES.put("part", new ResourceDef("plm_material", "part_no",
                List.of("partNo","materialName","status","materialType","productType","phase","projectNo"), "物料/零件"));
        RESOURCES.put("bom", new ResourceDef("plm_bom", "bom_no",
                List.of("rootPartNo","bomType","status","versionNo"), "BOM 三态"));
        RESOURCES.put("ecn", new ResourceDef("plm_ecn", "ecn_no",
                List.of("ecnNo","partNo","changeType","status","applicant"), "ECN 变更"));
        RESOURCES.put("issue", new ResourceDef("plm_issue", "issue_no",
                List.of("issueNo","title","severity","status","category"), "问题"));
        RESOURCES.put("project", new ResourceDef("plm_project", "project_no",
                List.of("projectNo","projectName","partNo","customerName","currentGate","status"), "项目NPI"));
    }

    private static final Map<String, SearchDef> SEARCH_DEFS = Map.of(
            "part", new SearchDef("plm_material",
                    "SELECT id, part_no AS key, material_name AS title FROM plm_material WHERE deleted=0 AND (part_no ILIKE ? OR material_name ILIKE ?)",
                    List.of("id","key","title")),
            "bom", new SearchDef("plm_bom",
                    "SELECT id, bom_no AS key, root_part_no AS title FROM plm_bom WHERE deleted=0 AND (bom_no ILIKE ? OR root_part_no ILIKE ?)",
                    List.of("id","key","title")),
            "ecn", new SearchDef("plm_ecn",
                    "SELECT id, ecn_no AS key, change_reason AS title FROM plm_ecn WHERE deleted=0 AND (ecn_no ILIKE ? OR change_reason ILIKE ?)",
                    List.of("id","key","title")),
            "issue", new SearchDef("plm_issue",
                    "SELECT id, issue_no AS key, title FROM plm_issue WHERE title ILIKE ? OR issue_no ILIKE ?",
                    List.of("id","key","title"))
    );

    private final MaterialMapper materialMapper;
    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "可查询资源目录")
    @GetMapping("/query/resources")
    public Result<List<Map<String, Object>>> resources() {
        List<Map<String, Object>> out = new ArrayList<>();
        RESOURCES.forEach((k, v) -> out.add(Map.of(
                "resource", k,
                "table", v.table,
                "keyField", v.keyField,
                "filterFields", v.filterFields,
                "description", v.description)));
        return Result.success(out);
    }

    @Operation(summary = "通用列表查询")
    @PostMapping("/query")
    public Result<PageResult<Map<String, Object>>> query(@RequestBody QueryReq req) {
        if (req.getResource() == null) {
            return Result.failed("resource 必填");
        }
        ResourceDef def = RESOURCES.get(req.getResource().toLowerCase());
        if (def == null) {
            return Result.failed("不支持的 resource: " + req.getResource());
        }
        int page = req.getPage() == null ? 1 : req.getPage();
        int size = req.getPageSize() == null ? 20 : Math.min(req.getPageSize(), 200);
        return Result.success(execGenericList(def, req.getFilter(), page, size));
    }

    private PageResult<Map<String, Object>> execGenericList(ResourceDef def, Map<String, Object> filter, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM " + def.table + " WHERE deleted=0");
        List<Object> args = new ArrayList<>();
        if (filter != null) appendFilters(sql, def, filter, args);

        long total = 0;
        try {
            String countSql = sql.toString().replaceFirst("SELECT \\*", "SELECT COUNT(*)");
            total = ((Number) jdbcTemplate.queryForMap(countSql, args.toArray()).values().iterator().next()).longValue();
        } catch (Exception ignored) {}

        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return PageResult.of(total, page, size, rows);
    }

    @SuppressWarnings("unchecked")
    private void appendFilters(StringBuilder sql, ResourceDef def, Map<String, Object> filter, List<Object> args) {
        Object and = filter.get("and");
        if (and instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    String col = camelToUnder(String.valueOf(m.get("field")));
                    String op = String.valueOf(m.get("op"));
                    Object value = m.get("value");
                    sql.append(" AND ").append(col);
                    switch (op) {
                        case "eq" -> { sql.append("=?"); args.add(value); }
                        case "like" -> { sql.append(" ILIKE ?"); args.add("%" + value + "%"); }
                        case "in" -> {
                            if (value instanceof Collection<?> c && !c.isEmpty()) {
                                String placeholders = String.join(",", Collections.nCopies(c.size(), "?"));
                                sql.append(" IN (").append(placeholders).append(")");
                                args.addAll(c);
                            }
                        }
                        default -> { sql.append("=?"); args.add(value); }
                    }
                }
            }
        } else {
            for (Map.Entry<String, Object> e : filter.entrySet()) {
                if (!"and".equals(e.getKey()) && !"or".equals(e.getKey()) && e.getValue() != null) {
                    sql.append(" AND ").append(camelToUnder(e.getKey())).append("=?");
                    args.add(e.getValue());
                }
            }
        }
    }

    private String camelToUnder(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    @Operation(summary = "全文检索(ILIKE)")
    @GetMapping("/search")
    public Result<List<Map<String, Object>>> search(@RequestParam String q,
                                                    @RequestParam(required = false) String type,
                                                    @RequestParam(defaultValue = "20") int limit) {
        if (!StringUtils.hasText(q) || q.length() < 2) return Result.success(List.of());
        List<Map<String, Object>> results = new ArrayList<>();
        String like = "%" + q.trim() + "%";

        if (type == null || "part".equalsIgnoreCase(type) || "all".equalsIgnoreCase(type)) {
            addSearchResults(results, "part", like, limit);
        }
        if (type == null || "bom".equalsIgnoreCase(type) || "all".equalsIgnoreCase(type)) {
            addSearchResults(results, "bom", like, limit);
        }
        if (type == null || "ecn".equalsIgnoreCase(type) || "all".equalsIgnoreCase(type)) {
            addSearchResults(results, "ecn", like, limit);
        }
        if (type == null || "issue".equalsIgnoreCase(type) || "all".equalsIgnoreCase(type)) {
            addSearchResults(results, "issue", like, limit);
        }
        return Result.success(results);
    }

    private void addSearchResults(List<Map<String, Object>> results, String type, String like, int limit) {
        SearchDef def = SEARCH_DEFS.get(type);
        if (def == null) return;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    def.sql, like, like, limit);
            rows.forEach(r -> {
                Map<String, Object> m = new LinkedHashMap<>(r);
                m.put("type", type);
                results.add(m);
            });
        } catch (Exception e) {
            // 字段不存在时静默跳过
        }
    }

    @Operation(summary = "关系图展开(where-used/impact)")
    @PostMapping("/graph/expand")
    public Result<Map<String, Object>> graphExpand(@RequestBody GraphReq req) {
        if (req.getStart() == null) {
            return Result.failed("start.required");
        }
        String type = String.valueOf(req.getStart().getOrDefault("type", ""));
        String id = String.valueOf(req.getStart().getOrDefault("id", ""));
        int depth = req.getDepth() == null ? 2 : Math.min(req.getDepth(), 5);
        List<String> edges = req.getEdgeTypes() == null ? List.of("bom_parent", "bom_child", "ecn_impact") : req.getEdgeTypes();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("start", req.getStart());
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> links = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        if ("part".equalsIgnoreCase(type)) {
            expandPartGraph(id, depth, edges, nodes, links, visited);
        } else if ("ecn".equalsIgnoreCase(type)) {
            expandEcnGraph(id, depth, edges, nodes, links, visited);
        }
        result.put("nodes", nodes);
        result.put("links", links);
        return Result.success(result);
    }

    private void expandPartGraph(String partNo, int depth, List<String> edges, List<Map<String, Object>> nodes,
                                List<Map<String, Object>> links, Set<String> visited) {
        String nodeId = "part:" + partNo;
        if (!visited.add(nodeId)) return;
        nodes.add(Map.of("id", nodeId, "label", partNo, "type", "part"));

        if (depth <= 0) return;
        if (edges.contains("bom_child")) {
            try {
                List<Map<String, Object>> childBoms = jdbcTemplate.queryForList(
                        "SELECT b.bom_no, b.bom_type FROM plm_bom_item i " +
                                "JOIN plm_bom b ON i.bom_id=b.id WHERE i.part_no=? AND b.deleted=0 LIMIT 20",
                        partNo);
                for (Map<String, Object> bom : childBoms) {
                    String bomId = "bom:" + bom.get("bom_no");
                    links.add(Map.of("source", nodeId, "target", bomId, "relation", "used_in"));
                    if (!visited.contains(bomId)) {
                        nodes.add(Map.of("id", bomId, "label", bom.get("bom_no"), "type", "bom"));
                        visited.add(bomId);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (edges.contains("bom_parent")) {
            try {
                List<Map<String, Object>> parents = jdbcTemplate.queryForList(
                        "SELECT b.root_part_no, b.bom_no FROM plm_bom b " +
                                "WHERE b.id IN (SELECT bom_id FROM plm_bom_item WHERE part_no=? AND b.deleted=0) LIMIT 20",
                        partNo);
                for (Map<String, Object> p : parents) {
                    String pn = String.valueOf(p.get("root_part_no"));
                    expandPartGraph(pn, depth - 1, edges, nodes, links, visited);
                }
            } catch (Exception ignored) {}
        }
        if (edges.contains("ecn_impact")) {
            try {
                List<Map<String, Object>> ecns = jdbcTemplate.queryForList(
                        "SELECT ecn_no FROM plm_ecn WHERE part_no=? AND deleted=0 ORDER BY created_at DESC LIMIT 10",
                        partNo);
                for (Map<String, Object> e : ecns) {
                    String ecnId = "ecn:" + e.get("ecn_no");
                    links.add(Map.of("source", ecnId, "target", nodeId, "relation", "affects"));
                    if (visited.add(ecnId)) {
                        nodes.add(Map.of("id", ecnId, "label", e.get("ecn_no"), "type", "ecn"));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void expandEcnGraph(String ecnNo, int depth, List<String> edges,
                                List<Map<String, Object>> nodes, List<Map<String, Object>> links, Set<String> visited) {
        String nodeId = "ecn:" + ecnNo;
        if (!visited.add(nodeId)) return;
        nodes.add(Map.of("id", nodeId, "label", ecnNo, "type", "ecn"));
        try {
            List<Map<String, Object>> impacts = jdbcTemplate.queryForList(
                    "SELECT impact_type, target_type FROM plm_ecn_impact WHERE ecn_no=? LIMIT 20", ecnNo);
            for (Map<String, Object> imp : impacts) {
                String type = String.valueOf(imp.get("impact_type"));
                String target = type + ":" + ecnNo + ":" + imp.get("target_type");
                nodes.add(Map.of("id", target, "label", type + "→" + imp.get("target_type"), "type", type.toLowerCase()));
                links.add(Map.of("source", nodeId, "target", target, "relation", "impacts"));
            }
        } catch (Exception ignored) {}
    }

    @Operation(summary = "批量键取")
    @PostMapping("/lookup")
    public Result<List<Map<String, Object>>> lookup(@RequestBody LookupReq req) {
        if (req.getKeys() == null || req.getKeys().isEmpty()) return Result.success(List.of());
        String resource = req.getResource() == null ? "part" : req.getResource().toLowerCase();
        String keyField = req.getKeyField() == null ? "part_no" : camelToUnder(req.getKeyField());

        ResourceDef def = RESOURCES.get(resource);
        if (def == null) return Result.failed("不支持的 resource");
        try {
            String placeholders = String.join(",", Collections.nCopies(req.getKeys().size(), "?"));
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM " + def.table + " WHERE " + keyField + " IN (" + placeholders + ") AND deleted=0",
                    req.getKeys().toArray());
            return Result.success(rows);
        } catch (Exception e) {
            return Result.failed("查询失败: " + e.getMessage());
        }
    }

    private record ResourceDef(String table, String keyField, List<String> filterFields, String description) {}
    private record SearchDef(String table, String sql, List<String> fields) {}

    @Data public static class QueryReq {
        private String resource;
        private List<String> fields;
        private Map<String, Object> filter;
        private Integer page;
        private Integer pageSize;
    }

    @Data public static class LookupReq {
        private String resource;
        private List<String> keys;
        private String keyField;
        private List<String> fields;
    }

    @Data public static class GraphReq {
        private Map<String, Object> start;
        private List<String> edgeTypes;
        private Integer depth;
    }
}
