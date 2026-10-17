package com.hjgd.plm.rd.service;

import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.metric.domain.HealthScoreDef;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目数据变更列表 (留痕)。
 */
@Service
@RequiredArgsConstructor
public class ChangeLogService {

    private final JdbcTemplate jdbc;

    public void record(Long projectId, String projectNo, String objectType, Object objectId,
                       String nodeCode, String action, String field, String oldV, String newV,
                       String source) {
        try {
            String operator = null;
            try { operator = SecurityUtils.getCurrentRealName(); } catch (Exception ignore) {}
            jdbc.update("INSERT INTO plm_project_change_log(project_id,project_no,object_type,object_id," +
                            "node_code,action,field_name,old_value,new_value,source,operator) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    projectId, projectNo, objectType, objectId == null ? null : String.valueOf(objectId),
                    nodeCode, action, field, oldV, newV, source == null ? "UI" : source, operator);
        } catch (Exception e) {
            // 变更留痕失败不影响业务
        }
    }

    /**
     * 正式变更次数（健康分变更因子 CF 的唯一取数口径，见 HealthScoreDef）。
     *
     * <p>「正式变更」= {@code action in ('CREATE','UPDATE','DELETE')}；
     * {@code ROLLUP}（工作表汇总派生）与 {@code CELL}（跟踪总表内联编辑）为系统派生记录，
     * 不是人发起的正式变更，不计入 CF —— 这正是 ISS-002 口径错误的修复点：
     * 原实现用「节点编辑次数」近似，把系统派生回写也算成了变更。
     *
     * <p>未知动作码同样不计入（由 {@link HealthScoreDef.ChangeCategory#fromAction} 判定），
     * 保证口径只有一处来源。
     */
    public long countFormalChanges(String projectNo) {
        if (!StringUtils.hasText(projectNo)) return 0L;
        List<String> codes = new java.util.ArrayList<>();
        for (HealthScoreDef.ChangeCategory c : HealthScoreDef.ChangeCategory.values()) {
            if (c.isFormal()) codes.add(c.actionCode());
        }
        if (codes.isEmpty()) return 0L;
        String placeholders = String.join(",", codes.stream().map(x -> "?").toList());
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(projectNo);
        args.addAll(codes);
        Long n = jdbc.queryForObject(
                "SELECT count(*) FROM plm_project_change_log WHERE project_no=? AND action IN ("
                        + placeholders + ")", Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Map<String, Object> list(String projectNo, String objectType, int pageNum, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (StringUtils.hasText(projectNo)) { where.append(" AND project_no=?"); args.add(projectNo); }
        if (StringUtils.hasText(objectType)) { where.append(" AND object_type=?"); args.add(objectType); }
        // 数据可见性：1=全部, 2=仅他人, 3=仅本人（operator 或本人项目）
        int ds = com.hjgd.plm.common.DataScope.scope();
        if (ds != com.hjgd.plm.common.DataScope.SCOPE_ALL) {
            String me = com.hjgd.plm.common.DataScope.name();
            if (ds == com.hjgd.plm.common.DataScope.SCOPE_SELF) {
                where.append(" AND (operator=? OR project_no IN (SELECT project_no FROM plm_project WHERE owner=?))");
            } else {
                where.append(" AND (operator IS DISTINCT FROM ? AND project_no NOT IN (SELECT project_no FROM plm_project WHERE owner=?))");
            }
            args.add(me);
            args.add(me);
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM plm_project_change_log" + where, Long.class, args.toArray());
        List<Map<String, Object>> records = jdbc.queryForList(
                "SELECT id, project_no AS projectNo, object_type AS objectType, object_id AS objectId, " +
                        "node_code AS nodeCode, action, field_name AS fieldName, old_value AS oldValue, " +
                        "new_value AS newValue, source, operator, created_at AS createdAt " +
                        "FROM plm_project_change_log" + where + " ORDER BY id DESC LIMIT " + pageSize +
                        " OFFSET " + ((pageNum - 1) * pageSize), args.toArray());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records);
        out.put("total", total == null ? 0 : total);
        out.put("pageNum", pageNum);
        out.put("pageSize", pageSize);
        return out;
    }
}
