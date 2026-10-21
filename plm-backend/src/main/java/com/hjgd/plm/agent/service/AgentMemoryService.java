package com.hjgd.plm.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体自学习记忆：从历史问答中学习，多维度检索（历史对话 + 知识库 + 系统数据）。
 * 关键词召回（无向量库时的轻量实现），命中即作为上下文反哺给大模型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMemoryService {

    private final JdbcTemplate jdbc;

    /** 召回最接近的历史问答（按关键词重叠度评分，取最接近的 topN） */
    public List<Map<String, Object>> recall(String question, int limit) {
        if (question == null || question.isBlank()) return List.of();
        List<String> kws = keywords(question);
        if (kws.isEmpty()) return List.of();
        List<Map<String, Object>> candidates = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (String kw : kws) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, question, answer, source, score FROM plm_agent_memory " +
                            "WHERE deleted=0 AND (question ILIKE ? OR tags ILIKE ?) ORDER BY score DESC, updated_at DESC LIMIT 20",
                    "%" + kw + "%", "%" + kw + "%");
            for (Map<String, Object> r : rows) {
                Long id = ((Number) r.get("id")).longValue();
                if (seen.add(id)) candidates.add(r);
            }
        }
        // 关键词重叠度评分：重叠越多越接近
        for (Map<String, Object> c : candidates) {
            List<String> ckws = keywords(String.valueOf(c.get("question")));
            int overlap = 0;
            for (String k : kws) if (ckws.contains(k)) overlap++;
            c.put("_overlap", overlap);
        }
        candidates.sort((a, b) -> {
            int d = Integer.compare((Integer) b.get("_overlap"), (Integer) a.get("_overlap"));
            if (d != 0) return d;
            return Integer.compare((Integer) b.get("score"), (Integer) a.get("score"));
        });
        int n = Math.min(limit, candidates.size());
        List<Map<String, Object>> out = candidates.subList(0, n);
        for (Map<String, Object> m : out) m.remove("_overlap");
        return out;
    }

    /** 记忆一条问答（去重：同问题只保留最新） */
    public void remember(String question, String answer, String source) {
        if (question == null || question.isBlank()) return;
        if (answer == null || answer.isBlank()) return;
        if (question.length() > 300 || answer.length() > 2000) return;
        try {
            int n = jdbc.update(
                    "UPDATE plm_agent_memory SET answer=?, source=?, score=score+1, updated_at=NOW() WHERE question=? AND deleted=0",
                    answer, source, question);
            if (n == 0) {
                jdbc.update("INSERT INTO plm_agent_memory(question, answer, tags, source, score) VALUES (?,?,?,?,1)",
                        question, answer, String.join(",", keywords(question)), source);
            }
        } catch (Exception e) {
            log.warn("[记忆] 写入失败: {}", e.getMessage());
        }
    }

    /** 系统概况（多维度上下文：项目/物料/节点/反馈/知识库统计） */
    public Map<String, Object> systemSnapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("projects", cnt("SELECT COUNT(*) FROM plm_project"));
        m.put("materials", cnt("SELECT COUNT(*) FROM plm_material"));
        m.put("nodesDone", cnt("SELECT COUNT(*) FROM plm_project_node WHERE status='DONE'"));
        m.put("feedbackPending", cnt("SELECT COUNT(*) FROM plm_feedback WHERE status='pending'"));
        m.put("knowledge", cnt("SELECT COUNT(*) FROM plm_knowledge WHERE deleted=0"));
        m.put("memory", cnt("SELECT COUNT(*) FROM plm_agent_memory WHERE deleted=0"));
        return m;
    }

    /** 知识库检索（多维度之一） */
    public List<Map<String, Object>> recallKnowledge(String question, int limit) {
        if (question == null || question.isBlank()) return List.of();
        List<String> kws = keywords(question);
        if (kws.isEmpty()) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (String kw : kws) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT title, LEFT(content, 800) AS content FROM plm_knowledge WHERE deleted=0 AND (title ILIKE ? OR content ILIKE ?) LIMIT 3",
                    "%" + kw + "%", "%" + kw + "%");
            out.addAll(rows);
            if (out.size() >= limit) break;
        }
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    private List<String> keywords(String q) {
        List<String> kws = new ArrayList<>();
        for (String t : q.split("[^\\p{L}\\p{N}]+")) {
            String s = t.trim();
            if (s.length() >= 2 && !kws.contains(s)) kws.add(s);
        }
        if (kws.size() > 6) kws = kws.subList(0, 6);
        return kws;
    }

    private long cnt(String sql) {
        try { Long v = jdbc.queryForObject(sql, Long.class); return v == null ? 0 : v; }
        catch (Exception e) { return 0; }
    }
}
