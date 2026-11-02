package com.hjgd.plm.common;

import com.hjgd.plm.me.service.OrgTodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按人推送待办：遍历系统用户，取各自未完成/逾期节点，通过企业微信自建应用发给个人。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoPushService {

    private final JdbcTemplate jdbc;
    private final OrgTodoService orgTodoService;
    private final WeComAppService weComAppService;

    @Value("${plm.notify.portal-url:http://192.168.0.71/}")
    private String portalUrl;

    /**
     * 给所有用户推送待办。
     * @return 统计 {users, sent, skipped, failed}
     */
    public Map<String, Object> pushAll() {
        if (!weComAppService.enabled()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("error", "企业微信自建应用未配置或未启用");
            return m;
        }
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT id, real_name, phone, wecom_userid FROM sys_user WHERE deleted=0 AND status=1");
        int sent = 0, skipped = 0, failed = 0;
        for (Map<String, Object> u : users) {
            String realName = str(u.get("real_name"));
            if (realName.isEmpty()) { skipped++; continue; }
            String userid = str(u.get("wecom_userid"));
            if (userid.isEmpty()) {
                userid = weComAppService.useridByMobile(str(u.get("phone")));
                if (userid != null && !userid.isEmpty()) {
                    jdbc.update("UPDATE sys_user SET wecom_userid=? WHERE id=?", userid, u.get("id"));
                }
            }
            if (userid == null || userid.isEmpty()) { skipped++; continue; }

            String content = buildContent(realName);
            if (content == null) { skipped++; continue; } // 无待办不打扰
            if (weComAppService.sendText(userid, content)) sent++; else failed++;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("users", users.size());
        m.put("sent", sent);
        m.put("skipped", skipped);
        m.put("failed", failed);
        log.info("[待办推送] users={} sent={} skipped={} failed={}", users.size(), sent, skipped, failed);
        return m;
    }

    /** 给单个用户推送（realName 为系统真实姓名）。返回发送的待办条数，-1 表示失败/不可发送。 */
    public int pushOne(String realName, String userid) {
        if (!weComAppService.enabled()) return -1;
        if (userid == null || userid.isEmpty()) return -1;
        String content = buildContent(realName);
        if (content == null) return 0;
        return weComAppService.sendText(userid, content) ? 1 : -1;
    }

    /**
     * 即时推送：按真实姓名解析企业微信 userid 后发送一条自定义通知。
     * 未启用/找不到用户/未匹配到 userid 时静默返回 false。
     */
    public boolean pushUser(String realName, String content) {
        if (!weComAppService.enabled() || realName == null || realName.isBlank() || content == null) return false;
        String me = com.hjgd.plm.common.DataScope.name();
        if (realName.equals(me)) return false; // 不打扰操作者本人
        try {
            var rows = jdbc.queryForList(
                    "SELECT id, phone, wecom_userid FROM sys_user WHERE deleted=0 AND status=1 AND real_name=? LIMIT 1",
                    realName);
            if (rows.isEmpty()) return false;
            Map<String, Object> u = rows.get(0);
            String userid = str(u.get("wecom_userid"));
            if (userid.isEmpty()) {
                userid = weComAppService.useridByMobile(str(u.get("phone")));
                if (userid != null && !userid.isEmpty()) {
                    jdbc.update("UPDATE sys_user SET wecom_userid=? WHERE id=?", userid, u.get("id"));
                }
            }
            if (userid == null || userid.isEmpty()) return false;
            return weComAppService.sendText(userid, content);
        } catch (Exception e) {
            log.warn("[即时推送] 给{} 发送失败: {}", realName, e.getMessage());
            return false;
        }
    }

    /** 组装某人的待办文本；无待办返回 null。 */
    @SuppressWarnings("unchecked")
    public String buildContent(String realName) {
        Map<String, Object> todos = orgTodoService.todosFor(realName);
        List<Map<String, Object>> overdue = (List<Map<String, Object>>) todos.getOrDefault("overdue", List.of());
        List<Map<String, Object>> todo = (List<Map<String, Object>>) todos.getOrDefault("todo", List.of());
        if (overdue.isEmpty() && todo.isEmpty()) return null;

        List<String> lines = new ArrayList<>();
        lines.add("【PLM待办提醒】" + realName + "，你有 " + (overdue.size() + todo.size())
                + " 项待处理" + (overdue.isEmpty() ? "" : "（逾期 " + overdue.size() + "）") + "：");
        int budget = 8;
        for (Map<String, Object> r : overdue) {
            if (budget-- <= 0) break;
            lines.add("⚠逾期 " + line(r));
        }
        for (Map<String, Object> r : todo) {
            if (budget-- <= 0) break;
            lines.add("· " + line(r));
        }
        if (overdue.size() + todo.size() > 8) lines.add("…还有更多，请登录查看");
        lines.add("请登录 PLM 处理：" + portalUrl);
        return String.join("\n", lines);
    }

    private String line(Map<String, Object> r) {
        return str(r.get("project_no")) + " " + str(r.get("node_name"))
                + (str(r.get("plan_date")).isEmpty() ? "" : " (计划" + r.get("plan_date") + ")");
    }

    private String str(Object o) { return o == null ? "" : String.valueOf(o).trim(); }
}
