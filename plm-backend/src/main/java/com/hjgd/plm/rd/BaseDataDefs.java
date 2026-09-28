package com.hjgd.plm.rd;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础数据定义 (系统管理模块; 对应模板 M_客户/M_产品/M_物料/M_供应商/M_人员/M_部门)。
 */
public final class BaseDataDefs {

    public static final Map<String, RdSheetDefs.Def> DEFS = new LinkedHashMap<>();

    private static RdSheetDefs.Col c(String key, String label) { return new RdSheetDefs.Col(key, label, "text", null); }
    private static RdSheetDefs.Col c(String key, String label, String type) { return new RdSheetDefs.Col(key, label, type, null); }
    private static RdSheetDefs.Col c(String key, String label, String type, String options) { return new RdSheetDefs.Col(key, label, type, options); }

    static {
        DEFS.put("customer", new RdSheetDefs.Def("customer", "m_customer", "M_客户", List.of(
                c("code", "客户编号"), c("name", "客户名称"),
                c("type", "客户类型", "select", "海外客户,国内客户,内部"),
                c("level", "客户等级", "select", "A,B,C"),
                c("contact", "联系人"), c("phone", "联系方式"), c("region", "地区"), c("remark", "备注", "textarea"))));

        DEFS.put("product", new RdSheetDefs.Def("product", "m_product", "M_产品", List.of(
                c("model", "产品型号"), c("name", "产品名称"), c("spec", "规格"), c("power", "功率(W)"),
                c("voltage", "输入电压"), c("cct", "色温(K)"), c("luminous", "光通量(lm)"), c("ra", "显指"),
                c("waterproof", "防水等级"), c("dimension", "产品尺寸"), c("material", "材质"), c("remark", "备注", "textarea"))));

        DEFS.put("material", new RdSheetDefs.Def("material", "m_material", "M_物料", List.of(
                c("code", "物料代码"), c("name", "物料名称"), c("spec", "规格型号"), c("unit", "单位"),
                c("category", "物料类别", "select", "电子件,结构件,包材,标准件,其他"),
                c("supplier_code", "默认供应商"), c("price", "参考单价", "number"), c("remark", "备注", "textarea"))));

        DEFS.put("supplier", new RdSheetDefs.Def("supplier", "m_supplier", "M_供应商", List.of(
                c("code", "供应商编号"), c("name", "供应商名称"), c("contact", "联系人"), c("phone", "联系方式"),
                c("category", "供应品类"), c("level", "评级", "select", "A,B,C,D"),
                c("address", "地址"), c("remark", "备注", "textarea"))));

        DEFS.put("person", new RdSheetDefs.Def("person", "m_person", "M_人员", List.of(
                c("code", "工号"), c("name", "姓名"), c("dept", "部门"), c("position", "岗位"),
                c("remark", "备注", "textarea"))));

        DEFS.put("department", new RdSheetDefs.Def("department", "m_department", "M_部门", List.of(
                c("code", "部门编号"), c("name", "部门名称"), c("remark", "备注", "textarea"))));
    }

    public static RdSheetDefs.Def get(String type) { return DEFS.get(type); }
}
