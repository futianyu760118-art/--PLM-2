package com.hjgd.plm.project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阶段门自动进阶：按项目 22 节点进度，满足当前门全部节点完成后自动推进到下一门。
 * 门 -> 所需完成节点(全部 DONE 才进阶) 的映射，与研发项目跟踪/项目明细表口径一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GateAdvanceService {

    private final JdbcTemplate jdbc;

    private static final String[] GATES = {"G0", "G1", "G2", "G3", "G4", "G5", "G6", "G7", "G8"};
    private static final Map<String, String> GATE_NAMES = new LinkedHashMap<>();
    static {
        GATE_NAMES.put("G0", "概念立项");
        GATE_NAMES.put("G1", "方案评审");
        GATE_NAMES.put("G2", "结构冻结");
        GATE_NAMES.put("G3", "开模");
        GATE_NAMES.put("G4", "T0/T1试模");
        GATE_NAMES.put("G5", "T2/工试");
        GATE_NAMES.put("G6", "技转放行");
        GATE_NAMES.put("G7", "量产");
        GATE_NAMES.put("G8", "关闭");
    }

    /** 每个门进阶所需的节点(全部 DONE) */
    private static final Map<String, String[]> GATE_REQUIRE = new HashMap<>();
    static {
        GATE_REQUIRE.put("G0", new String[]{"PLAN"});
        GATE_REQUIRE.put("G1", new String[]{"SPEC", "CONFIG", "MOLD_REVIEW"});
        GATE_REQUIRE.put("G2", new String[]{"APPEARANCE", "STRUCTURE", "ELECTRONICS", "MOLD_DRAWING"});
        GATE_REQUIRE.put("G3", new String[]{"MOLD"});
        GATE_REQUIRE.put("G4", new String[]{"MOLD_SAMPLE"});
        GATE_REQUIRE.put("G5", new String[]{"ELEC_TRIAL", "RD_TRIAL", "ENG_TRIAL"});
        GATE_REQUIRE.put("G6", new String[]{"TECH_TRANSFER", "PACKAGING", "TEST_REPORT", "PROD_TRIAL"});
        GATE_REQUIRE.put("G7", new String[]{"SHIPMENT"});
        GATE_REQUIRE.put("G8", new String[]{"REVIEW"});
    }

    /**
     * 按项目节点进度自动进阶阶段门（幂等：满足才推进，且只推进一格）。
     * 返回: 本次进阶后的门(若推进) 或 null(未推进)。
     */
    @Transactional
    public String autoAdvance(Long projectId) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("SELECT id, project_no, current_gate, status FROM plm_project WHERE id=?", projectId);
        } catch (Exception e) {
            return null;
        }
        if (rows.isEmpty()) return null;
        Map<String, Object> p = rows.get(0);
        String projectNo = (String) p.get("project_no");
        String gate = (String) p.get("current_gate");
        if (gate == null) gate = "G0";

        int idx = indexOf(gate);
        if (idx < 0 || idx >= GATES.length) return null; // 已是最后(G8)或未知

        String[] required = GATE_REQUIRE.get(gate);
        if (required == null) return null;
        if (!allDone(projectNo, required)) return null;

        String next = GATES[idx + 1];
        jdbc.update("UPDATE plm_project SET current_gate=?, gate_status='ON_TRACK', updated_at=NOW() WHERE id=?", next, projectId);

        // G8 完成 -> 关闭项目
        if ("G8".equals(next)) {
            jdbc.update("UPDATE plm_project SET status='CLOSED', close_date=CURRENT_DATE, updated_at=NOW() WHERE id=?", projectId);
        }

        // 记录阶段门日志(自动)
        try {
            jdbc.update("INSERT INTO plm_project_gate_log(project_id, gate_code, gate_name, result, actual_date, operator, comment, created_at) " +
                            "VALUES (?,?,?,?,?,?,?,NOW())",
                    projectId, next, GATE_NAMES.getOrDefault(next, next), "PASS",
                    java.sql.Date.valueOf(LocalDate.now()), "自动", "节点进度达成，自动进阶");
        } catch (Exception e) {
            log.warn("[阶段门] 自动进阶日志记录失败 project={} gate={}: {}", projectNo, next, e.getMessage());
        }
        log.info("[阶段门] 项目 {} 由 {} 自动进阶至 {}", projectNo, gate, next);
        return next;
    }

    private boolean allDone(String projectNo, String[] nodeCodes) {
        String in = String.join(",", java.util.Collections.nCopies(nodeCodes.length, "?"));
        Long done = jdbc.queryForObject(
                "SELECT COUNT(*) FROM plm_project_node WHERE project_no=? AND node_code IN (" + in + ") AND status='DONE'",
                Long.class, (Object[]) prepend(projectNo, nodeCodes));
        return done != null && done == nodeCodes.length;
    }

    private Object[] prepend(String first, String[] arr) {
        Object[] out = new Object[arr.length + 1];
        out[0] = first;
        System.arraycopy(arr, 0, out, 1, arr.length);
        return out;
    }

    private int indexOf(String g) {
        for (int i = 0; i < GATES.length; i++) if (GATES[i].equals(g)) return i;
        return -1;
    }
}
