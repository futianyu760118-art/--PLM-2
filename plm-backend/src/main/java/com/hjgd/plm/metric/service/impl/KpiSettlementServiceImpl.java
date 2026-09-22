package com.hjgd.plm.metric.service.impl;

import com.hjgd.plm.metric.service.KpiSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.*;

/**
 * KPI 周期结算实现。
 * - actual_value = 周期内该 metric 最新日值（与度量日批「当日快照」语义一致）。
 * - 红黄绿按 plm_metric_def.direction 与 plm_kpi_def 三阈值比较。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiSettlementServiceImpl implements KpiSettlementService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Map<String, Object>> settle() {
        List<Map<String, Object>> defs;
        try {
            defs = jdbcTemplate.queryForList(
                    "SELECT k.kpi_code, k.metric_code, k.period_type, "
                            + "k.threshold_green, k.threshold_yellow, k.threshold_red, "
                            + "m.direction "
                            + "FROM plm_kpi_def k JOIN plm_metric_def m ON k.metric_code = m.metric_code "
                            + "WHERE k.enabled = true AND m.enabled = true");
        } catch (Exception e) {
            log.warn("[KPI] load defs failed: {}", e.getMessage());
            return Map.of();
        }
        Map<String, Map<String, Object>> done = new LinkedHashMap<>();
        for (Map<String, Object> def : defs) {
            String kpi = str(def.get("kpi_code"));
            String metric = str(def.get("metric_code"));
            String periodType = str(def.get("period_type"));
            try {
                Window w = window(periodType);
                BigDecimal actual = latestValue(metric, w);
                if (actual == null) {
                    done.put(kpi, Map.of("status", "NO_DATA", "period", w.key));
                    continue;
                }
                String status = trafficLight(actual, def);
                BigDecimal score = scoreOf(status);
                upsert(kpi, w.key, actual, score, status);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("actual", actual);
                r.put("status", status);
                r.put("score", score);
                r.put("period", w.key);
                done.put(kpi, r);
            } catch (Exception e) {
                log.warn("[KPI] {} 结算失败: {}", kpi, e.getMessage());
                done.put(kpi, Map.of("status", "ERROR", "msg", e.getMessage()));
            }
        }
        log.info("[KPI] settle done count={}", done.size());
        return done;
    }

    private BigDecimal latestValue(String metric, Window w) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT value_calc FROM plm_metric_value WHERE metric_code=? "
                            + "AND grain_time BETWEEN ? AND ? ORDER BY grain_time DESC, id DESC LIMIT 1",
                    BigDecimal.class, metric, Date.valueOf(w.from), Date.valueOf(w.to));
        } catch (Exception e) {
            return null;
        }
    }

    private String trafficLight(BigDecimal actual, Map<String, Object> def) {
        String direction = str(def.get("direction"));
        BigDecimal green = num(def.get("threshold_green"));
        BigDecimal yellow = num(def.get("threshold_yellow"));
        boolean higher = "HIGHER_BETTER".equalsIgnoreCase(direction);
        if (higher) {
            if (green != null && actual.compareTo(green) >= 0) return "GREEN";
            if (yellow != null && actual.compareTo(yellow) >= 0) return "YELLOW";
            return "RED";
        } else {
            if (green != null && actual.compareTo(green) <= 0) return "GREEN";
            if (yellow != null && actual.compareTo(yellow) <= 0) return "YELLOW";
            return "RED";
        }
    }

    private BigDecimal scoreOf(String status) {
        return switch (status) {
            case "GREEN" -> new BigDecimal("92");
            case "YELLOW" -> new BigDecimal("65");
            default -> new BigDecimal("30");
        };
    }

    private void upsert(String kpi, String periodKey, BigDecimal actual, BigDecimal score, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO plm_kpi_value(kpi_code,period_key,actual_value,score,status,updated_at)
                VALUES(?,?,?,?,?,NOW())
                ON CONFLICT(kpi_code,period_key) DO UPDATE SET
                  actual_value=EXCLUDED.actual_value,
                  score=EXCLUDED.score,
                  status=EXCLUDED.status,
                  updated_at=NOW()
                """,
                kpi, periodKey, actual, score.setScale(2, RoundingMode.HALF_UP), status);
    }

    private Window window(String periodType) {
        LocalDate today = LocalDate.now();
        if ("WEEK".equalsIgnoreCase(periodType)) {
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate sunday = monday.plusDays(6);
            int weekNum = today.get(WeekFields.ISO.weekOfYear());
            return new Window(monday, sunday, today.getYear() + "-W" + String.format("%02d", weekNum));
        }
        if ("QUARTER".equalsIgnoreCase(periodType)) {
            LocalDate qStart = today.with(today.getMonth().firstMonthOfQuarter())
                    .with(TemporalAdjusters.firstDayOfMonth());
            int q = (today.getMonthValue() - 1) / 3 + 1;
            return new Window(qStart, today, today.getYear() + "-Q" + q);
        }
        // 默认 MONTH
        YearMonth ym = YearMonth.from(today);
        return new Window(ym.atDay(1), ym.atEndOfMonth(), ym.toString());
    }

    private record Window(LocalDate from, LocalDate to, String key) {}

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static BigDecimal num(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(o.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
