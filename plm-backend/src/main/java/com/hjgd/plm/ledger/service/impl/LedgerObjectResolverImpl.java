package com.hjgd.plm.ledger.service.impl;

import com.hjgd.plm.ledger.service.LedgerObjectResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 关联对象解析器：object_type 注册表 → 真实业务表（架构 §4.2）。
 *
 * <p>各表的「编号 / 名称 / 状态 / 软删」列名不统一，用 {@link ObjectDef} 描述；
 * SQL 里拼接的表名与列名全部来自本类的常量定义，只有 objectId/keyword 走绑定参数，
 * 不构成 SQL 注入面。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerObjectResolverImpl implements LedgerObjectResolver {

    private final JdbcTemplate jdbcTemplate;

    /** 视为「已作废」的状态取值；命中即把关联标为失效并注明 OBSOLETE（AC-A4-5） */
    private static final Set<String> OBSOLETE_STATUS =
            Set.of("OBSOLETE", "VOID", "CANCELLED", "CANCELED", "SCRAPPED", "INVALID");

    /**
     * @param table      业务表
     * @param keyColumn  业务键（落 plm_ledger_link.object_id）
     * @param noColumn   单据编号列
     * @param nameColumn 展示名列
     * @param statusColumn 状态列，无则 null
     * @param softDelete 是否带 deleted 软删列
     */
    private record ObjectDef(String table, String keyColumn, String noColumn,
                             String nameColumn, String statusColumn, boolean softDelete) {
    }

    private static final Map<String, ObjectDef> REGISTRY = new LinkedHashMap<>();

    static {
        REGISTRY.put("PROJECT", new ObjectDef("plm_project", "project_no", "project_no", "project_name", "status", false));
        REGISTRY.put("REQUIREMENT", new ObjectDef("plm_issue", "issue_no", "issue_no", "title", "status", false));
        REGISTRY.put("MATERIAL", new ObjectDef("plm_material", "part_no", "part_no", "material_name", "lifecycle_status", true));
        REGISTRY.put("BOM", new ObjectDef("plm_bom", "bom_no", "bom_no", "root_part_no", "status", false));
        REGISTRY.put("ECN", new ObjectDef("plm_ecn", "ecn_no", "ecn_no", "material_name", "status", false));
        REGISTRY.put("TASK", new ObjectDef("plm_work_item", "id", "item_no", "title", "status", false));
        REGISTRY.put("REVIEW", new ObjectDef("plm_project_node", "id", "node_code", "node_name", "status", false));
    }

    @Override
    public Set<String> supportedTypes() {
        return REGISTRY.keySet();
    }

    @Override
    public ObjectRef resolve(String objectType, String objectId) {
        ObjectDef def = def(objectType);
        if (def == null || !StringUtils.hasText(objectId)) {
            return new ObjectRef(objectId, null, null, false, "DELETED");
        }
        String key = objectId.trim();
        String sql = "SELECT " + def.noColumn() + " AS object_no, "
                + def.nameColumn() + " AS object_name"
                + (def.statusColumn() == null ? "" : ", " + def.statusColumn() + " AS object_status")
                + " FROM " + def.table()
                + " WHERE CAST(" + def.keyColumn() + " AS VARCHAR) = ?"
                + (def.softDelete() ? " AND deleted = 0" : "")
                + " LIMIT 1";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, key);
            if (rows.isEmpty()) {
                // 行不存在或已软删 → 已删除（AC-A4-5 不静默丢失、不报错中断页面）
                return new ObjectRef(key, null, null, false, "DELETED");
            }
            Map<String, Object> row = rows.get(0);
            String status = row.get("object_status") == null ? null : String.valueOf(row.get("object_status"));
            boolean obsolete = status != null
                    && OBSOLETE_STATUS.contains(status.toUpperCase(Locale.ROOT));
            return new ObjectRef(key,
                    asText(row.get("object_no")),
                    asText(row.get("object_name")),
                    !obsolete,
                    obsolete ? "OBSOLETE" : null);
        } catch (Exception e) {
            // 目标模块不可用不阻断台账主链路：保留原冗余值，标记失效并留日志（AC-A3-8）
            log.warn("关联对象解析失败 type={} id={}: {}", objectType, objectId, e.getMessage());
            return new ObjectRef(key, null, null, false, "DELETED");
        }
    }

    @Override
    public List<ObjectRef> search(String objectType, String keyword, int limit) {
        ObjectDef def = def(objectType);
        if (def == null) {
            return List.of();
        }
        int cap = Math.max(1, Math.min(limit, 50));
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        String sql = "SELECT CAST(" + def.keyColumn() + " AS VARCHAR) AS object_id, "
                + def.noColumn() + " AS object_no, " + def.nameColumn() + " AS object_name"
                + " FROM " + def.table()
                + " WHERE (" + def.noColumn() + " LIKE ? OR " + def.nameColumn() + " LIKE ?)"
                + (def.softDelete() ? " AND deleted = 0" : "")
                + " ORDER BY " + def.keyColumn() + " DESC LIMIT " + cap;
        List<ObjectRef> out = new ArrayList<>();
        try {
            for (Map<String, Object> row : jdbcTemplate.queryForList(sql, like, like)) {
                out.add(new ObjectRef(asText(row.get("object_id")),
                        asText(row.get("object_no")), asText(row.get("object_name")), true, null));
            }
        } catch (Exception e) {
            log.warn("关联对象检索失败 type={}: {}", objectType, e.getMessage());
        }
        return out;
    }

    private ObjectDef def(String objectType) {
        if (!StringUtils.hasText(objectType)) {
            return null;
        }
        return REGISTRY.get(objectType.trim().toUpperCase(Locale.ROOT));
    }

    private String asText(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
