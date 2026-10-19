package com.hjgd.plm.me.controller;

import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.DataScope;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.me.service.OrgTodoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 「我的」即时数据聚合：按当前登录用户（用户ID + 真实姓名）跨模块统计。
 * 姓名字段(owner/uploaded_by)以 realName 匹配；身份字段(owner_id/submitter_id)以 userId 匹配。
 */
@Tag(name = "V1 我的即时数据")
@RestController
@RequestMapping("/v1/me")
@RequiredArgsConstructor
public class MeOverviewController {

    private final JdbcTemplate jdbc;
    private final OrgTodoService orgTodoService;

    private long c(String sql, Object... args) {
        try {
            Long v = jdbc.queryForObject(sql, Long.class, args);
            return v == null ? 0 : v;
        } catch (Exception e) {
            return 0;
        }
    }

    @Operation(summary = "当前用户各模块即时数据")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Long userId;
        String realName;
        try {
            userId = SecurityUtils.getCurrentUserId();
            realName = SecurityUtils.getCurrentRealName();
        } catch (Exception e) {
            return Result.failed(401, "未登录");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", userId);
        out.put("realName", realName);

        // 我的项目（owner 为姓名）
        Map<String, Object> projects = new LinkedHashMap<>();
        projects.put("total", c("SELECT COUNT(*) FROM plm_project WHERE owner=?", realName));
        projects.put("active", c("SELECT COUNT(*) FROM plm_project WHERE owner=? AND status='ACTIVE'", realName));
        projects.put("closed", c("SELECT COUNT(*) FROM plm_project WHERE owner=? AND status IN ('CLOSED','CANCELLED')", realName));
        try {
            projects.put("recent", jdbc.queryForList(
                    "SELECT id, project_no, project_name, status, current_gate FROM plm_project WHERE owner=? ORDER BY updated_at DESC LIMIT 5", realName));
        } catch (Exception e) {
            projects.put("recent", Collections.emptyList());
        }
        out.put("projects", projects);

        // 我的待办（owner_id 为用户ID，服务端身份）
        Map<String, Object> todos = new LinkedHashMap<>();
        todos.put("open", c("SELECT COUNT(*) FROM plm_work_item WHERE owner_id=? AND status='OPEN'", userId));
        todos.put("escalated", c("SELECT COUNT(*) FROM plm_work_item WHERE owner_id=? AND status='ESCALATED'", userId));
        todos.put("done", c("SELECT COUNT(*) FROM plm_work_item WHERE owner_id=? AND status='DONE'", userId));
        out.put("todos", todos);

        // 我的节点：负责且逾期未完成
        Map<String, Object> nodes = new LinkedHashMap<>();
        nodes.put("owned", c("SELECT COUNT(*) FROM plm_project_node WHERE owner=?", realName));
        nodes.put("overdue", c("SELECT COUNT(*) FROM plm_project_node WHERE owner=? AND plan_date < CURRENT_DATE AND status <> 'DONE'", realName));
        nodes.put("done", c("SELECT COUNT(*) FROM plm_project_node WHERE owner=? AND status='DONE'", realName));
        try {
            nodes.put("overdueList", jdbc.queryForList(
                    "SELECT n.project_no, n.node_code, n.node_name, n.plan_date, n.status FROM plm_project_node n " +
                            "WHERE n.owner=? AND n.plan_date < CURRENT_DATE AND n.status <> 'DONE' ORDER BY n.plan_date LIMIT 5", realName));
        } catch (Exception e) {
            nodes.put("overdueList", Collections.emptyList());
        }
        out.put("nodes", nodes);

        // 我的证据 / 我的工作表(计划表 owner) / 我的反馈
        out.put("evidence", c("SELECT COUNT(*) FROM plm_project_node_evidence WHERE uploaded_by=?", realName));
        out.put("planSheets", c("SELECT COUNT(*) FROM plm_plan_sheet WHERE owner=?", realName));
        out.put("feedback", c("SELECT COUNT(*) FROM plm_feedback WHERE submitter_id=? AND COALESCE(deleted,0)=0", userId));

        // 今日动态
        out.put("todayChanges", c("SELECT COUNT(*) FROM plm_project_change_log WHERE operator=? AND created_at::date=CURRENT_DATE", realName));

        return Result.success(out);
    }

    @Operation(summary = "当前用户数据范围(1=全部,2=仅他人,3=仅本人)")
    @GetMapping("/scope")
    public Result<Map<String, Object>> scope() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dataScope", DataScope.scope());
        m.put("admin", DataScope.admin());
        return Result.success(m);
    }

    @Operation(summary = "我的待办(来自我负责项目的未完成节点): 逾期 / 待办 / 已完成")
    @GetMapping("/todos")
    public Result<Map<String, Object>> todos() {
        return Result.success(orgTodoService.todosFor(DataScope.name()));
    }

    @Operation(summary = "部门待办(组织架构聚合): 上级看下级, 个人汇总成部门")
    @GetMapping("/dept-todos")
    public Result<Map<String, Object>> deptTodos() {
        return Result.success(orgTodoService.deptTodos());
    }

    @Operation(summary = "切换当前用户数据范围(工程师可看到/操作全部或仅本人)")
    @PutMapping("/scope")
    public Result<Map<String, Object>> setScope(@RequestBody Map<String, Object> body) {
        Integer scope = body.get("dataScope") == null ? null : Integer.valueOf(String.valueOf(body.get("dataScope")));
        if (scope == null || (scope != 1 && scope != 2 && scope != 3)) {
            return Result.failed(400, "dataScope 仅支持 1(全部) / 2(仅他人) / 3(仅本人)");
        }
        Long uid = SecurityUtils.getCurrentUserId();
        int n = jdbc.update("UPDATE sys_user SET data_scope=? WHERE id=?", scope, uid);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dataScope", scope);
        m.put("updated", n);
        return Result.success(m);
    }
}
