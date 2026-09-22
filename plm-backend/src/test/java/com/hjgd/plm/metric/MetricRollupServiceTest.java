package com.hjgd.plm.metric;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.config.JobProperties;
import com.hjgd.plm.metric.dto.MetricPoint;
import com.hjgd.plm.metric.service.impl.MetricRollupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 度量日批核心算子单测：验证 6 个指标计算口径与落库幂等。
 */
@DisplayName("度量日批算子 V1.1")
@ExtendWith(MockitoExtension.class)
class MetricRollupServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JobProperties jobProperties = new JobProperties();
    @InjectMocks private MetricRollupServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MetricRollupServiceImpl(jdbcTemplate, objectMapper, jobProperties);
    }

    private void stubAvg(double v) {
        lenient().when(jdbcTemplate.queryForObject(contains("AVG(score_0_100)"), eq(Double.class)))
                .thenReturn(v);
    }

    @Nested
    @DisplayName("M_DQ_SCORE 主数据质量均分")
    class DqScore {
        @Test
        @DisplayName("有评分取均分并落库")
        void shouldRollupAvg() {
            stubAvg(85.5);
            lenient().when(jdbcTemplate.queryForObject(contains("AVG(score_0_100)"), eq(Double.class)))
                    .thenReturn(85.5);
            Map<String, Object> done = service.runDaily();

            assertEquals(new BigDecimal("85.50"), done.get("M_DQ_SCORE"));
            // 删当日 + 插入 = 每个 metric 2 次 update；至少 DQ_SCORE 有 update
            verify(jdbcTemplate, atLeast(2)).update(anyString(), any(Object[].class));
        }

        @Test
        @DisplayName("无评分视为 0")
        void shouldDefaultZeroWhenNoData() {
            when(jdbcTemplate.queryForObject(contains("AVG(score_0_100)"), eq(Double.class)))
                    .thenReturn(null);
            Map<String, Object> done = service.runDaily();
            assertEquals(new BigDecimal("0.00"), done.get("M_DQ_SCORE"));
        }
    }

    @Nested
    @DisplayName("M_CODE_AUTO_RATE 自动编号率")
    class CodeAutoRate {
        @Test
        @DisplayName("无新建料号默认 100%")
        void shouldDefaultFullWhenNoNewPart() {
            when(jdbcTemplate.queryForObject(contains("plm_material WHERE deleted=0 AND created_at"), eq(Integer.class)))
                    .thenReturn(0);
            Map<String, Object> done = service.runDaily();
            assertEquals(new BigDecimal("100.00"), done.get("M_CODE_AUTO_RATE"));
        }

        @Test
        @DisplayName("10 新建中 9 系统生成 = 90%")
        void shouldComputeRate() {
            when(jdbcTemplate.queryForObject(contains("plm_material WHERE deleted=0 AND created_at"), eq(Integer.class)))
                    .thenReturn(10);
            when(jdbcTemplate.queryForObject(contains("EXISTS (SELECT 1 FROM plm_code_issue_log"), eq(Integer.class)))
                    .thenReturn(9);
            Map<String, Object> done = service.runDaily();
            assertEquals(new BigDecimal("90.00"), done.get("M_CODE_AUTO_RATE"));
        }
    }

    @Nested
    @DisplayName("M_ECN_CYCLE_H / M_WIP_DRAFT / M_BOM_REWORK_7D / M_DOC_COMPLETE")
    class OtherMetrics {
        @Test
        @DisplayName("ECN 无生效数据则跳过(返回空不落库)")
        void ecnCycleNoDataSkips() {
            when(jdbcTemplate.queryForObject(contains("AVG(EXTRACT(EPOCH"), eq(Double.class)))
                    .thenReturn(null);
            Map<String, Object> done = service.runDaily();
            assertFalse(done.containsKey("M_ECN_CYCLE_H"), "无数据应跳过不入 done");
        }

        @Test
        @DisplayName("超龄草稿计数")
        void wipDraftCount() {
            when(jdbcTemplate.queryForObject(contains("status IN ('DRAFT','REVIEWING')"), eq(Integer.class)))
                    .thenReturn(12);
            Map<String, Object> done = service.runDaily();
            assertEquals(new BigDecimal("12.00"), done.get("M_WIP_DRAFT"));
        }

        @Test
        @DisplayName("技转齐套率=已发布达标/已发布总数")
        void docCompleteRate() {
            when(jdbcTemplate.queryForObject(contains("status='RELEASED'"), eq(Integer.class)))
                    .thenReturn(20);   // den
            when(jdbcTemplate.queryForObject(contains("block_count=0 AND s.score_0_100 >= 90"), eq(Integer.class)))
                    .thenReturn(18);   // num
            Map<String, Object> done = service.runDaily();
            assertEquals(new BigDecimal("90.00"), done.get("M_DOC_COMPLETE"));
        }
    }

    @Test
    @DisplayName("dryRun 返回单行不落库")
    void dryRunShouldNotPersist() {
        when(jdbcTemplate.queryForObject(contains("AVG(score_0_100)"), eq(Double.class)))
                .thenReturn(77.0);
        List<Map<String, Object>> rows = service.dryRun("M_DQ_SCORE");
        assertEquals(1, rows.size());
        assertEquals(new BigDecimal("77.00"), rows.get(0).get("valueCalc"));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("单测异常 metric 不中断整批")
    void errorMetricDoesNotBreakBatch() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Double.class))).thenThrow(new RuntimeException("db down"));
        Map<String, Object> done = service.runDaily();
        assertTrue(done.toString().contains("M_DQ_SCORE"), "异常以 ERROR 记录而非抛出");
    }
}
