package com.hjgd.plm.rd.service;

import com.hjgd.plm.project.service.ProjectProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 完成判定与统计服务: 工作表/台账数据 -> 项目明细表节点状态。
 * 规则见 docs/project-node-worksheet-map.md「完成判定」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeRollupService {

    private final JdbcTemplate jdbc;
    private final ChangeLogService changeLogService;

    /** node_code -> 判定 SQL (参数: project_no) */
    private static final Map<String, String> RULES = new HashMap<>();

    static {
        rule("SPEC", "SELECT 1 FROM plm_spec_sheet WHERE project_no=? AND status IN ('已批准','APPROVED') LIMIT 1");
        rule("CONFIG", "SELECT 1 FROM plm_config_sheet WHERE project_no=? AND status IN ('已签认','已批准','CONFIRMED') LIMIT 1");
        review("MOLD_REVIEW", "开模");
        review("APPEARANCE", "外观");
        review("STRUCTURE", "结构");
        review("HAND_SAMPLE", "样机");
        review("ELECTRONICS", "电子");
        review("MOLD_SAMPLE", "样品");
        review("TECH_TRANSFER", "技转");
        rule("ELEC_TRIAL", "SELECT 1 FROM plm_trial_report WHERE project_no=? AND trial_type LIKE '%电子%' AND conclusion='通过' LIMIT 1");
        rule("RD_TRIAL", "SELECT 1 FROM plm_trial_report WHERE project_no=? AND trial_type LIKE '%研发%' AND conclusion='通过' LIMIT 1");
        rule("ENG_TRIAL", "SELECT 1 FROM plm_trial_report WHERE project_no=? AND trial_type LIKE '%工程%' AND conclusion='通过' LIMIT 1");
        rule("PROD_TRIAL", "SELECT 1 FROM plm_trial_report WHERE project_no=? AND trial_type LIKE '%生产%' AND conclusion='通过' LIMIT 1");
        rule("TEST_REPORT", "SELECT 1 FROM plm_test_report WHERE project_no=? AND verdict='合格' LIMIT 1");
        rule("SHIPMENT", "SELECT 1 FROM plm_shipment WHERE project_no=? AND (verdict='合格' OR status IN ('已出货','已完成')) LIMIT 1");
        rule("REVIEW", "SELECT 1 FROM plm_project_review WHERE project_no=? LIMIT 1");
    }

    private static void rule(String code, String sql) { RULES.put(code, sql); }
    private static void review(String code, String typeWord) {
        RULES.put(code, "SELECT 1 FROM plm_review_sheet WHERE project_no=? AND review_type LIKE '%" + typeWord
                + "%' AND conclusion='通过' LIMIT 1");
    }

    @Transactional
    public Map<String, Object> rollupProject(Long projectId) {
        Map<String, Object> p = jdbc.queryForMap(
                "SELECT id, project_no, part_no FROM plm_project WHERE id=?", projectId);
        String projectNo = (String) p.get("project_no");
        String partNo = (String) p.get("part_no");

        Map<String, Integer> byNode = new HashMap<>();
        int updated = 0;
        for (String code : ProjectProgressService.TRACKING_ORDER) {
            boolean matched = false;
            if ("BOM".equals(code)) {
                matched = StringUtils.hasText(partNo) && !jdbc.queryForList(
                        "SELECT 1 FROM plm_bom WHERE root_part_no=? AND status='RELEASED' LIMIT 1", partNo).isEmpty();
            } else if (RULES.containsKey(code) && StringUtils.hasText(projectNo)) {
                matched = !jdbc.queryForList(RULES.get(code), projectNo).isEmpty();
            }
            if (matched) {
                int n = jdbc.update("UPDATE plm_project_node SET status='DONE', " +
                                "actual_date=COALESCE(actual_date, CURRENT_DATE), source_system='ROLLUP', " +
                                "sync_status='SYNCED', updated_at=NOW() " +
                                "WHERE project_id=? AND node_code=? AND status<>'DONE'", projectId, code);
                if (n > 0) {
                    updated += n;
                    changeLogService.record(projectId, projectNo, "PROJECT_NODE", null,
                            code, "ROLLUP", "status", null, "DONE", "ROLLUP");
                }
            }
        }
        Map<String, Object> out = new HashMap<>();
        out.put("projectId", projectId);
        out.put("projectNo", projectNo);
        out.put("updated", updated);
        return out;
    }

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
}
