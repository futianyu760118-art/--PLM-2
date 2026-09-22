package com.hjgd.plm.agent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.agent.entity.AgentAction;
import com.hjgd.plm.agent.entity.AgentMessage;
import com.hjgd.plm.agent.entity.AgentSession;
import com.hjgd.plm.agent.mapper.AgentActionMapper;
import com.hjgd.plm.agent.mapper.AgentMessageMapper;
import com.hjgd.plm.agent.mapper.AgentSessionMapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.dq.dto.DqRunResult;
import com.hjgd.plm.improve.entity.Insight;
import com.hjgd.plm.improve.service.ImproveService;
import com.hjgd.plm.material.service.MaterialService;
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

@Slf4j
@Tag(name = "V1 智能体编排器")
@RestController
@RequestMapping("/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;
    private final AgentActionMapper actionMapper;
    private final SequenceService sequenceService;
    private final MaterialService materialService;
    private final ImproveService improveService;
    private final JdbcTemplate jdbcTemplate;

    // ============ L0 只读快捷端点(保留兼容) ============

    @Operation(summary = "质检官:单料号检查")
    @PostMapping("/qa/check")
    public Result<DqRunResult> qaCheck(@RequestBody AgentReq req) {
        Long id = resolveId(req.getObjectId());
        return Result.success(materialService.checkQuality(id));
    }

    @Operation(summary = "分析师:开放洞察列表")
    @GetMapping("/analyst/insights")
    public Result<List<Insight>> analystInsights() {
        return Result.success(improveService.listInsights("NEW"));
    }

    @Operation(summary = "改善教练:建议摘要")
    @GetMapping("/coach/suggestions")
    public Result<Map<String, Object>> coachSuggestions() {
        List<Insight> insights = improveService.listInsights("NEW");
        long high = insights.stream().filter(i -> "HIGH".equals(i.getSeverity())).count();
        return Result.success(Map.of(
                "totalOpenInsights", insights.size(),
                "highSeverity", high,
                "suggestion", high > 0 ? "建议优先处理" + high + "条高严重洞察" : "暂无紧急改善项"
        ));
    }

    // ============ 会话管理 ============

    @Operation(summary = "创建会话")
    @PostMapping("/{agentCode}/sessions")
    public Result<AgentSession> createSession(@PathVariable String agentCode,
                                              @RequestBody(required = false) CreateSessionReq req) {
        AgentSession session = new AgentSession();
        session.setSessionNo(sequenceService.nextNo("AGENT_SESSION"));
        session.setAgentCode(agentCode);
        try { session.setUserId(SecurityUtils.getCurrentUserId()); } catch (Exception ignored) {}
        session.setChannel(req == null ? "UI" : req.getChannel());
        session.setContextJson(req == null ? null : req.getContextJson());
        session.setStatus("OPEN");
        session.setStartedAt(OffsetDateTime.now());
        sessionMapper.insert(session);
        return Result.success(session);
    }

    @Operation(summary = "会话列表")
    @GetMapping("/sessions")
    public Result<List<AgentSession>> listSessions(@RequestParam(defaultValue = "20") int limit) {
        LambdaQueryWrapper<AgentSession> w = new LambdaQueryWrapper<>();
        w.orderByDesc(AgentSession::getStartedAt).last("LIMIT " + Math.min(limit, 100));
        return Result.success(sessionMapper.selectList(w));
    }

    @Operation(summary = "会话详情")
    @GetMapping("/sessions/{id}")
    public Result<AgentSession> getSession(@PathVariable Long id) {
        return Result.success(sessionMapper.selectById(id));
    }

    @Operation(summary = "关闭会话")
    @PostMapping("/sessions/{id}/close")
    public Result<Void> closeSession(@PathVariable Long id) {
        AgentSession s = sessionMapper.selectById(id);
        if (s != null) {
            s.setStatus("CLOSED");
            s.setEndedAt(OffsetDateTime.now());
            sessionMapper.updateById(s);
        }
        return Result.success();
    }

    // ============ 消息 ============

    @Operation(summary = "发送消息并取回复(L0:模板规则式,无LLM)")
    @PostMapping("/sessions/{id}/messages")
    public Result<Map<String, Object>> send(@PathVariable Long id, @RequestBody SendReq req) {
        AgentSession session = sessionMapper.selectById(id);
        if (session == null) return Result.failed(404, "会话不存在");
        if (!"OPEN".equals(session.getStatus())) return Result.failed(409, "会话已关闭");

        // 写用户消息
        AgentMessage userMsg = new AgentMessage();
        userMsg.setSessionId(id);
        userMsg.setRole("user");
        userMsg.setContent(req.getContent());
        userMsg.setCreatedAt(OffsetDateTime.now());
        messageMapper.insert(userMsg);

        // 根据 agentCode + content 生成回复(模板式 L0)
        AgentAction action = null;
        String reply = simpleAgentReply(session.getAgentCode(), req.getContent(), session);
        if (reply == null) reply = "收到: " + req.getContent();

        // 如果是 QA + partNo, 自动跑 DQ 并建 action
        if ("AGENT_QA".equals(session.getAgentCode()) && req.getPartNo() != null) {
            try {
                MaterialService svc = materialService;
                Long mid = resolveIdByPartNo(req.getPartNo());
                DqRunResult dq = mid != null ? svc.checkQuality(mid) : null;
                if (dq != null) {
                    reply = "料号 " + req.getPartNo() + " 质量分:" + dq.getScore() +
                            " | BLOCK:" + dq.getBlockCount() + " WARN:" + dq.getWarnCount();
                    action = new AgentAction();
                    action.setSessionId(id);
                    action.setActionType("dq_check");
                    action.setObjectType("PART");
                    action.setObjectId(req.getPartNo());
                    action.setPayloadJson("{\"score\":" + dq.getScore() + "}");
                    action.setStatus("PROPOSED");
                    action.setEstimatedMinutesSaved(15);
                    action.setCreatedAt(OffsetDateTime.now());
                    actionMapper.insert(action);
                }
            } catch (Exception e) {
                log.debug("agent dq auto-run failed: {}", e.getMessage());
            }
        }

        AgentMessage aiMsg = new AgentMessage();
        aiMsg.setSessionId(id);
        aiMsg.setRole("assistant");
        aiMsg.setContent(reply);
        aiMsg.setCreatedAt(OffsetDateTime.now());
        messageMapper.insert(aiMsg);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userMessage", userMsg);
        result.put("assistantMessage", aiMsg);
        result.put("pendingAction", action);
        return Result.success(result);
    }

    @Operation(summary = "会话消息列表")
    @GetMapping("/sessions/{id}/messages")
    public Result<List<AgentMessage>> messages(@PathVariable Long id) {
        return Result.success(messageMapper.selectList(
                new LambdaQueryWrapper<AgentMessage>()
                        .eq(AgentMessage::getSessionId, id)
                        .orderByAsc(AgentMessage::getCreatedAt)));
    }

    // ============ 动作(决策) ============

    @Operation(summary = "建议列表(待决策)")
    @GetMapping("/actions")
    public Result<List<AgentAction>> pendingActions(@RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "50") int limit) {
        LambdaQueryWrapper<AgentAction> w = new LambdaQueryWrapper<>();
        if (status != null) w.eq(AgentAction::getStatus, status);
        w.orderByDesc(AgentAction::getCreatedAt).last("LIMIT " + Math.min(limit, 100));
        return Result.success(actionMapper.selectList(w));
    }

    @Operation(summary = "采纳/驳回动作")
    @PostMapping("/actions/{id}/decide")
    public Result<AgentAction> decide(@PathVariable Long id, @RequestBody DecideReq req) {
        AgentAction a = actionMapper.selectById(id);
        if (a == null) return Result.failed(404, "动作不存在");
        a.setStatus("APPROVED".equalsIgnoreCase(req.getDecision()) ? "APPROVED" : "REJECTED");
        a.setDecidedAt(OffsetDateTime.now());
        try { a.setDecidedBy(SecurityUtils.getCurrentUserId()); } catch (Exception ignored) {}
        actionMapper.updateById(a);
        return Result.success(a);
    }

    @Operation(summary = "采纳率指标(供 /v1/metrics/query 调用)")
    @GetMapping("/actions/stats")
    public Result<Map<String, Object>> adoptStats() {
        try {
            Map<String, Object> approved = jdbcTemplate.queryForMap(
                    "SELECT COUNT(1) AS c FROM plm_agent_action WHERE status='APPROVED'");
            Map<String, Object> rejected = jdbcTemplate.queryForMap(
                    "SELECT COUNT(1) AS c FROM plm_agent_action WHERE status='REJECTED'");
            return Result.success(Map.of(
                    "approved", approved.get("c"),
                    "rejected", rejected.get("c")
            ));
        } catch (Exception e) {
            return Result.success(Map.of("approved", 0, "rejected", 0));
        }
    }

    // ============ 内部 ============

    private Long resolveId(String idOrPartNo) {
        if (idOrPartNo == null) return null;
        try { return Long.parseLong(idOrPartNo); }
        catch (NumberFormatException e) {
            var m = materialService.getByPartNo(idOrPartNo);
            return m == null ? null : m.getId();
        }
    }

    private Long resolveIdByPartNo(String partNo) {
        var m = materialService.getByPartNo(partNo);
        return m == null ? null : m.getId();
    }

    private String simpleAgentReply(String agentCode, String content, AgentSession session) {
        if (content == null) return "请告诉我需要帮助的内容。";
        String lower = content.toLowerCase();
        switch (agentCode) {
            case "AGENT_QA":
                if (lower.contains("检查") || lower.contains("check") || lower.contains("质量")) {
                    return "请提供料号(partNo 字段),我将自动跑 DQ 规则并返回质量分。";
                }
                return "QA 智能体已就绪。支持: 单料号检查 / 全库巡检 / 债务查询。";
            case "AGENT_ANALYST":
                return "分析中: 当前 /v1/analytics/insights 返回开放洞察列表,请选择方向。";
            case "AGENT_COACH":
                return "改善教练: 请告诉我痛点或高严重洞察 ID,我将帮你拆 5Why + 改善动作。";
            case "AGENT_BOM":
                return "BOM 助理: 可调用 /v1/boms/{}/tree 与 where-used。";
            case "AGENT_ECN":
                return "ECN 助理: 草稿/审批/生效均可由我代理,请提供 ECN 单号。";
            case "AGENT_GATE":
                return "技转助理: 可查项目阶段门 G0-G8 通过状态。";
            case "AGENT_HELP":
                return "你好,我是 PLM 助手。请描述你需要的功能或问题。";
            case "AGENT_ORCH":
                return "总控已激活 — 根据你的问题自动路由到 QA / Analyst / Coach / BOM / ECN。";
            default:
                return "智能体 " + agentCode + " 收到: " + content;
        }
    }

    @Data public static class AgentReq {
        private String agentCode;
        private String objectType;
        private String objectId;
    }

    @Data public static class CreateSessionReq {
        private String channel;
        private String contextJson;
    }

    @Data public static class SendReq {
        private String content;
        private String partNo;
    }

    @Data public static class DecideReq {
        private String decision;
        private String comment;
    }
}
