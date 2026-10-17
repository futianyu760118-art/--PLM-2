package com.hjgd.plm.lifecycle.controller;

import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 全生命周期(12环节)真实数据聚合（只读）。
 * 依据 AEOS_PLM_Frontend_Execution_V1 资料包：只统计现有库内事实，
 * 不虚构执行/审批状态；执行类流程仍以后端模块为准。
 */
@Tag(name = "V1 生命周期数据")
@RestController
@RequestMapping("/v1/lifecycle")
@RequiredArgsConstructor
public class LifecycleOverviewController {

    private final JdbcTemplate jdbc;

    /** 优先按逻辑删除过滤统计，表无 deleted 列时回退全量 */
    private long count(String sqlWithDeleted, String sqlPlain) {
        try {
            Long v = jdbc.queryForObject(sqlWithDeleted, Long.class);
            return v == null ? 0 : v;
        } catch (Exception ignore) {
            try {
                Long v = jdbc.queryForObject(sqlPlain, Long.class);
                return v == null ? 0 : v;
            } catch (Exception ignore2) {
                return 0;
            }
        }
    }

    @Operation(summary = "12环节总览(各环节真实计数; mine=true 时项目类口径只统计本人)")
    @GetMapping("/overview")
    public Result<List<Map<String, Object>>> overview(@RequestParam(required = false, defaultValue = "false") boolean mine) {
        // 非管理员强制只看本人（登录者只显示自己拥有的相关内容）
        boolean onlyMine = mine || com.hjgd.plm.common.DataScope.scope() == com.hjgd.plm.common.DataScope.SCOPE_SELF;
        String me = onlyMine ? safeName() : null;

        List<Map<String, Object>> stages = new ArrayList<>();

        // 1 需求与立项（mine: 只统计本人申请/负责）
        stages.add(stage("需求与立项", "需求、立项、目标约束", "/project/initiation", items(
                item("立项申请", cnt("SELECT COUNT(*) FROM plm_project_initiation t WHERE " + iOwner("t.applicant", "t.owner", me), me), "条", "/project/initiation"),
                item("已批准", cnt("SELECT COUNT(*) FROM plm_project_initiation t WHERE approval_status='approved' AND " + iOwner("t.applicant", "t.owner", me), me), "条", "/project/initiation"),
                item("已转项目", cnt("SELECT COUNT(*) FROM plm_project_initiation t WHERE project_id IS NOT NULL AND " + iOwner("t.applicant", "t.owner", me), me), "个", "/project/list"))));

        // 2 方案与规格（mine: 只统计本人项目的工作表）
        stages.add(stage("方案与规格", "规格、配置、方案评审", "/project/worksheets", items(
                item("规格书", cnt("SELECT COUNT(*) FROM plm_spec_sheet s WHERE " + iSheet("s.project_no", me), me), "份", "/project/worksheets"),
                item("规格书已批准", count0("SELECT COUNT(*) FROM plm_spec_sheet WHERE status IN ('已批准','APPROVED')"), "份", "/project/worksheets"),
                item("配置表已签认", count0("SELECT COUNT(*) FROM plm_config_sheet WHERE status IN ('已签认','已批准','CONFIRMED')"), "份", "/project/worksheets"))));

        // 3 详细设计
        stages.add(stage("详细设计", "图纸、物料、EBOM、模具", "/material/list", items(
                item("物料", count0("SELECT COUNT(*) FROM plm_material"), "个", "/material/list"),
                item("BOM单", count0("SELECT COUNT(*) FROM plm_bom"), "个", "/bom/list"),
                item("模具台账", count0("SELECT COUNT(*) FROM plm_mold"), "套", "/mold/list"))));

        // 4 样机与验证（mine: 只统计本人项目证据）
        stages.add(stage("样机与验证", "样品、试验条件与结果(不自动判定合格)", "/project/evidence", items(
                item("样品单", cnt("SELECT COUNT(*) FROM plm_sample s WHERE " + iSheet("s.project_no", me), me), "单", "/project/worksheets"),
                item("测试报告", cnt("SELECT COUNT(*) FROM plm_test_report s WHERE " + iSheet("s.project_no", me), me), "份", "/project/worksheets"),
                item("证据记录", cnt("SELECT COUNT(*) FROM plm_project_node_evidence e WHERE " + iEv(e -> e ? "1=1" : "e.project_no IN (SELECT project_no FROM plm_project WHERE owner='" + esc(me) + "')", me), me), "条", "/project/evidence"))));

        // 5 试产与评审
        stages.add(stage("试产与评审", "试产、工艺、检验、遗留项", "/project/worksheets", items(
                item("试产报告", cnt("SELECT COUNT(*) FROM plm_trial_report s WHERE " + iSheet("s.project_no", me), me), "份", "/project/worksheets"),
                item("评审通过", count0("SELECT COUNT(*) FROM plm_review_sheet WHERE conclusion='通过'"), "次", "/project/worksheets"),
                item("工序路线", count0("SELECT COUNT(*) FROM plm_process_route"), "条", "/process/list"))));

        // 6 量产移交（mine: 只统计本人项目）
        stages.add(stage("量产移交", "发布包、接收记录(发布与接收口径待联调)", "/ecn/list", items(
                item("ECN已生效", count0("SELECT COUNT(*) FROM plm_ecn WHERE status='EFFECTIVE'"), "单", "/ecn/list"),
                item("已量产项目", cnt("SELECT COUNT(*) FROM plm_project WHERE status='MP' AND " + pOwner(me), me), "个", "/project/list"))));

        // 7 上市与在产维护
        stages.add(stage("上市与在产维护", "在产版本、生产与销售反馈(数据只读聚合)", "/project/list", items(
                item("在产项目", cnt("SELECT COUNT(*) FROM plm_project WHERE status='MP' AND " + pOwner(me), me), "个", "/project/list"),
                item("出货/验货记录", cnt("SELECT COUNT(*) FROM plm_shipment s WHERE " + iSheet("s.project_no", me), me), "条", "/project/worksheets"),
                item("销售推广", cnt("SELECT COUNT(*) FROM plm_project_sales_promotion s WHERE " + iSheet("s.project_no", me), me), "条", "/project/sales"))));

        // 8 售后与质量反馈（mine: 只统计本人提交的反馈）
        stages.add(stage("售后与质量反馈", "反馈、失效分析、纠正措施", "/admin/feedback", items(
                item("反馈-待处理", cnt("SELECT COUNT(*) FROM plm_feedback WHERE status='pending' AND " + fSub(me), me), "条", "/admin/feedback"),
                item("反馈-已解决", cnt("SELECT COUNT(*) FROM plm_feedback WHERE status='resolved' AND " + fSub(me), me), "条", "/admin/feedback"),
                item("供应链品质异常", cnt("SELECT COUNT(*) FROM plm_project_supply_issue s WHERE " + iSheet("s.project_no", me), me), "条", "/project/supply"))));

        // 9 变更与迭代
        stages.add(stage("变更与迭代", "ECN、影响、验证、生效版本(跨阶段循环)", "/ecn/list", items(
                item("ECN总数", count0("SELECT COUNT(*) FROM plm_ecn"), "单", "/ecn/list"),
                item("ECN已生效", count0("SELECT COUNT(*) FROM plm_ecn WHERE status='EFFECTIVE'"), "单", "/ecn/list"),
                item("变更留痕", count0("SELECT COUNT(*) FROM plm_project_change_log"), "条", "/project/changes"))));

        // 10 停产评估与批准
        stages.add(stage("停产评估与批准", "订单/库存/供应/模具/服务评估(执行与审批待确认)", "/project/list", items(
                item("暂停项目", cnt("SELECT COUNT(*) FROM plm_project WHERE status='ON_HOLD' AND " + pOwner(me), me), "个", "/project/list"))));

        // 11 产品退市
        stages.add(stage("产品退市", "渠道退出、库存处置、服务安排(执行流程待接入)", "/project/list", items(
                item("已关闭项目", cnt("SELECT COUNT(*) FROM plm_project WHERE status='CLOSED' AND " + pOwner(me), me), "个", "/project/list"),
                item("已取消项目", cnt("SELECT COUNT(*) FROM plm_project WHERE status='CANCELLED' AND " + pOwner(me), me), "个", "/project/list"))));

        // 12 归档与持续义务
        stages.add(stage("归档与持续义务", "归档、追溯、备件服务、经营复盘(不因退市删除记录)", "/archive/list", items(
                item("档案文件", count0("SELECT COUNT(*) FROM plm_archive_file"), "份", "/archive/list"),
                item("项目复盘", cnt("SELECT COUNT(*) FROM plm_project_review s WHERE " + iSheet("s.project_no", me), me), "次", "/project/review"))));

        return Result.success(stages);
    }

    // ===== mine 过滤片段构造 =====
    private String safeName() {
        try { return SecurityUtils.getCurrentRealName(); } catch (Exception e) { return ""; }
    }
    private static String esc(String s) { return s == null ? "" : s.replace("'", "''"); }
    /** 项目表 owner 条件 */
    private String pOwner(String me) { return me == null ? "1=1" : "owner='" + esc(me) + "'"; }
    /** 立项表 applicant/owner 条件 */
    private String iOwner(String applicantCol, String ownerCol, String me) {
        return me == null ? "1=1" : "(" + applicantCol + "='" + esc(me) + "' OR " + ownerCol + "='" + esc(me) + "')";
    }
    /** 工作表按本人项目编号集条件 */
    private String iSheet(String noCol, String me) {
        return me == null ? "1=1" : noCol + " IN (SELECT project_no FROM plm_project WHERE owner='" + esc(me) + "')";
    }
    /** 反馈按提交人条件（按真实姓名冗余字段 submitter_name 或 submitter_id 不可用时的口径） */
    private String fSub(String me) {
        if (me == null) return "1=1";
        try {
            Long uid = SecurityUtils.getCurrentUserId();
            return "submitter_id=" + uid;
        } catch (Exception e) {
            return "submitter_name='" + esc(me) + "'";
        }
    }
    private interface EvCond { String apply(boolean global); }
    private String iEv(EvCond c, String me) { return c.apply(me == null); }

    /** mine 过滤计数（无过滤 SQL 回退 0 之外的安全执行） */
    private long cnt(String sql, String me) {
        if (me == null) {
            // 非过滤场景不应走到这，兜底执行
        }
        try {
            Long v = jdbc.queryForObject(sql, Long.class);
            return v == null ? 0 : v;
        } catch (Exception e) {
            return 0;
        }
    }
    private long count0(String sql) {
        try {
            Long v = jdbc.queryForObject(sql, Long.class);
            return v == null ? 0 : v;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 产品工作空间数据卡：某项目的真实聚合(工作表9类/ECN/证据/阶段门/立项) */
    @Operation(summary = "产品空间数据卡(按项目)")
    @GetMapping("/project-card")
    public Result<Map<String, Object>> projectCard(@RequestParam Long projectId) {
        Map<String, Object> p;
        try {
            // plm_project 无逻辑删除列, 直接按 id 查询
            p = jdbc.queryForMap("SELECT id, project_no, part_no, project_name, status, current_gate FROM plm_project WHERE id=?", projectId);
        } catch (Exception e) {
            return Result.failed(404, "项目不存在");
        }
        String projectNo = (String) p.get("project_no");
        String partNo = (String) p.get("part_no");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("project", p);

        Map<String, Long> sheets = new LinkedHashMap<>();
        sheets.put("plan", count("SELECT COUNT(*) FROM plm_plan_sheet WHERE project_no='" + esc(projectNo) + "'", "SELECT COUNT(*) FROM plm_plan_sheet WHERE project_no='" + esc(projectNo) + "'"));
        sheets.put("basebom", count("SELECT COUNT(*) FROM plm_base_bom WHERE project_no='" + esc(projectNo) + "'", "SELECT COUNT(*) FROM plm_base_bom WHERE project_no='" + esc(projectNo) + "'"));
        sheets.put("spec", count("SELECT COUNT(*) FROM plm_spec_sheet WHERE project_no='" + esc(projectNo) + "'", "SELECT COUNT(*) FROM plm_spec_sheet WHERE project_no='" + esc(projectNo) + "'"));
        sheets.put("config", count("SELECT COUNT(*) FROM plm_config_sheet WHERE project_no='" + esc(projectNo) + "'", "SELECT COUNT(*) FROM plm_config_sheet WHERE project_no='" + esc(projectNo) + "'"));
        sheets.put("sample", count("SELECT COUNT(*) FROM plm_sample WHERE project_no='" + esc(projectNo) + "'", "SELECT COUNT(*) FROM plm_sample WHERE project_no='" + esc(projectNo) + "'"));
        sheets.put("review", count("SELECT COUNT(*) FROM plm_review_sheet WHERE project_no='" + esc(projectNo) + "'", "SELECT COUNT(*) FROM plm_review_sheet WHERE project_no='" + esc(projectNo) + "'"));
        sheets.put("test", count("SELECT COUNT(*) FROM plm_test_report WHERE project_no='" + esc(projectNo) + "'", "SELECT COUNT(*) FROM plm_test_report WHERE project_no='" + esc(projectNo) + "'"));
        sheets.put("trial", count("SELECT COUNT(*) FROM plm_trial_report WHERE project_no='" + esc(projectNo) + "'", "SELECT COUNT(*) FROM plm_trial_report WHERE project_no='" + esc(projectNo) + "'"));
        sheets.put("shipment", count("SELECT COUNT(*) FROM plm_shipment WHERE project_no='" + esc(projectNo) + "'", "SELECT COUNT(*) FROM plm_shipment WHERE project_no='" + esc(projectNo) + "'"));
        out.put("sheets", sheets);

        out.put("evidenceCount", count("SELECT COUNT(*) FROM plm_project_node_evidence WHERE project_id=" + projectId, "SELECT COUNT(*) FROM plm_project_node_evidence WHERE project_id=" + projectId));
        if (partNo != null && !partNo.isEmpty()) {
            out.put("ecnCount", count("SELECT COUNT(*) FROM plm_ecn WHERE part_no='" + esc(partNo) + "' AND COALESCE(deleted,0)=0", "SELECT COUNT(*) FROM plm_ecn WHERE part_no='" + esc(partNo) + "'"));
        } else {
            out.put("ecnCount", 0L);
        }
        out.put("initiation", jdbc.queryForList("SELECT init_no, project_name, approval_status FROM plm_project_initiation WHERE project_id=" + projectId + " LIMIT 1"));
        out.put("gates", jdbc.queryForList("SELECT gate_code, gate_name, result, actual_date, operator FROM plm_project_gate_log WHERE project_id=" + projectId + " ORDER BY id DESC LIMIT 5"));
        out.put("nodes", jdbc.queryForMap("SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE status='DONE') AS done FROM plm_project_node WHERE project_id=?", projectId));
        return Result.success(out);
    }

    private Map<String, Object> stage(String name, String scope, String link, List<Map<String, Object>> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name); m.put("scope", scope); m.put("link", link); m.put("items", items);
        return m;
    }
    private List<Map<String, Object>> items(Map<String, Object>... is) { return new ArrayList<>(List.of(is)); }
    private Map<String, Object> item(String label, long value, String unit, String link) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label); m.put("value", value); m.put("unit", unit); m.put("link", link);
        return m;
    }
}
