package com.hjgd.plm.me.service;

import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.auth.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 我的待办 · 组织架构聚合。
 *
 * <p>口径：上级(经理类角色)能看到本部门下级待办，总经理/管理员看全部；个人待办向上汇总成部门待办。
 * 上下级关系按「角色层级」判定，部门归属按「角色→部门」映射（与 database/50_org_dept_assign.sql 一致）。
 */
@Service
@RequiredArgsConstructor
public class OrgTodoService {

    private final JdbcTemplate jdbc;

    /** 角色 -> 部门编码 */
    private static final Map<String, String> ROLE_DEPT = Map.ofEntries(
            Map.entry("engineer", "RD"), Map.entry("ENGINEER", "RD"),
            Map.entry("rd_manager", "RD"), Map.entry("project_manager", "RD"),
            Map.entry("sales_manager", "SALES"), Map.entry("sales", "SALES"),
            Map.entry("SALES", "SALES"), Map.entry("sales_director", "SALES"), Map.entry("sales_user", "SALES"),
            Map.entry("finance", "FIN"), Map.entry("cw1", "FIN"),
            Map.entry("purchase", "SCM"),
            Map.entry("hr_manager", "ADM"),
            Map.entry("QUALITY", "QA"));

    /** 经理类角色 -> 可查看的部门编码集合 */
    private static final Map<String, List<String>> LEADER_DEPTS = Map.of(
            "rd_manager", List.of("RD"),
            "project_manager", List.of("RD"),
            "sales_director", List.of("SALES"),
            "sales_manager", List.of("SALES"),
            "cw1", List.of("FIN"),
            "hr_manager", List.of("ADM"));

    /** 全局可见角色 */
    private static final Set<String> ALL_ROLES = Set.of("ADMIN", "admin", "gm");

    public Map<String, Object> deptTodos() {
        LoginUser u = SecurityUtils.getCurrentUser();
        String me = u.getRealName();
        List<String> roles = u.getRoles() == null ? List.of() : u.getRoles();

        boolean all = roles.stream().anyMatch(ALL_ROLES::contains);

        Map<Long, String> deptCodeById = loadDeptCodeById();
        Map<Long, String> userName = loadUserName();
        Map<Long, String> userDept = loadUserDept(deptCodeById);

        Set<String> scopeDeptCodes = new LinkedHashSet<>();
        if (!all) {
            for (String r : roles) {
                List<String> ds = LEADER_DEPTS.get(r);
                if (ds != null) scopeDeptCodes.addAll(ds);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scope", all ? "ALL" : (scopeDeptCodes.isEmpty() ? "SELF" : "DEPT"));
        out.put("scopeDepts", all ? null : new ArrayList<>(scopeDeptCodes));

        Map<String, Object> self = todosFor(me);
        self.put("realName", me);
        self.put("userId", u.getUserId());
        out.put("self", self);

        List<Map<String, Object>> depts = new ArrayList<>();
        if (all || !scopeDeptCodes.isEmpty()) {
            List<Map<String, Object>> deptRows;
            if (all) {
                deptRows = jdbc.queryForList("SELECT id, dept_code, dept_name FROM sys_department ORDER BY id");
            } else {
                deptRows = jdbc.queryForList(
                        "SELECT id, dept_code, dept_name FROM sys_department WHERE dept_code IN (" + in(scopeDeptCodes.size()) + ") ORDER BY id",
                        scopeDeptCodes.toArray());
            }
            Set<String> seenNames = new LinkedHashSet<>();
            for (Map<String, Object> dr : deptRows) {
                Long deptId = ((Number) dr.get("id")).longValue();
                String deptCode = (String) dr.get("dept_code");
                String deptName = (String) dr.get("dept_name");

                List<Map<String, Object>> members = new ArrayList<>();
                int ov = 0, td = 0, dn = 0;
                for (Map.Entry<Long, String> e : userDept.entrySet()) {
                    Long uid = e.getKey();
                    String dc = e.getValue();
                    if (!deptCode.equals(dc)) continue;
                    String name = userName.get(uid);
                    if (name == null) continue;
                    if (!seenNames.add(name)) continue; // 同名用户去重，避免节点按姓名归属被重复计入

                    Map<String, Object> m = todosFor(name);
                    m.put("userId", uid);
                    m.put("realName", name);
                    m.put("deptName", deptName);
                    ov += ((Number) m.get("overdueCount")).intValue();
                    td += ((Number) m.get("todoCount")).intValue();
                    dn += ((Number) m.get("doneCount")).intValue();
                    members.add(m);
                }

                Map<String, Object> d = new LinkedHashMap<>();
                d.put("deptId", deptId);
                d.put("deptCode", deptCode);
                d.put("deptName", deptName);
                d.put("overdueCount", ov);
                d.put("todoCount", td);
                d.put("doneCount", dn);
                d.put("members", members);
                depts.add(d);
            }
        }
        out.put("departments", depts);
        return out;
    }

    /** 某用户的待办（口径与 /v1/me/todos 一致）：负责项目下的节点，或节点直接指定给该用户。 */
    public Map<String, Object> todosFor(String realName) {
        String cols = "n.project_no, p.project_name, p.status AS project_status, " +
                "n.node_code, n.node_name, n.status, n.plan_date, n.actual_date";
        String from = " FROM plm_project_node n LEFT JOIN plm_project p ON p.project_no = n.project_no " +
                "WHERE (n.owner=? OR n.project_no IN (SELECT project_no FROM plm_project WHERE owner=?))";
        List<Map<String, Object>> overdue = jdbc.queryForList(
                "SELECT " + cols + from + " AND n.status <> 'DONE' AND n.plan_date IS NOT NULL AND n.plan_date < CURRENT_DATE " +
                        "ORDER BY n.plan_date", realName, realName);
        List<Map<String, Object>> todo = jdbc.queryForList(
                "SELECT " + cols + from + " AND n.status <> 'DONE' AND (n.plan_date IS NULL OR n.plan_date >= CURRENT_DATE) " +
                        "ORDER BY n.plan_date NULLS LAST", realName, realName);
        List<Map<String, Object>> done = jdbc.queryForList(
                "SELECT " + cols + from + " AND n.status = 'DONE' ORDER BY n.actual_date DESC NULLS LAST LIMIT 200", realName, realName);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("overdue", overdue);
        out.put("todo", todo);
        out.put("done", done);
        out.put("overdueCount", overdue.size());
        out.put("todoCount", todo.size());
        out.put("doneCount", done.size());
        return out;
    }

    private Map<Long, String> loadDeptCodeById() {
        Map<Long, String> m = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT id, dept_code FROM sys_department")) {
            m.put(((Number) r.get("id")).longValue(), (String) r.get("dept_code"));
        }
        return m;
    }

    private Map<Long, String> loadUserName() {
        Map<Long, String> m = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT id, real_name FROM sys_user WHERE status=1")) {
            m.put(((Number) r.get("id")).longValue(), (String) r.get("real_name"));
        }
        return m;
    }

    /** userId -> deptCode：优先 dept_id，其次按角色映射兜底。 */
    private Map<Long, String> loadUserDept(Map<Long, String> deptCodeById) {
        Map<Long, String> userDept = new LinkedHashMap<>();
        List<Map<String, Object>> users = jdbc.queryForList("SELECT id, dept_id FROM sys_user WHERE status=1");
        Map<Long, List<String>> rolesByUser = new LinkedHashMap<>();
        List<Map<String, Object>> roleRows = jdbc.queryForList(
                "SELECT ur.user_id, r.role_code FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id ORDER BY ur.user_id, ur.role_id");
        for (Map<String, Object> r : roleRows) {
            Long uid = ((Number) r.get("user_id")).longValue();
            rolesByUser.computeIfAbsent(uid, k -> new ArrayList<>()).add((String) r.get("role_code"));
        }
        for (Map<String, Object> r : users) {
            Long uid = ((Number) r.get("id")).longValue();
            Long deptId = r.get("dept_id") == null ? null : ((Number) r.get("dept_id")).longValue();
            if (deptId != null && deptCodeById.containsKey(deptId)) {
                userDept.put(uid, deptCodeById.get(deptId));
            } else {
                List<String> rs = rolesByUser.get(uid);
                if (rs != null) {
                    for (String role : rs) {
                        String dc = ROLE_DEPT.get(role);
                        if (dc != null) {
                            userDept.put(uid, dc);
                            break;
                        }
                    }
                }
            }
        }
        return userDept;
    }

    private String in(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(",");
            sb.append("?");
        }
        return sb.toString();
    }
}
