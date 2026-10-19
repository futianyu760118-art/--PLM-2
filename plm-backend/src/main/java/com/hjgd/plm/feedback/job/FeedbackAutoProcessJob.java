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
 * 反馈自动处理：用户提交反馈后，由 AI 自动接手并给出初步处理答复（无需人工派单）。
 * 状态流转：pending(待处理) → processing(处理中，AI 已回复初步方案)。
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
                "SELECT id, type, severity, title, description, page_path FROM plm_feedback " +
                        "WHERE deleted=0 AND status='pending' AND (ai_status IS NULL OR ai_status='none') ORDER BY id LIMIT 10");
        for (Map<String, Object> r : rows) {
            Long id = ((Number) r.get("id")).longValue();
            String sys = "你是恒剑PLM的反馈处理智能体。用户刚提交了一条问题/建议，请自动给出初步处理："
                    + "先一句话确认收到，再判断问题类型与可能影响，最后给出处理计划或建议（涉及功能改进时说明打算怎么改）。"
                    + "用中文简洁回复、分点列出，不要编造系统里不存在的信息。";
            String user = "类型:" + r.get("type") + " 严重度:" + r.get("severity")
                    + " 所在页面:" + (r.get("page_path") == null ? "" : r.get("page_path"))
                    + "\n标题:" + r.get("title")
                    + "\n描述:" + (r.get("description") == null ? "" : r.get("description"));
            try {
                String reply = llmService.chat(sys, user);
                if (reply == null || reply.isBlank()) continue;
                jdbc.update("UPDATE plm_feedback SET status='processing', reply=?, replied_by='AI 智能体', replied_at=now(), updated_at=now() WHERE id=?",
                        reply, id);
                log.info("[反馈自动处理] #{} 已生成初步答复", id);
            } catch (Exception e) {
                log.warn("[反馈自动处理] #{} 处理失败: {}", id, e.getMessage());
            }
        }
    }
}
