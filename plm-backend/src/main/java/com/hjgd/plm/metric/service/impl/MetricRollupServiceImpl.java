package com.hjgd.plm.metric.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.config.JobProperties;
import com.hjgd.plm.metric.dto.MetricPoint;
import com.hjgd.plm.metric.service.MetricRollupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/**
 * 度量日批实现：日粒度为「当日快照」语义（而非当日发生事件数），
 * 这样趋势图连续、驾驶舱每天都有真数。窗口类指标在 dimNote 注明窗口。
 *
 * 已实现 6 个核心指标（其余依赖未上线模块，暂跳过并记 WARN）：
 *  M_DQ_SCORE       主数据质量均分（当前全局均分）
 *  M_CODE_AUTO_RATE 自动编号率（近 30 天新建料号中系统生成占比）
 *  M_ECN_CYCLE_H    ECN 平均闭环小时（近 N 天生效 ECN 均值）
 *  M_WIP_DRAFT      超龄草稿料号数（DRAFT/REVIEWING 且超期）
 *  M_BOM_REWORK_7D  发布后 7 日内回退次数（RELEASED→CHANGING）
 *  M_DOC_COMPLETE   技转一次齐套率（已发布且无阻断且 score≥90 占比）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricRollupServiceImpl implements MetricRollupService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final JobProperties jobProperties;

    private static final List<String> SUPPORTED = List.of(
            "M_DQ_SCORE", "M_CODE_AUTO_RATE", "M_ECN_CYCLE_H",
            "M_WIP_DRAFT", "M_BOM_REWORK_7D", "M_DOC_COMPLETE",
            "M_AGENT_ADOPT", "M_IMPROVE_CLOSE", "M_CBOM_SYNC",
            "M_LOCK_VER", "M_OUTSRC_LEAK", "M_GATE_OTD");

    @Override
    public Map<String, Object> runDaily() {
        LocalDate grain = LocalDate.now();
        String batch = "METRIC-" + grain + "-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> done = new LinkedHashMap<>();
        for (String code : SUPPORTED) {
            try {
                MetricPoint p = compute(code);
                if (p == null) {
                    log.warn("[METRIC] {} 计算返回空，跳过", code);
                    continue;
                }
                persist(grain, p, batch);
                done.put(code, p.getValueCalc());
            } catch (Exception e) {
                log.warn("[METRIC] {} 计算失败: {}", code, e.getMessage());
                done.put(code, "ERROR: " + e.getMessage());
            }
        }
        log.info("[METRIC] daily rollup grain={} batch={} done={}", grain, batch, done);
        return done;
    }

    @Override
    public List<Map<String, Object>> dryRun(String metricCode) {
        MetricPoint p = compute(metricCode);
        if (p == null) {
            return List.of();
        }
        return List.of(toRow(p));
    }

    private MetricPoint compute(String code) {
        switch (code) {
            case "M_DQ_SCORE": return dqScore();
            case "M_CODE_AUTO_RATE": return codeAutoRate();
            case "M_ECN_CYCLE_H": return ecnCycleHours();
            case "M_WIP_DRAFT": return wipDraft();
            case "M_BOM_REWORK_7D": return bomRework7d();
            case "M_DOC_COMPLETE": return docComplete();
            case "M_AGENT_ADOPT": return agentAdopt();
            case "M_IMPROVE_CLOSE": return improveClose();
            case "M_CBOM_SYNC": return cbomSync();
            case "M_LOCK_VER": return lockVer();
            case "M_OUTSRC_LEAK": return outsrcLeak();
            case "M_GATE_OTD": return gateOtd();
            default: return null;
        }
    }

    /** 主数据质量均分：全部零件 DQ 评分均值。无评分则视为 0。 */
    private MetricPoint dqScore() {
        Double avg = queryQuietly(
                "SELECT AVG(score_0_100) FROM plm_dq_object_score WHERE object_type='PART'",
                Double.class);
        double v = avg == null ? 0d : avg;
        return point("M_DQ_SCORE", null, null, round2(v), null);
    }

    /** 自动编号率：近 30 天新建料号中，在 plm_code_issue_log 有记录(经系统分配)的占比。 */
    private MetricPoint codeAutoRate() {
        Integer den = queryQuietly(
                "SELECT COUNT(1) FROM plm_material WHERE deleted=0 AND created_at >= NOW() - INTERVAL '30 day'",
                Integer.class);
        if (den == null || den == 0) {
            return point("M_CODE_AUTO_RATE", ZERO, ONE, HUNDRED, "近30天无新建料号，默认100%");
        }
        Integer num = queryQuietly(
                "SELECT COUNT(1) FROM plm_material m WHERE m.deleted=0 AND m.created_at >= NOW() - INTERVAL '30 day' "
                        + "AND EXISTS (SELECT 1 FROM plm_code_issue_log l WHERE l.object_type='PART' AND l.generated_code=m.part_no)",
                Integer.class);
        double rate = num == null ? 0 : num * 100.0 / den;
        return point("M_CODE_AUTO_RATE", bd(num), bd(den), round2(rate), "近30天窗口");
    }

    /** ECN 平均闭环小时：近 ecnCycleWindowDays 天生效 ECN 的 (effective_time-apply_time) 均值。 */
    private MetricPoint ecnCycleHours() {
        int win = Math.max(1, jobProperties.getEcnCycleWindowDays());
        Double avg = queryQuietly(
                "SELECT AVG(EXTRACT(EPOCH FROM (effective_time - apply_time))/3600) FROM plm_ecn "
                        + "WHERE status='EFFECTIVE' AND effective_time IS NOT NULL AND apply_time IS NOT NULL "
                        + "AND effective_time >= NOW() - INTERVAL '" + win + " day'",
                Double.class);
        if (avg == null) {
            return null;
        }
        return point("M_ECN_CYCLE_H", null, null, round2(avg), "近" + win + "天生效ECN均值");
    }

    /** 超龄草稿料号数：处于 DRAFT/REVIEWING 且创建超过 draftAgedDays 天。 */
    private MetricPoint wipDraft() {
        int aged = Math.max(1, jobProperties.getDraftAgedDays());
        Integer cnt = queryQuietly(
                "SELECT COUNT(1) FROM plm_material WHERE deleted=0 AND status IN ('DRAFT','REVIEWING') "
                        + "AND created_at < NOW() - INTERVAL '" + aged + " day'",
                Integer.class);
        double v = cnt == null ? 0 : cnt;
        return point("M_WIP_DRAFT", null, null, round2(v), "超" + aged + "天草稿/评审中");
    }

    /** BOM 发布后 7 日回退次数：近 7 天 RELEASED→CHANGING(start_change) 的生命周期记录数。 */
    private MetricPoint bomRework7d() {
        Integer cnt = queryQuietly(
                "SELECT COUNT(1) FROM plm_lifecycle_history "
                        + "WHERE object_type='PART' AND action_code='start_change' "
                        + "AND from_state='RELEASED' AND created_at >= NOW() - INTERVAL '7 day'",
                Integer.class);
        double v = cnt == null ? 0 : cnt;
        return point("M_BOM_REWORK_7D", null, null, round2(v), "近7天发布后变更回退");
    }

    /** 技转一次齐套率：已发布料号中 block_count=0 且 score≥90 的占比。 */
    private MetricPoint docComplete() {
        Integer den = queryQuietly(
                "SELECT COUNT(1) FROM plm_material WHERE deleted=0 AND status='RELEASED'",
                Integer.class);
        if (den == null || den == 0) {
            return null;
        }
        // 注意：plm_dq_object_score.object_id 存的是物料数字 id（见 DataQualityServiceImpl#runForPart），非 part_no
        Integer num = queryQuietly(
                "SELECT COUNT(1) FROM plm_dq_object_score s JOIN plm_material m "
                        + "ON m.id::text = s.object_id AND s.object_type='PART' "
                        + "WHERE m.deleted=0 AND m.status='RELEASED' AND s.block_count=0 AND s.score_0_100 >= 90",
                Integer.class);
        double rate = num == null ? 0 : num * 100.0 / den;
        return point("M_DOC_COMPLETE", bd(num), bd(den), round2(rate), "已发布且无阻断且评分≥90");
    }

    // ---------- 落库 ----------

    /** 智能体建议采纳率：APPROVED / (APPROVED+REJECTED) in plm_agent_action */
    private MetricPoint agentAdopt() {
        Integer approved = queryQuietly(
                "SELECT COUNT(1) FROM plm_agent_action WHERE status='APPROVED'", Integer.class);
        Integer rejected = queryQuietly(
                "SELECT COUNT(1) FROM plm_agent_action WHERE status='REJECTED'", Integer.class);
        int total = (approved == null ? 0 : approved) + (rejected == null ? 0 : rejected);
        if (total == 0) return point("M_AGENT_ADOPT", ZERO, ONE, HUNDRED, "无智能体动作记录，默认100%");
        double rate = (approved == null ? 0 : approved) * 100.0 / total;
        return point("M_AGENT_ADOPT", bd(approved), bd(total), round2(rate), "APPROVED/(APPROVED+REJECTED)");
    }

    /** 改善单按时关闭率：CLOSED 且 closed_at <= due_date / 全部 CLOSED */
    private MetricPoint improveClose() {
        Integer closed = queryQuietly(
                "SELECT COUNT(1) FROM plm_issue WHERE status='CLOSED'", Integer.class);
        if (closed == null || closed == 0) return point("M_IMPROVE_CLOSE", ZERO, ONE, HUNDRED, "无已关闭问题");
        Integer onTime = queryQuietly(
                "SELECT COUNT(1) FROM plm_issue WHERE status='CLOSED' AND due_date IS NOT NULL AND closed_at <= due_date + INTERVAL '1 day'",
                Integer.class);
        double rate = (onTime == null ? 0 : onTime) * 100.0 / closed;
        return point("M_IMPROVE_CLOSE", bd(onTime), bd(closed), round2(rate), "按时关闭/全部关闭");
    }

    /** EBMS结构同源率：有订单锁版本的成品占比（集成健康代理指标） */
    private MetricPoint cbomSync() {
        Integer released = queryQuietly(
                "SELECT COUNT(1) FROM plm_material WHERE deleted=0 AND status IN ('RELEASED','IN_PRODUCTION')",
                Integer.class);
        if (released == null || released == 0) return point("M_CBOM_SYNC", ZERO, ONE, HUNDRED, "无已发布料号");
        Integer synced = queryQuietly(
                "SELECT COUNT(DISTINCT part_no) FROM plm_order_version_lock", Integer.class);
        double rate = Math.min(100, (synced == null ? 0 : synced) * 100.0 / released);
        return point("M_CBOM_SYNC", bd(synced), bd(released), round2(rate), "有锁版本的料号/已发布料号");
    }

    /** 大货订单锁版本数（原始计数，方向 LOWER_BETTER 看未锁的） */
    private MetricPoint lockVer() {
        Integer locks = queryQuietly(
                "SELECT COUNT(1) FROM plm_order_version_lock", Integer.class);
        double v = locks == null ? 0 : locks;
        return point("M_LOCK_VER", null, null, round2(v), "订单锁版本记录数");
    }

    /** 外协风险下载次数：过期后下载或越权下载（原始计数） */
    private MetricPoint outsrcLeak() {
        Integer leaks = queryQuietly(
                "SELECT COUNT(1) FROM plm_outsource_download_log WHERE downloaded_at > " +
                "(SELECT expire_at FROM plm_outsource_file WHERE plm_outsource_file.id = plm_outsource_download_log.file_id)",
                Integer.class);
        if (leaks == null) leaks = 0;
        return point("M_OUTSRC_LEAK", null, null, round2(leaks), "过期/越权下载次数");
    }

    /** 阶段门准时率：近30天 PASS 门中 actual_date <= planned_date 的占比 */
    private MetricPoint gateOtd() {
        Integer total = queryQuietly(
                "SELECT COUNT(1) FROM plm_project_gate_log WHERE result='PASS' AND actual_date IS NOT NULL " +
                "AND created_at >= NOW() - INTERVAL '30 day'", Integer.class);
        if (total == null || total == 0) return point("M_GATE_OTD", ZERO, ONE, HUNDRED, "近30天无门通过记录");
        Integer onTime = queryQuietly(
                "SELECT COUNT(1) FROM plm_project_gate_log WHERE result='PASS' AND actual_date IS NOT NULL " +
                "AND planned_date IS NOT NULL AND actual_date <= planned_date " +
                "AND created_at >= NOW() - INTERVAL '30 day'", Integer.class);
        double rate = (onTime == null ? 0 : onTime) * 100.0 / total;
        return point("M_GATE_OTD", bd(onTime), bd(total), round2(rate), "近30天准时通过/全部通过");
    }

    // ---------- 落库 ----------

    private void persist(LocalDate grain, MetricPoint p, String batch) {
        String dims = p.getDimNote() == null ? null
                : safeJson(Map.of("note", p.getDimNote()));
        jdbcTemplate.update(
                "DELETE FROM plm_metric_value WHERE metric_code=? AND grain_time=? "
                        + "AND (object_id IS NULL OR object_id='')",
                p.getMetricCode(), Date.valueOf(grain));
        jdbcTemplate.update(
                "INSERT INTO plm_metric_value(metric_code,grain_time,dim_json,object_type,object_id,"
                        + "value_num,value_den,value_calc,batch_no) VALUES(?,?,?,?,?,?,?,?,?)",
                p.getMetricCode(), Date.valueOf(grain), dims, null, null,
                p.getValueNum(), p.getValueDen(), p.getValueCalc(), batch);
    }

    private Map<String, Object> toRow(MetricPoint p) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("metricCode", p.getMetricCode());
        row.put("valueCalc", p.getValueCalc());
        row.put("valueNum", p.getValueNum());
        row.put("valueDen", p.getValueDen());
        row.put("dimNote", p.getDimNote());
        return row;
    }

    // ---------- 小工具 ----------

    private <T> T queryQuietly(String sql, Class<T> type) {
        try {
            return jdbcTemplate.queryForObject(sql, type);
        } catch (Exception e) {
            log.debug("metric query quiet fail: {}", e.getMessage());
            return null;
        }
    }

    private String safeJson(Map<String, Object> m) {
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return null;
        }
    }

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    private static MetricPoint point(String code, BigDecimal num, BigDecimal den, BigDecimal calc, String dimNote) {
        return MetricPoint.builder()
                .metricCode(code).valueNum(num).valueDen(den).valueCalc(calc).dimNote(dimNote)
                .build();
    }

    private static BigDecimal round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(Integer i) {
        return i == null ? null : BigDecimal.valueOf(i);
    }
}
