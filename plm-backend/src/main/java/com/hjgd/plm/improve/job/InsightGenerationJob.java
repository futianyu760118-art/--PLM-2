package com.hjgd.plm.improve.job;

import com.hjgd.plm.improve.entity.Insight;
import com.hjgd.plm.improve.mapper.InsightMapper;
import com.hjgd.plm.system.service.SequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class InsightGenerationJob {

    private final JdbcTemplate jdbcTemplate;
    private final InsightMapper insightMapper;
    private final SequenceService sequenceService;

    @Scheduled(cron = "0 15 2 * * ?")
    public void generateInsights() {
        log.info("=== [Job] InsightGeneration start ===");
        int created = 0;
        created += checkLowDqScore();
        created += checkHighDraftWip();
        created += checkStaleEcn();
        log.info("=== [Job] InsightGeneration done: {} insights ===", created);
    }

    private int checkLowDqScore() {
        try {
            List<Map<String, Object>> low = jdbcTemplate.queryForList("""
                    SELECT object_id, score_0_100, block_count, warn_count
                    FROM plm_dq_object_score
                    WHERE score_0_100 < 70 AND object_type='PART'
                    ORDER BY score_0_100 ASC LIMIT 20
                    """);
            for (Map<String, Object> row : low) {
                String partNo = String.valueOf(row.get("object_id"));
                createInsight("HIGH", "QUALITY",
                        "料号 " + partNo + " 质量分偏低(" + row.get("score_0_100") + ")",
                        "M_DQ_SCORE", "{\"partNo\":\"" + partNo + "\"}");
            }
            return low.size();
        } catch (Exception e) {
            log.warn("insight DQ check failed: {}", e.getMessage());
            return 0;
        }
    }

    private int checkHighDraftWip() {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM plm_material WHERE status='DRAFT' AND deleted=0 AND created_at < NOW() - INTERVAL '14 days'",
                    Long.class);
            if (count != null && count > 10) {
                createInsight("MEDIUM", "LEAN",
                        "超龄草稿料号过多: " + count + " 个(>14天未提交)",
                        "M_WIP_DRAFT", "{\"count\":" + count + "}");
                return 1;
            }
        } catch (Exception e) {
            log.warn("insight WIP check failed: {}", e.getMessage());
        }
        return 0;
    }

    private int checkStaleEcn() {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM plm_ecn WHERE status IN ('PENDING_L1','PENDING_L2') AND apply_time < NOW() - INTERVAL '7 days' AND deleted=0",
                    Long.class);
            if (count != null && count > 0) {
                createInsight("HIGH", "CYCLE",
                        "有 " + count + " 个ECN超7天未审结",
                        "M_ECN_CYCLE_H", "{\"count\":" + count + "}");
                return 1;
            }
        } catch (Exception e) {
            log.warn("insight ECN check failed: {}", e.getMessage());
        }
        return 0;
    }

    private void createInsight(String severity, String category, String title, String kpiCode, String finding) {
        try {
            Insight insight = new Insight();
            insight.setInsightNo("INS-" + System.currentTimeMillis());
            insight.setSource("JOB");
            insight.setTitle(title);
            insight.setSeverity(severity);
            insight.setCategory(category);
            insight.setFindingJson(finding);
            insight.setRelatedKpiCodes(kpiCode);
            insight.setStatus("NEW");
            insight.setCreatedAt(LocalDateTime.now());
            insightMapper.insert(insight);
            log.info("[Insight] {} - {}", severity, title);
        } catch (Exception e) {
            log.warn("createInsight failed: {}", e.getMessage());
        }
    }
}
