package com.hjgd.plm.metric;

import com.hjgd.plm.metric.service.impl.KpiSettlementServiceImpl;
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
 * KPI 周期结算单测：验证红黄绿判定方向 + 幂等 upsert。
 */
@DisplayName("KPI 周期结算 V1.1")
@ExtendWith(MockitoExtension.class)
class KpiSettlementServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @InjectMocks private KpiSettlementServiceImpl service;

    private void stubDef(String kpi, String metric, String periodType,
                         String direction, String green, String yellow, String red) {
        Map<String, Object> def = Map.of(
                "kpi_code", kpi, "metric_code", metric, "period_type", periodType,
                "direction", direction,
                "threshold_green", new BigDecimal(green),
                "threshold_yellow", new BigDecimal(yellow),
                "threshold_red", new BigDecimal(red));
        lenient().when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(def));
    }

    @Nested
    @DisplayName("红黄绿判定方向")
    class TrafficLight {
        @Test
        @DisplayName("HIGHER_BETTER 达标=GREEN")
        void higherBetterGreen() {
            stubDef("KPI_DQ_SCORE", "M_DQ_SCORE", "WEEK", "HIGHER_BETTER", "90", "80", "70");
            when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any(Object[].class)))
                    .thenReturn(new BigDecimal("95"));
            Map<String, Map<String, Object>> done = service.settle();
            assertEquals("GREEN", done.get("KPI_DQ_SCORE").get("status"));
            verify(jdbcTemplate).update(anyString(), any(Object[].class));
        }

        @Test
        @DisplayName("HIGHER_BETTER 仅达黄线=YELLOW")
        void higherBetterYellow() {
            stubDef("KPI_DQ_SCORE", "M_DQ_SCORE", "WEEK", "HIGHER_BETTER", "90", "80", "70");
            when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any(Object[].class)))
                    .thenReturn(new BigDecimal("82"));
            assertEquals("YELLOW", service.settle().get("KPI_DQ_SCORE").get("status"));
        }

        @Test
        @DisplayName("HIGHER_BETTER 低于黄线=RED")
        void higherBetterRed() {
            stubDef("KPI_DQ_SCORE", "M_DQ_SCORE", "WEEK", "HIGHER_BETTER", "90", "80", "70");
            when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any(Object[].class)))
                    .thenReturn(new BigDecimal("60"));
            assertEquals("RED", service.settle().get("KPI_DQ_SCORE").get("status"));
        }

        @Test
        @DisplayName("LOWER_BETTER 低于绿线=GREEN")
        void lowerBetterGreen() {
            stubDef("KPI_ECN_CYCLE_H", "M_ECN_CYCLE_H", "MONTH", "LOWER_BETTER", "72", "120", "168");
            when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any(Object[].class)))
                    .thenReturn(new BigDecimal("50"));
            assertEquals("GREEN", service.settle().get("KPI_ECN_CYCLE_H").get("status"));
        }

        @Test
        @DisplayName("LOWER_BETTER 超过黄线=RED")
        void lowerBetterRed() {
            stubDef("KPI_ECN_CYCLE_H", "M_ECN_CYCLE_H", "MONTH", "LOWER_BETTER", "72", "120", "168");
            when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any(Object[].class)))
                    .thenReturn(new BigDecimal("200"));
            assertEquals("RED", service.settle().get("KPI_ECN_CYCLE_H").get("status"));
        }
    }

    @Test
    @DisplayName("周期内无 metric 数据标记 NO_DATA 不写库")
    void noDataSkipsUpsert() {
        stubDef("KPI_DQ_SCORE", "M_DQ_SCORE", "WEEK", "HIGHER_BETTER", "90", "80", "70");
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), any(Object[].class)))
                .thenReturn(null);
        Map<String, Map<String, Object>> done = service.settle();
        assertEquals("NO_DATA", done.get("KPI_DQ_SCORE").get("status"));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}
