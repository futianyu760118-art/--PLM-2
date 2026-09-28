package com.hjgd.plm.rd;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目工作表定义 (模板工作表落地)。见 docs/project-node-worksheet-map.md。
 */
public final class RdSheetDefs {

    public record Col(String key, String label, String type, String options) {}

    public record Def(String type, String table, String label, List<Col> cols) {}

    public static final Map<String, Def> DEFS = new LinkedHashMap<>();

    private static Col c(String key, String label) { return new Col(key, label, "text", null); }
    private static Col c(String key, String label, String type) { return new Col(key, label, type, null); }
    private static Col c(String key, String label, String type, String options) { return new Col(key, label, type, options); }

    static {
        DEFS.put("plan", new Def("plan", "plm_plan_sheet", "计划表(甘特)", List.of(
                c("project_no", "项目编号"), c("stage", "阶段"), c("seq", "序号", "number"),
                c("work_item", "工作项目"), c("month", "月份"),
                c("plan_start", "计划开始", "date"), c("plan_end", "计划完成", "date"),
                c("owner", "责任人"), c("confirmer", "确认人"), c("form_name", "表单"),
                c("status", "完成情况", "select", "计划,已完成,延期"),
                c("remark", "说明", "textarea"))));

        DEFS.put("basebom", new Def("basebom", "plm_base_bom", "基础BOM(5)", List.of(
                c("project_no", "项目编号"), c("seq", "顺序号", "number"), c("material_code", "物料代码"),
                c("position_no", "位置号"), c("ref_no", "位号"), c("material_name", "物料名称"),
                c("spec", "规格型号"), c("aux_attr", "辅助属性"), c("material_attr", "物料属性"),
                c("quantity", "用量", "number"), c("unit", "单位"), c("sub_type", "子项类型"),
                c("supplier", "供应商"), c("key_part", "关键件", "select", "是,否"),
                c("use_status", "使用状态", "select", "在用,停用"), c("remark", "备注", "textarea"))));

        DEFS.put("spec", new Def("spec", "plm_spec_sheet", "规格书登记", List.of(
                c("project_no", "项目编号"), c("model", "产品型号"), c("product_name", "产品名称"),
                c("description", "描述"), c("version", "版本号"), c("file_no", "文件编号"),
                c("light_source", "光源"), c("power", "功率"), c("input_voltage", "输入电压"),
                c("power_efficiency", "电源效率"), c("beam_angle", "光束角"),
                c("luminous_flux", "光通量"), c("cct", "色温"), c("ra", "显指"), c("ta", "环境温度"),
                c("life_time", "寿命"), c("ip_rating", "防水等级"),
                c("shell_material", "壳体材质"), c("reflector_material", "反光罩材质"),
                c("battery_capacity", "电池容量"), c("discharge_time", "放电时间"), c("charging_time", "充电时间"),
                c("switch_type", "开关类型"), c("cable_spec", "线规"),
                c("dimension", "尺寸"), c("net_weight", "净重"),
                c("inbox_size", "内箱尺寸"), c("carton_size", "外箱尺寸"), c("gw_nw", "毛重/净重"),
                c("status", "状态", "select", "编制中,审核中,已批准,作废"),
                c("approved_by", "批准人"), c("approved_at", "批准日期", "date"), c("remark", "备注", "textarea"))));

        DEFS.put("config", new Def("config", "plm_config_sheet", "配置表", List.of(
                c("project_no", "项目编号"), c("model", "产品型号"),
                c("structure_shell", "壳体材质"), c("structure_reflector", "反光罩材质"), c("structure_bracket", "支架"),
                c("structure_handle", "手杆"), c("structure_waterproof", "防水等级"), c("structure_cable", "线规"),
                c("elec_luminous", "光参数LM"), c("elec_efficiency", "光效"), c("elec_color_temp", "色温"),
                c("elec_ra", "显指"), c("elec_rated_power", "标称功率"), c("elec_chip", "芯片方案"),
                c("elec_battery", "电池"),
                c("pack_inner", "内包"), c("pack_outer", "外包"), c("pack_transport", "运输要求"),
                c("certificate_required", "认证需求"), c("special_env", "环保要求"), c("special_uv", "UV测试"),
                c("special_salt", "盐雾测试"),
                c("compensated_flux", "补偿光通量"), c("discharge_time", "放电时间"), c("charging_time", "充电时间"),
                c("status", "状态", "select", "编制中,已签认,已批准,作废"), c("remark", "备注", "textarea"))));

        DEFS.put("sample", new Def("sample", "plm_sample", "样品单", List.of(
                c("sample_no", "样品编号"), c("project_no", "项目编号"), c("sample_name", "样品名称"),
                c("spec", "规格"), c("quantity", "数量", "number"), c("purpose", "用途"),
                c("require_date", "需求日期", "date"),
                c("status", "状态", "select", "待寄送,已寄送,已确认,完成"),
                c("send_no", "寄送单号"), c("owner", "负责人"), c("remark", "备注", "textarea"))));

        DEFS.put("review", new Def("review", "plm_review_sheet", "评审单", List.of(
                c("review_no", "评审编号"), c("project_no", "项目编号"),
                c("review_type", "评审类型", "select", "开模评审,结构评审,外观评审,样机评审,样品评审,技转评审,电子评审"),
                c("review_date", "评审日期", "date"), c("host", "主持人"), c("participants", "参与人"),
                c("conclusion", "评审结论", "select", "通过,不通过"),
                c("issues", "问题与整改", "textarea"), c("tracker", "跟踪人"), c("complete_date", "完成日期", "date"),
                c("remark", "备注", "textarea"))));

        DEFS.put("test", new Def("test", "plm_test_report", "送检/测试报告", List.of(
                c("report_no", "报告编号"), c("send_no", "送检编号"), c("project_no", "项目编号"),
                c("test_type", "测试类型", "select", "电性能,温升,传导辐射,安规,防水,其他"),
                c("test_item", "测试项目"), c("standard_req", "标准要求"), c("actual_value", "实测值"),
                c("verdict", "判定", "select", "合格,不合格"),
                c("test_date", "测试日期", "date"), c("require_date", "要求完成日期", "date"),
                c("lab", "实验室"), c("tester", "测试人"),
                c("status", "状态", "select", "待检,检测中,已出具"),
                c("remark", "备注", "textarea"))));

        DEFS.put("trial", new Def("trial", "plm_trial_report", "试产报告", List.of(
                c("report_no", "报告编号"), c("project_no", "项目编号"),
                c("trial_type", "试产类型", "select", "电子试产,研发试产,工程试产,生产试产"),
                c("trial_batch", "试产批次"), c("trial_qty", "试产数量", "number"), c("good_qty", "良品数", "number"),
                c("defect_rate", "不良率%"), c("main_issues", "主要问题", "textarea"),
                c("conclusion", "结论", "select", "通过,不通过"),
                c("trial_date", "试产日期", "date"), c("owner", "负责人"), c("remark", "备注", "textarea"))));

        DEFS.put("shipment", new Def("shipment", "plm_shipment", "出货/验货对比", List.of(
                c("order_no", "订单编号"), c("project_no", "项目编号"), c("customer", "客户"),
                c("ship_date", "出货日期", "date"), c("verify_date", "验货日期", "date"),
                c("verify_item", "验货项目"), c("standard_req", "标准要求"), c("actual_result", "实际结果"),
                c("verdict", "判定", "select", "合格,不合格"),
                c("status", "状态", "select", "待出货,已出货,已完成"),
                c("rectify_req", "整改要求", "textarea"), c("remark", "备注", "textarea"))));
    }

    public static Def get(String type) { return DEFS.get(type); }
}
