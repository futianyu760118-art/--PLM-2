package com.hjgd.plm.feedback.job;

import com.hjgd.plm.agent.service.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 反馈自动分析：用户提交反馈后，AI 自动生成可执行方案（非代码），供提交人确认。
 * 状态流转：pending(待分析) → proposed(待确认方案，AI 已出方案)。
 * 提交人确认方案后(approved)，再由管理员派单给 AI Runner 做代码实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackAutoProcessJob {

    private final JdbcTemplate jdbc;
    private final LlmService llmService;

    @Scheduled(fixedDelay = 30000, initialDelay = 15000)
    public void autoProcess() {
        if (!llmService.enabled()) return;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, type, severity, title, description, page_path, reject_note FROM plm_feedback " +
                        "WHERE deleted=0 AND status='pending' AND (ai_status IS NULL OR ai_status='none') ORDER BY id LIMIT 10");
        for (Map<String, Object> r : rows) {
            Long id = ((Number) r.get("id")).longValue();
            String sys = "你是恒剑PLM的研发反馈分析智能体。请针对下面的用户反馈，输出一份可落地、可执行的技术方案（不是泛泛的安抚答复），包含三点："
                    + "1) 问题定位与根因；2) 具体改动方案（涉及哪些模块/文件/接口/表）；3) 验证方式。"
                    + "用中文分点、简洁、可落地，不要编造系统里不存在的信息，不要写代码正文。";
            String rejectNote = r.get("reject_note") == null ? null : String.valueOf(r.get("reject_note"));
            String user = "类型:" + r.get("type") + " 严重度:" + r.get("severity")
                    + " 所在页面:" + (r.get("page_path") == null ? "" : r.get("page_path"))
                    + "\n标题:" + r.get("title")
                    + "\n描述:" + (r.get("description") == null ? "" : r.get("description"));
            if (rejectNote != null && !rejectNote.isBlank()) {
                user += "\n（上次方案被驳回，驳回意见：" + rejectNote + "，请据此调整方案。）";
            }
            try {
                String proposal = llmService.chat(sys, user);
                if (proposal == null || proposal.isBlank()) continue;
                jdbc.update("UPDATE plm_feedback SET status='proposed', proposal=?, proposal_at=now(), reject_note=NULL, updated_at=now() WHERE id=?",
                        proposal, id);
                log.info("[反馈自动分析] #{} 已生成方案", id);
            } catch (Exception e) {
                log.warn("[反馈自动分析] #{} 分析失败: {}", id, e.getMessage());
            }
        }
    }
}
