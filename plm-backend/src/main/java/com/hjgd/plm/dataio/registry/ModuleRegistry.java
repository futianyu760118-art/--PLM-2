package com.hjgd.plm.dataio.registry;

import com.hjgd.plm.dataio.model.ColumnDef;
import com.hjgd.plm.dataio.model.ModuleDef;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用导入/导出/自检 模块注册表。
 * 新增模块只需在下方追加一条 ModuleDef 定义。
 */
@Component
public class ModuleRegistry {

    private final Map<String, ModuleDef> modules = new LinkedHashMap<>();

    public ModuleRegistry() {
        register(ModuleDef.builder()
                .code("material").name("物料主数据").table("plm_material").keyField("part_no").logicDelete(true)
                .columns(List.of(
                        uniq("part_no", "料号"),
                        req("material_name", "物料名称"),
                        en("material_type", "物料类型", "FINISHED", "SEMI", "PLASTIC", "HARDWARE", "STANDARD"),
                        col("material_texture", "材质"), col("color", "颜色"), col("specification", "规格"),
                        col("product_series", "产品系列"), col("project_no", "项目号"),
                        col("supplier_code", "供应商编码"), col("supplier_name", "供应商名称"),
                        en("status", "状态", "DRAFT", "REVIEWING", "RELEASED", "IN_PRODUCTION", "CHANGING", "OBSOLETE", "SEALED"),
                        col("version_no", "版本号"), col("make_type", "制造类型"), col("unit", "单位"),
                        col("lifecycle_status", "生命周期状态"), col("phase", "阶段"), col("part_category", "物料分类"),
                        col("name_en", "英文名称"), col("product_type", "产品类型"), col("ip_rating", "防护等级"),
                        num("power_w", "功率(W)"), num("standard_cost", "标准成本"), col("cost_currency", "币种"),
                        col("drawing_no", "图纸号"), col("drawing_revision", "图纸版本"), col("remark", "备注")
                )).build());

        register(ModuleDef.builder()
                .code("bom").name("BOM产品结构").table("plm_bom").keyField("bom_no").logicDelete(true)
                .columns(List.of(
                        uniq("bom_no", "BOM编号"),
                        ref("root_part_no", "母件料号", "plm_material", "part_no", "物料"),
                        col("version_no", "版本号"),
                        en("status", "状态", "DRAFT", "RELEASED", "OBSOLETE"),
                        col("ecn_no", "关联ECN"), col("bom_type", "BOM类型"), col("source", "来源"), col("remark", "备注")
                )).build());

        register(ModuleDef.builder()
                .code("ecn").name("ECN工程变更").table("plm_ecn").keyField("ecn_no").logicDelete(true)
                .columns(List.of(
                        uniq("ecn_no", "ECN编号"),
                        ref("part_no", "料号", "plm_material", "part_no", "物料"),
                        col("material_name", "物料名称"),
                        en("change_type", "变更类型", "STRUCTURE", "MOLD", "PROCESS", "BOM", "DIMENSION"),
                        col("version_before", "变更前版本"), col("version_after", "变更后版本"),
                        col("change_location", "变更位置"), col("change_reason", "变更原因"),
                        num("mold_cost", "模具费用"), col("impact_scope", "影响范围"),
                        col("trial_impact", "试模影响"), col("mass_impact", "量产影响"),
                        en("status", "状态", "DRAFT", "PENDING_L1", "PENDING_L2", "APPROVED", "REJECTED", "EFFECTIVE", "VOID"),
                        col("applicant", "申请人"), date("apply_time", "申请时间"), col("final_comment", "最终意见")
                )).build());

        register(ModuleDef.builder()
                .code("mold").name("模具资产").table("plm_mold").keyField("mold_no").logicDelete(true)
                .columns(List.of(
                        uniq("mold_no", "模具编号"),
                        ref("part_no", "料号", "plm_material", "part_no", "物料"),
                        col("mold_name", "模具名称"), date("open_date", "开模日期"),
                        num("cavity_count", "模腔数"), num("accumulate_shots", "累计啤数"),
                        num("maintenance_cycle", "保养周期"), date("last_maintenance_date", "上次保养日期"),
                        en("status", "状态", "IN_DESIGN", "IN_MACHINING", "TRIAL", "IN_PRODUCTION", "MAINTENANCE", "OBSOLETE", "SEALED"),
                        num("asset_value", "资产价值"), col("remark", "备注")
                )).build());

        register(ModuleDef.builder()
                .code("quality").name("品质检验标准").table("plm_inspection_standard").keyField("item_name").logicDelete(true)
                .columns(List.of(
                        ref("part_no", "料号", "plm_material", "part_no", "物料"),
                        en("category", "检验分类", "DIMENSION", "APPEARANCE", "ASSEMBLY", "FUNCTION", "WATERPROOF", "INCOMING"),
                        req("item_name", "检验项目"),
                        col("standard_value", "标准值"), col("tolerance_range", "公差范围"), col("tool", "量具"),
                        col("method", "检验方法"), col("criteria", "判定标准"), col("defect_def", "缺陷定义"),
                        col("drawing_version", "图纸版本"),
                        en("status", "状态", "DRAFT", "RELEASED", "OBSOLETE"), col("version_no", "版本号")
                )).build());

        register(ModuleDef.builder()
                .code("outsourcing").name("外协发图").table("plm_outsource_request").keyField("request_no").logicDelete(true)
                .columns(List.of(
                        uniq("request_no", "申请编号"),
                        req("outsource_company", "外协单位"),
                        col("contact_person", "联系人"), col("purpose", "用途"), col("drawing_type", "图纸类型"),
                        num("validity_days", "有效期(天)"), date("expire_at", "失效时间"),
                        col("description", "说明"), col("applicant", "申请人"),
                        en("status", "状态", "DRAFT", "PENDING", "APPROVED", "REJECTED", "EXPIRED", "VOID")
                )).build());

        register(ModuleDef.builder()
                .code("project").name("研发项目NPI").table("plm_project").keyField("project_no").logicDelete(false)
                .columns(List.of(
                        uniq("project_no", "项目编号"),
                        req("project_name", "项目名称"),
                        ref("part_no", "关联料号", "plm_material", "part_no", "物料"),
                        col("customer_code", "客户编码"), col("customer_name", "客户名称"),
                        col("project_type", "项目类型"), col("project_level", "项目等级"), col("urgency", "紧急度"),
                        col("owner", "负责人"), col("department", "部门"),
                        date("start_date", "开始日期"), date("target_date", "目标日期"), date("close_date", "结束日期"),
                        en("current_gate", "当前阶段门", "G0", "G1", "G2", "G3", "G4", "G5", "G6", "G7", "G8"),
                        en("gate_status", "门状态", "ON_TRACK", "BLOCKED", "DELAYED"),
                        num("project_amount", "项目金额"), num("order_amount", "订单金额"), num("invest_amount", "投入金额"),
                        en("status", "状态", "ACTIVE", "MP", "CLOSED", "ON_HOLD"),
                        col("risk_level", "风险等级"), col("remarks", "备注")
                ))
                .startField("start_date").endField("target_date").build());

        register(ModuleDef.builder()
                .code("initiation").name("立项申请书").table("plm_project_initiation").keyField("init_no").logicDelete(false)
                .columns(List.of(
                        uniq("init_no", "申请编号"),
                        col("project_no", "项目编号"),
                        req("project_name", "项目名称"),
                        col("project_type", "项目类型"), date("start_date", "起始时间"),
                        col("department", "项目部门"), col("owner", "主要负责人"), col("cooperators", "配合人员"),
                        col("customer_no", "客户编号"), col("customer_type", "客户类型"), col("customer_level", "客户等级"),
                        col("customer_win_rate", "客户赢率"), col("market_status", "市场状况"),
                        col("customer_pain", "客户痛点"), col("key_success", "关键成功要素"),
                        col("has_competitor", "竞争对手"), col("purchase_cycle", "采购周期"), col("dev_type", "定制开发类型"),
                        col("background", "立项背景"), col("rd_objectives", "研发目标"), col("tech_solution", "技术方案"),
                        num("budget_total", "预算总额"), col("applicant", "申请人"), date("apply_date", "申请日期"),
                        en("approval_status", "审批状态", "draft", "submitted", "approved", "rejected"),
                        en("workflow_stage", "审批阶段", "apply", "dept", "review", "gm", "execute", "rejected"),
                        col("remarks", "备注")
                )).build());

        register(ModuleDef.builder()
                .code("process").name("工序路线").table("plm_process_route").keyField("route_no").logicDelete(false)
                .columns(List.of(
                        uniq("route_no", "路线编号"),
                        req("route_name", "工序路线名称"),
                        en("ref_type", "关联业务类型", "PROJECT", "MOLD", "MATERIAL", "ECN", "OTHER"),
                        col("ref_id", "关联单号"),
                        en("status", "状态", "RUNNING", "COMPLETED", "CANCELLED")
                )).build());

        register(ModuleDef.builder()
                .code("model3d").name("3D模型库").table("plm_model3d").keyField("model_name").logicDelete(true)
                .columns(List.of(
                        ref("part_no", "料号", "plm_material", "part_no", "物料"),
                        req("model_name", "模型名称"),
                        en("model_type", "模型类型", "APPEARANCE", "STRUCTURE", "MOLD_FLOW", "MOLD", "EXPLODE"),
                        col("source_format", "源格式"), col("version_no", "版本号"), col("ecn_no", "关联ECN"),
                        en("status", "状态", "DRAFT", "REVIEWING", "RELEASED", "IN_PRODUCTION", "CHANGING", "OBSOLETE", "SEALED"),
                        col("source", "来源")
                )).build());

        register(ModuleDef.builder()
                .code("workitem").name("待办事项").table("plm_work_item").keyField("item_no").logicDelete(false)
                .columns(List.of(
                        uniq("item_no", "待办编号"), col("type", "类型"),
                        req("title", "标题"), col("ref_type", "关联类型"), col("ref_id", "关联ID"),
                        num("priority", "优先级"), num("owner_id", "负责人ID"),
                        en("status", "状态", "OPEN", "ESCALATED", "DONE"), date("sla_due_at", "到期时间")
                )).build());
    }

    private void register(ModuleDef def) { modules.put(def.getCode(), def); }

    public ModuleDef get(String code) { return modules.get(code); }

    public Map<String, ModuleDef> all() { return modules; }

    // ---------- 列定义辅助 ----------
    private static ColumnDef col(String field, String label) {
        return ColumnDef.builder().field(field).label(label).type("STRING").build();
    }
    private static ColumnDef req(String field, String label) {
        return ColumnDef.builder().field(field).label(label).type("STRING").required(true).build();
    }
    private static ColumnDef uniq(String field, String label) {
        return ColumnDef.builder().field(field).label(label).type("STRING").required(true).unique(true).build();
    }
    private static ColumnDef num(String field, String label) {
        return ColumnDef.builder().field(field).label(label).type("NUMBER").build();
    }
    private static ColumnDef date(String field, String label) {
        return ColumnDef.builder().field(field).label(label).type("DATE").build();
    }
    private static ColumnDef en(String field, String label, String... values) {
        return ColumnDef.builder().field(field).label(label).type("ENUM").enums(Arrays.asList(values)).build();
    }
    private static ColumnDef ref(String field, String label, String refTable, String refColumn, String refLabel) {
        return ColumnDef.builder().field(field).label(label).type("STRING")
                .refTable(refTable).refColumn(refColumn).refLabel(refLabel).build();
    }
}
