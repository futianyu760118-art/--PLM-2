package com.hjgd.plm.rd.service;

import com.hjgd.plm.project.service.ProjectProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 完成判定与统计服务: 工作表/台账数据 -> 项目明细表节点(状态/计划日期/实际日期)。
 * 规则见 docs/project-node-worksheet-map.md「完成判定」/「统计规则」。
 *
 * 每条规则 SQL 统一返回三列:
 *   cnt        命中行数 (>0 视为该节点完成, status=DONE)
 *   plan_date  计划信息 (带入节点 plan_date)
 *   actual_date 完成信息 (带入节点 actual_date)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeRollupService {

    private final JdbcTemplate jdbc;
    private final ChangeLogService changeLogService;

    /** node_code -> 源查询(返回 cnt/plan_date/actual_date, 参数: project_no) */
    private static final Map<String, String> QUERIES = new HashMap<>();

    static {
        query("PLAN", "SELECT COUNT(*) AS cnt, NULL::date AS plan_date, " +
                "CASE WHEN COUNT(*)>0 AND COUNT(*) FILTER (WHERE status='已完成')=COUNT(*) THEN MAX(plan_end) END AS actual_date " +
                "FROM plm_plan_sheet WHERE project_no=?");
        query("SPEC", "SELECT COUNT(*) AS cnt, NULL::date AS plan_date, MAX(approved_at)::date AS actual_date " +
                "FROM plm_spec_sheet WHERE project_no=? AND status IN ('已批准','APPROVED')");
        query("CONFIG", "SELECT COUNT(*) AS cnt, NULL::date AS plan_date, MAX(updated_at)::date AS actual_date " +
                "FROM plm_config_sheet WHERE project_no=? AND status IN ('已签认','已批准','CONFIRMED')");
        review("MOLD_REVIEW", "开模");
        review("APPEARANCE", "外观");
        review("STRUCTURE", "结构");
        review("HAND_SAMPLE", "样机");
        review("ELECTRONICS", "电子");
        review("MOLD_SAMPLE", "样品");
        review("TECH_TRANSFER", "技转");
        trial("ELEC_TRIAL", "电子");
        trial("RD_TRIAL", "研发");
        trial("ENG_TRIAL", "工程");
        trial("PROD_TRIAL", "生产");
        query("TEST_REPORT", "SELECT COUNT(*) AS cnt, MAX(require_date) AS plan_date, MAX(test_date) AS actual_date " +
                "FROM plm_test_report WHERE project_no=? AND verdict='合格'");
        query("SHIPMENT", "SELECT COUNT(*) AS cnt, NULL::date AS plan_date, MAX(COALESCE(verify_date, ship_date)) AS actual_date " +
                "FROM plm_shipment WHERE project_no=? AND (verdict='合格' OR status IN ('已出货','已完成'))");
        query("REVIEW", "SELECT COUNT(*) AS cnt, NULL::date AS plan_date, MAX(created_at)::date AS actual_date " +
                "FROM plm_project_review WHERE project_no=?");
    }

    private static void query(String code, String sql) { QUERIES.put(code, sql); }

    private static void review(String code, String typeWord) {
        QUERIES.put(code, "SELECT COUNT(*) AS cnt, NULL::date AS plan_date, " +
                "MAX(COALESCE(complete_date, review_date)) AS actual_date " +
                "FROM plm_review_sheet WHERE project_no=? AND review_type LIKE '%" + typeWord + "%' AND conclusion='通过'");
    }

    private static void trial(String code, String typeWord) {
        QUERIES.put(code, "SELECT COUNT(*) AS cnt, NULL::date AS plan_date, MAX(trial_date) AS actual_date " +
                "FROM plm_trial_report WHERE project_no=? AND trial_type LIKE '%" + typeWord + "%' AND conclusion='通过'");
    }

    @Transactional
    public Map<String, Object> rollupProject(Long projectId) {
        Map<String, Object> p = jdbc.queryForMap(
                "SELECT id, project_no, part_no FROM plm_project WHERE id=?", projectId);
        String projectNo = (String) p.get("project_no");
        String partNo = (String) p.get("part_no");

        int updated = 0;
        int planned = 0;
        for (String code : ProjectProgressService.TRACKING_ORDER) {
            boolean matched = false;
            LocalDate planDate = null;
            LocalDate actualDate = null;

            if ("BOM".equals(code)) {
                matched = StringUtils.hasText(partNo) && !jdbc.queryForList(
                        "SELECT 1 FROM plm_bom WHERE root_part_no=? AND status='RELEASED' LIMIT 1", partNo).isEmpty();
            } else if (QUERIES.containsKey(code) && StringUtils.hasText(projectNo)) {
                List<Map<String, Object>> rows = jdbc.queryForList(QUERIES.get(code), projectNo);
                if (!rows.isEmpty()) {
                    Map<String, Object> r = rows.get(0);
                    long cnt = r.get("cnt") == null ? 0 : ((Number) r.get("cnt")).longValue();
                    matched = cnt > 0;
                    planDate = toLocalDate(r.get("plan_date"));
                    actualDate = toLocalDate(r.get("actual_date"));
                }
            }

            // 计划信息带入 (来源工作表有计划日期时刷新)
            if (planDate != null) {
                int n = jdbc.update("UPDATE plm_project_node SET plan_date=?, source_system='ROLLUP', " +
                                "sync_status='SYNCED', updated_at=NOW() " +
                                "WHERE project_id=? AND node_code=? AND (plan_date IS NULL OR plan_date<>?)",
                        planDate, projectId, code, planDate);
                if (n > 0) planned += n;
            }

            // 完成信息带入
            if (actualDate != null) {
                jdbc.update("UPDATE plm_project_node SET actual_date=?, source_system='ROLLUP', " +
                                "sync_status='SYNCED', updated_at=NOW() " +
                                "WHERE project_id=? AND node_code=? AND (actual_date IS NULL OR actual_date<>?)",
                        actualDate, projectId, code, actualDate);
            }
            if (matched) {
                int n = jdbc.update("UPDATE plm_project_node SET status='DONE', " +
                                "actual_date=COALESCE(actual_date, ?, CURRENT_DATE), source_system='ROLLUP', " +
                                "sync_status='SYNCED', updated_at=NOW() " +
                                "WHERE project_id=? AND node_code=? AND status<>'DONE'",
                        actualDate, projectId, code);
                if (n > 0) {
                    updated += n;
                    changeLogService.record(projectId, projectNo, "PROJECT_NODE", null,
                            code, "ROLLUP", "status", null, "DONE", "ROLLUP");
                }
            }
        }
        syncPlanDates(projectId, projectNo);
        Map<String, Object> out = new HashMap<>();
        out.put("projectId", projectId);
        out.put("projectNo", projectNo);
        out.put("updated", updated);
        out.put("planned", planned);
        return out;
    }

    /** 计划表(甘特) 计划完成日期 -> 按工作项目关键字匹配的节点 plan_date。 */
    private void syncPlanDates(Long projectId, String projectNo) {
        if (!StringUtils.hasText(projectNo)) return;
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(
                    "SELECT work_item, form_name, plan_end FROM plm_plan_sheet WHERE project_no=? ORDER BY seq", projectNo);
        } catch (Exception e) {
            return;
        }
        for (Map<String, Object> row : rows) {
            String text = str(row.get("work_item")) + " " + str(row.get("form_name"));
            LocalDate planEnd = toLocalDate(row.get("plan_end"));
            if (planEnd == null) continue;
            for (String code : matchPlanNodes(text)) {
                int n = jdbc.update("UPDATE plm_project_node SET plan_date=?, source_system='ROLLUP', " +
                                "sync_status='SYNCED', updated_at=NOW() " +
                                "WHERE project_id=? AND node_code=? AND (plan_date IS NULL OR plan_date<>?)",
                        planEnd, projectId, code, planEnd);
                if (n > 0) log.debug("[计划同步] 项目 {} 节点 {} 计划完成 {}", projectNo, code, planEnd);
            }
        }
    }

    /** 计划表工作项目/表单关键字 -> 节点编码 */
    private static final String[][] PLAN_KEYWORDS = {
            {"开模", "MOLD_REVIEW"},
            {"立项", "PLAN"}, {"启动", "PLAN"},
            {"外观", "APPEARANCE"},
            {"结构", "STRUCTURE"},
            {"电子", "ELECTRONICS"},
            {"手板", "HAND_SAMPLE"}, {"手样", "HAND_SAMPLE"}, {"样机", "HAND_SAMPLE"},
            {"测试", "TEST_REPORT"},
            {"包装", "PACKAGING"},
            {"模具样", "MOLD_SAMPLE"}, {"模样", "MOLD_SAMPLE"}, {"样品", "MOLD_SAMPLE"},
            {"模具", "MOLD"},
            {"工艺", "TECH_TRANSFER"}, {"技转", "TECH_TRANSFER"},
            {"物料清单", "BOM"}, {"BOM", "BOM"},
            {"规格", "SPEC"},
            {"配置", "CONFIG"},
            {"出货", "SHIPMENT"},
            {"复盘", "REVIEW"},
    };

    private static java.util.Set<String> matchPlanNodes(String text) {
        java.util.Set<String> codes = new java.util.LinkedHashSet<>();
        if (!StringUtils.hasText(text)) return codes;
        String t = text.toUpperCase();
        for (String[] k : PLAN_KEYWORDS) {
            if (t.contains(k[0].toUpperCase())) codes.add(k[1]);
        }
        return codes;
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }

    /** 依据项目编号自动同步引用(工作表变更后调用) */
    @Transactional
    public Map<String, Object> rollupByProjectNo(String projectNo) {
        Map<String, Object> out = new HashMap<>();
        if (!StringUtils.hasText(projectNo)) { out.put("updated", 0); return out; }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id FROM plm_project WHERE project_no=?", projectNo);
        int updated = 0;
        for (Map<String, Object> r : rows) {
            updated += (int) rollupProject(((Number) r.get("id")).longValue()).get("updated");
        }
        out.put("updated", updated);
        out.put("projectNo", projectNo);
        return out;
    }

    @Transactional
    public Map<String, Object> rollupAll() {
        List<Map<String, Object>> projects = jdbc.queryForList("SELECT id FROM plm_project");
        int updated = 0;
        int projectsTouched = 0;
        for (Map<String, Object> pr : projects) {
            Map<String, Object> r = rollupProject(((Number) pr.get("id")).longValue());
            int u = (int) r.get("updated");
            if (u > 0) { updated += u; projectsTouched++; }
        }
        Map<String, Object> out = new HashMap<>();
        out.put("projects", projects.size());
        out.put("projectsTouched", projectsTouched);
        out.put("updated", updated);
        return out;
    }

    private LocalDate toLocalDate(Object o) {
        if (o == null) return null;
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        if (o instanceof LocalDate d) return d;
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime().toLocalDate();
        if (o instanceof java.util.Date d) return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        try { return LocalDate.parse(o.toString().substring(0, 10)); } catch (Exception e) { return null; }
    }
}
