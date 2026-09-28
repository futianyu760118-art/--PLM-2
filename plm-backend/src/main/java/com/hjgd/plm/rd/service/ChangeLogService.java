package com.hjgd.plm.rd.service;

import com.hjgd.plm.auth.security.SecurityUtils;
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

    public Map<String, Object> list(String projectNo, String objectType, int pageNum, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (StringUtils.hasText(projectNo)) { where.append(" AND project_no=?"); args.add(projectNo); }
        if (StringUtils.hasText(objectType)) { where.append(" AND object_type=?"); args.add(objectType); }
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
