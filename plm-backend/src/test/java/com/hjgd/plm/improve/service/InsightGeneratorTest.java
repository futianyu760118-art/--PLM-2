package com.hjgd.plm.improve.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 洞察生成 Job 测试：严重度阈值 + DQ债务→洞察→HIGH自动转ISSUE。
 */
@DisplayName("洞察生成 Job")
@ExtendWith(MockitoExtension.class)
class InsightGeneratorTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ImproveService improveService;
    @InjectMocks private InsightGenerator generator;

    @Test
    @DisplayName("严重度阈值: 债务≥5 或 WARN → HIGH")
    void severityThresholds() {
        assertEquals("HIGH", InsightGenerator.severityOf(5, "INFO"));
        assertEquals("HIGH", InsightGenerator.severityOf(1, "WARN"));
        assertEquals("MEDIUM", InsightGenerator.severityOf(3, "INFO"));
        assertEquals("LOW", InsightGenerator.severityOf(1, "INFO"));
    }

    @Test
    @DisplayName("DQ债务 TOP → 生成洞察; HIGH 自动转 ISSUE")
    void generatesInsightAndAutoIssueForHigh() {
        // 两条债务规则: PART_UNIT_WARN(8条,INFO→HIGH), PARAM_REQUIRED_IP_RATING(2条,INFO→LOW)
        List<Map<String, Object>> rows = List.of(
                Map.of("rule_code", "PART_UNIT_WARN", "severity", "INFO", "cnt", 8, "msg", "建议填写单位"),
                Map.of("rule_code", "PARAM_REQUIRED_IP_RATING", "severity", "INFO", "cnt", 2, "msg", "建议填写IP防护等级")
        );
        when(jdbcTemplate.queryForList(anyString())).thenReturn(rows);
        // 幂等检查：均无 NEW 洞察
        when(jdbcTemplate.queryForObject(contains("plm_analytics_insight"), eq(Integer.class), anyString()))
                .thenReturn(0);
        // createInsight 回填 id
        when(improveService.createInsight(any())).thenAnswer(inv -> {
            ((com.hjgd.plm.improve.entity.Insight) inv.getArgument(0)).setId(System.nanoTime());
            return null;
        });

        int n = generator.generateFromDqDebt();

        assertEquals(2, n);
        verify(improveService, times(2)).createInsight(any());
        // 仅 HIGH(cnt=8) 自动转 ISSUE
        verify(improveService, times(1)).convertInsightToIssue(anyLong());
    }

    @Test
    @DisplayName("幂等: 同标题 NEW 洞察已存在则跳过")
    void idempotentSkipsExisting() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("rule_code", "PART_UNIT_WARN", "severity", "INFO", "cnt", 8, "msg", "建议填写单位")));
        // 已存在 NEW 洞察 → 跳过
        when(jdbcTemplate.queryForObject(contains("plm_analytics_insight"), eq(Integer.class), anyString()))
                .thenReturn(1);

        int n = generator.generateFromDqDebt();

        assertEquals(0, n);
        verify(improveService, never()).createInsight(any());
        verify(improveService, never()).convertInsightToIssue(anyLong());
    }

    @Test
    @DisplayName("无 OPEN 债务 → 返回 0")
    void noDebtReturnsZero() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());
        assertEquals(0, generator.generateFromDqDebt());
        verify(improveService, never()).createInsight(any());
    }

    @Test
    @DisplayName("DB 查询失败 → 安全返回 0, 不抛异常")
    void dbFailureReturnsZero() {
        when(jdbcTemplate.queryForList(anyString())).thenThrow(new RuntimeException("db down"));
        assertEquals(0, generator.generateFromDqDebt());
        verify(improveService, never()).createInsight(any());
    }

    @Test
    @DisplayName("createInsight 后 DB 异常 → 异常被吞噬, 仍计数")
    void createInsightFailureSwallowed() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("rule_code", "PART_X", "severity", "INFO", "cnt", 8, "msg", "x")));
        when(jdbcTemplate.queryForObject(contains("plm_analytics_insight"), eq(Integer.class), anyString()))
                .thenReturn(0);
        doThrow(new RuntimeException("insert insight fail")).when(improveService).createInsight(any());

        assertEquals(0, generator.generateFromDqDebt());
        // 不抛, 不转 ISSUE
        verify(improveService, never()).convertInsightToIssue(anyLong());
    }

    @Test
    @DisplayName("rule_code 为空字符串 → 跳过")
    void emptyRuleCodeIsSkipped() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("rule_code", "", "severity", "INFO", "cnt", 1, "msg", "blank"),
                Map.of("rule_code", "VALID", "severity", "INFO", "cnt", 2, "msg", "ok")));
        when(jdbcTemplate.queryForObject(contains("plm_analytics_insight"), eq(Integer.class), anyString()))
                .thenReturn(0);
        when(improveService.createInsight(any())).thenAnswer(inv -> {
            ((com.hjgd.plm.improve.entity.Insight) inv.getArgument(0)).setId(System.nanoTime());
            return null;
        });

        int n = generator.generateFromDqDebt();
        assertEquals(1, n);
        verify(improveService, times(1)).createInsight(any());
    }
}
