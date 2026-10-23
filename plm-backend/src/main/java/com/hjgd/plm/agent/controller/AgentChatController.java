package com.hjgd.plm.agent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.agent.entity.AgentMessage;
import com.hjgd.plm.agent.entity.AgentSession;
import com.hjgd.plm.agent.mapper.AgentMessageMapper;
import com.hjgd.plm.agent.mapper.AgentSessionMapper;
import com.hjgd.plm.agent.service.LlmService;
import com.hjgd.plm.agent.service.AgentMemoryService;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.hub.service.HubInvocationService;
import com.hjgd.plm.system.service.SequenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * 总控智能体对话（multica Mika 式）：通俗语言进出，智能体自己路由执行；
 * 无法执行/歧义时返回「提问+选项」，由前端渲染按钮供用户点选。
 */
@Slf4j
@Tag(name = "V1 总控智能体对话")
@RestController
@RequestMapping("/v1/agents/chat")
@RequiredArgsConstructor
public class AgentChatController {

    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;
    private final LlmService llmService;
    private final AgentMemoryService memoryService;
    private final JdbcTemplate jdbc;
    private final SequenceService sequenceService;
    private final HubInvocationService hubInvocationService;

    @Data
    public static class ChatReq {
        private String message;
        private Long sessionId; // 可选，不带则新建总控会话
        private Map<String, Object> context; // 可选，当前页面上下文 {path,title}
    }

    @Operation(summary = "总控对话：执行动作或返回提问选项")
    @PostMapping
    public Result<Map<String, Object>> chat(@RequestBody ChatReq req) {
        String message = req.getMessage() == null ? "" : req.getMessage().trim();
        if (message.isEmpty()) return Result.failed(400, "请输入内容");

        // 1. 会话（沿用智能体会话表留痕）
        Long sessionId = req.getSessionId();
        if (sessionId == null) {
            AgentSession s = new AgentSession();
            s.setSessionNo(sequenceService.nextNo("AGENT_SESSION"));
            s.setAgentCode("AGENT_ORCH");
            s.setChannel("WIDGET");
            s.setStatus("OPEN");
            s.setStartedAt(OffsetDateTime.now());
            sessionMapper.insert(s);
            sessionId = s.getId();
        }
        AgentMessage um = new AgentMessage();
        um.setSessionId(sessionId); um.setRole("user"); um.setContent(message); um.setCreatedAt(OffsetDateTime.now());
        messageMapper.insert(um);

        // 2. 规则层直接执行（通俗关键词路由，LLM 不可用时也保证可用）
        Map<String, Object> rule = tryRuleExecute(message);
        Map<String, Object> result;
        if (rule != null) {
            result = rule;
        } else if (llmService.enabled()) {
            result = llmRoute(message, req.getContext(), loadHistory(sessionId));
        } else {
            result = cannotDo("我目前只能处理这些事情，请选择：",
                    List.of("查项目进度", "我的待办", "我的逾期节点", "查中台订单", "打开研发项目跟踪", "查看生命周期"));
        }

        // 3. 结果分析解读：让智能体按规则解读查询结果（指出异常/风险，不编造）
        String analysis = analyzeResult(message, result.get("data"));

        // 4. 组装回复并留痕
        String replyText = str(result.get("reply"));
        AgentMessage am = new AgentMessage();
        am.setSessionId(sessionId); am.setRole("assistant"); am.setContent(replyText); am.setCreatedAt(OffsetDateTime.now());
        messageMapper.insert(am);

        // 5. 自学习：把本次问答写入记忆，供后续多维度检索复用
        memoryService.remember(message, replyText, "chat");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", sessionId);
        out.put("reply", replyText);
        if (analysis != null) out.put("analysis", analysis);
        if (result.get("options") != null) out.put("options", result.get("options"));
        if (result.get("data") != null) out.put("data", result.get("data"));
        if (result.get("navigate") != null) out.put("navigate", result.get("navigate"));
        return Result.success(out);
    }

    // ================= 规则层：通俗语言直接执行 =================

    private Map<String, Object> tryRuleExecute(String m) {
        // hub.query：跨系统查中台（统一工具，经 Hub 能力调用，不直连中台）
        if (m.contains("中台") || m.contains("跨系统") || m.contains("hub.query")) {
            Map<String, Object> hub = tryHubQuery(m);
            if (hub != null) return hub;
        }
        // 导航指令：「打开/跳转/进入 XX」
        if (m.matches(".*(打开|跳转|进入|去).+")) {
            Map<String, String> nav = new LinkedHashMap<>();
            nav.put("项目", "/project/list"); nav.put("跟踪", "/project/tracking");
            nav.put("明细", "/project/progress"); nav.put("工作表", "/project/worksheets");
            nav.put("立项", "/project/initiation"); nav.put("物料", "/material/list");
            nav.put("BOM", "/bom/list"); nav.put("变更", "/ecn/list"); nav.put("ECN", "/ecn/list");
            nav.put("档案", "/archive/list"); nav.put("模型", "/model3d/list");
            nav.put("智能体", "/agent/list"); nav.put("帮助", "/help"); nav.put("生命周期", "/lifecycle");
            nav.put("待办", "/project/tasks"); nav.put("协同", "/project/tasks");
            nav.put("证据", "/project/evidence"); nav.put("复盘", "/project/review");
            for (Map.Entry<String, String> e : nav.entrySet()) {
                if (m.contains(e.getKey())) {
                    return ok("好的，已为你打开「" + e.getKey() + "」页面。", e.getValue());
                }
            }
        }
        // 我的待办
        if (m.matches(".*(我的)?待办|我的任务|todo.*")) {
            Long uid = uid();
            List<Map<String, Object>> items = jdbc.queryForList(
                    "SELECT title, status, sla_due_at FROM plm_work_item WHERE owner_id=? AND status<>'DONE' ORDER BY id DESC LIMIT 10", uid);
            if (items.isEmpty()) return reply("你现在没有待办任务，很清爽 👌", null);
            return reply("你有 " + items.size() + " 条未完成待办：", items);
        }
        // 我的逾期
        if (m.contains("逾期") || m.contains("过期") || m.contains("超期")) {
            String name = name();
            List<Map<String, Object>> list = jdbc.queryForList(
                    "SELECT project_no, node_name, plan_date FROM plm_project_node WHERE owner=? AND plan_date<CURRENT_DATE AND status<>'DONE' ORDER BY plan_date LIMIT 10", name);
            if (list.isEmpty()) return reply("你名下没有逾期节点，继续保持 🎉", null);
            return reply("你有 " + list.size() + " 个逾期节点，最紧的在前：", list);
        }
        // 生命周期
        if (m.contains("生命周期") || m.contains("大盘") || m.contains("总览")) {
            List<Map<String, Object>> data = new ArrayList<>();
            data.add(Map.of("立项", cnt("plm_project_initiation")));
            data.add(Map.of("项目", cnt("plm_project")));
            data.add(Map.of("物料", cnt("plm_material")));
            data.add(Map.of("证据", cnt("plm_project_node_evidence")));
            data.add(Map.of("变更留痕", cnt("plm_project_change_log")));
            return reply("全生命周期关键数据：", data);
        }
        // 含项目编号 → 项目详情（前缀匹配）
        String projectNo = findProject(m);
        if (projectNo != null) {
            Map<String, Object> p = jdbc.queryForMap(
                    "SELECT project_no, project_name, owner, status, current_gate FROM plm_project WHERE project_no=?", projectNo);
            Map<String, Object> n = jdbc.queryForMap(
                    "SELECT COUNT(*) total, COUNT(*) FILTER (WHERE status='DONE') done FROM plm_project_node WHERE project_no=?", projectNo);
            List<Map<String, Object>> data = new ArrayList<>();
            Map<String, Object> row = new LinkedHashMap<>(p);
            row.put("节点进度", n.get("done") + "/" + n.get("total"));
            data.add(row);
            return reply("项目 " + projectNo + " 的当前状态如下（点「明细」可看全部节点）：", data);
        }
        return null; // 规则未命中
    }

    // ================= hub.query 统一工具（跨系统查中台，经 Hub 能力调用） =================

    /**
     * 把「查中台XX前N条」路由到 Hub 能力调用。当前提供中台订单的提示映射；
     * 能力目录由 Hub 维护，此处只保留常用关键词→(capability,path) 的提示，不做每系统一套工具。
     */
    private Map<String, Object> tryHubQuery(String m) {
        if (!hubInvocationService.enabled()) {
            return reply("hub.query 未配置（后端缺少 AEOS_HUB_SYSTEM_TOKEN），暂不能查中台。", null);
        }
        String capability = "query_control_platform";
        String path = null;
        if (m.contains("订单")) {
            path = "/external/orders/list";
        }
        if (path == null) {
            return reply("我目前能通过 Hub 查中台的能力：订单。你可以说「查中台订单前 5 条」。", null);
        }
        int pageSize = extractPageSize(m, 5);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", 1);
        params.put("page_size", pageSize);
        try {
            Map<String, Object> result = hubInvocationService.query(capability, path, params);
            List<Map<String, Object>> items = extractItems(result);
            Object total = result.get("total");
            String msg = total != null
                    ? "已通过 Hub 查到中台数据（共 " + total + " 条）："
                    : "已通过 Hub 查到中台数据：";
            if (items.isEmpty() && result != null && !result.isEmpty()) {
                return reply(msg, List.of(result));
            }
            return reply(msg, items);
        } catch (BusinessException e) {
            log.warn("[hub.query] 查中台失败: {}", e.getMessage());
            return reply("查询中台失败：" + e.getMessage(), null);
        } catch (Exception e) {
            log.warn("[hub.query] 查中台异常", e);
            return reply("查询中台异常：" + e.getMessage(), null);
        }
    }

    private int extractPageSize(String m, int dft) {
        java.util.regex.Matcher mt = java.util.regex.Pattern.compile("前\\s*(\\d+)\\s*条").matcher(m);
        if (mt.find()) {
            try {
                int n = Integer.parseInt(mt.group(1));
                return n > 0 && n <= 100 ? n : dft;
            } catch (NumberFormatException ignored) { }
        }
        return dft;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItems(Map<String, Object> result) {
        if (result == null) return new ArrayList<>();
        Object items = result.get("items");
        if (items instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
            }
            return out;
        }
        Object records = result.get("records");
        if (records instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
            }
            return out;
        }
        return new ArrayList<>();
    }

    // ================= 结果分析解读（有规则，只读，不编造） =================

    /**
     * 让智能体按规则解读查询结果：先概括条数与主要对象，再指出异常/风险/逾期/阻塞/金额或数量异常。
     * 规则：只基于给出数据、不编造、结论仅供参考、涉及金额/承诺/质量/法律须提示人工确认。
     * LLM 未配置或数据为空时返回 null（前端不展示分析块，不影响主结果）。
     */
    private String analyzeResult(String query, Object dataObj) {
        if (!llmService.enabled()) return null;
        if (!(dataObj instanceof List<?> list) || list.isEmpty()) return null;
        String dataJson = compactJson(list);
        String sys = "你是恒剑PLM智能体的结果解读助手。请基于下面给出的查询结果，用中文简洁解读（2~4句，控制在80字内）。"
                + "先概括结果（条数、主要对象、状态分布），再指出值得注意的点（如风险、异常、逾期、阻塞、金额或数量异常）。"
                + "规则：严格只基于给出的数据，不得编造、不得臆断数据里没有的信息；"
                + "结论仅供参考，涉及金额、对外承诺、质量判定、法律事项时必须提示用户人工确认。";
        String out = llmService.chat(sys, "用户问题：" + query + "\n查询结果：" + dataJson);
        return (out == null || out.isBlank()) ? null : out.trim();
    }

    /** 把查询结果压缩成受限 JSON（最多 20 行 × 15 字段，长文本截断），避免把完整业务数据交给模型。 */
    private String compactJson(List<?> list) {
        try {
            int maxRows = Math.min(list.size(), 20);
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < maxRows; i++) {
                Object o = list.get(i);
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    int c = 0;
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        if (c++ >= 15) break;
                        Object v = e.getValue();
                        if (v instanceof String s && s.length() > 80) v = s.substring(0, 80) + "…";
                        row.put(String.valueOf(e.getKey()), v);
                    }
                    out.add(row);
                } else {
                    out.add(o);
                }
            }
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(out);
        } catch (Exception e) {
            return "[]";
        }
    }

    // ================= LLM 层：通俗路由（输出动作JSON或提问选项） =================

    private Map<String, Object> llmRoute(String message, Map<String, Object> context, String history) {
        String who = name();
        String page = context == null ? "" : String.valueOf(context.getOrDefault("path", ""));
        String mem = buildMemoryContext(message);
        String sys = "你是恒剑PLM的总控智能体（类似multica的Mika）。用户用通俗语言提需求，你自己判断怎么做，不要让用户自己去某个功能找。"
                + "当前用户：" + (who.isEmpty() ? "未知" : who) + "；当前页面：" + (page.isEmpty() ? "未知" : page) + "。"
                + "系统里有这些内容：研发项目(plm_project)、项目节点(plm_project_node)、我的待办、逾期节点、物料、证据、变更、生命周期等。\n"
                + "只能执行以下动作：\n"
                + "查项目(需项目编号/名称关键字)、查我的待办、查我的逾期节点、生命周期总览、查中台订单(经Hub跨系统)、打开页面(项目/跟踪/明细/工作表/立项/物料/BOM/变更/档案/模型/智能体/帮助/待办/证据)\n"
                + "若能执行：只输出JSON {\"action\":\"execute\",\"command\":\"<上面任一动作的自然语言指令>\"}\n"
                + "若不能执行但可以基于系统常识直接回答：只输出JSON {\"action\":\"answer\",\"text\":\"<简洁回答>\"}\n"
                + "若不能且需要澄清：只输出JSON {\"action\":\"ask\",\"question\":\"<一句通俗提问>\",\"options\":[\"选项1\",\"选项2\",\"选项3\"]}（2-4个选项）\n"
                + "禁止输出JSON以外的任何内容。";
        if (history != null && !history.isEmpty()) {
            sys = "【本轮对话上下文】\n" + history + "\n\n" + sys;
        }
        if (!mem.isEmpty()) {
            sys = mem + "\n\n" + sys;
        }
        String out = llmService.chat(sys, message);
        String json = extractJson(out);
        if (json == null) {
            return cannotDo("这个问题我暂时办不了，你可以：",
                    List.of("查项目进度", "我的待办", "我的逾期节点", "查中台订单", "查看生命周期"));
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            String action = node.path("action").asText("");
            if ("execute".equals(action)) {
                Map<String, Object> r = tryRuleExecute(node.path("command").asText(""));
                if (r != null) return r;
                return cannotDo("我理解了但执行失败，换个说法或选择：",
                        List.of("查项目进度", "我的待办", "我的逾期节点"));
            }
            if ("answer".equals(action)) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("reply", node.path("text").asText(""));
                return r;
            }
            // ask
            List<String> options = new ArrayList<>();
            node.path("options").forEach(o -> options.add(o.asText()));
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("reply", node.path("question").asText("你想做什么？"));
            if (!options.isEmpty()) r.put("options", options);
            return r;
        } catch (Exception e) {
            return cannotDo("我没听懂，可以换种说法，或选择：",
                    List.of("查项目进度", "我的待办", "我的逾期节点", "查中台订单", "查看生命周期"));
        }
    }

    private String extractJson(String s) {
        if (s == null) return null;
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        return (a >= 0 && b > a) ? s.substring(a, b + 1) : null;
    }

    // ================= 自学习：多维度上下文（历史对话 + 知识库 + 系统概况） =================

    private String loadHistory(Long sessionId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT role, content FROM plm_agent_message WHERE session_id=? ORDER BY id DESC LIMIT 6", sessionId);
            Collections.reverse(rows);
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> r : rows) {
                String role = String.valueOf(r.get("role"));
                String c = String.valueOf(r.get("content"));
                if (c.length() > 200) c = c.substring(0, 200);
                sb.append(role).append(": ").append(c).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String buildMemoryContext(String message) {
        StringBuilder sb = new StringBuilder();
        try {
            List<Map<String, Object>> mem = memoryService.recall(message, 3);
            if (!mem.isEmpty()) {
                sb.append("【历史对话记忆】以下是你以前回答过的相似问题，可参考：\n");
                for (Map<String, Object> m : mem) {
                    sb.append("- 问：").append(m.get("question")).append("\n  答：").append(m.get("answer")).append("\n");
                }
            }
            List<Map<String, Object>> kb = memoryService.recallKnowledge(message, 2);
            if (!kb.isEmpty()) {
                sb.append("【知识库】相关条目：\n");
                for (Map<String, Object> k : kb) {
                    sb.append("- ").append(k.get("title")).append("：").append(k.get("content")).append("\n");
                }
            }
            Map<String, Object> snap = memoryService.systemSnapshot();
            sb.append("【系统概况】项目").append(snap.get("projects")).append("个，物料").append(snap.get("materials"))
              .append("个，已完成节点").append(snap.get("nodesDone")).append("个，待处理反馈").append(snap.get("feedbackPending"))
              .append("条，知识库").append(snap.get("knowledge")).append("条，记忆").append(snap.get("memory")).append("条。\n");
        } catch (Exception e) {
            log.warn("[自学习] 上下文构建失败: {}", e.getMessage());
        }
        return sb.toString();
    }

    // ================= 工具 =================

    private Map<String, Object> ok(String reply, String navigate) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("reply", reply);
        r.put("navigate", navigate);
        return r;
    }
    private Map<String, Object> reply(String text, List<Map<String, Object>> data) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("reply", text);
        if (data != null) r.put("data", data);
        return r;
    }
    private Map<String, Object> cannotDo(String q, List<String> options) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("reply", q);
        r.put("options", options);
        return r;
    }
    private String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private Long uid() { try { return SecurityUtils.getCurrentUserId(); } catch (Exception e) { return 0L; } }
    private String name() { try { return SecurityUtils.getCurrentRealName(); } catch (Exception e) { return ""; } }

    private long cnt(String table) {
        try { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
        catch (Exception e) { return 0; }
    }

    private String findProject(String text) {
        for (String t : text.split("[^A-Za-z0-9._-]+")) {
            if (t.length() >= 6 && t.toUpperCase().startsWith("HJ.")) {
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT project_no FROM plm_project WHERE project_no LIKE ? LIMIT 1", t + "%");
                if (!rows.isEmpty()) return (String) rows.get(0).get("project_no");
            }
        }
        return null;
    }
}
