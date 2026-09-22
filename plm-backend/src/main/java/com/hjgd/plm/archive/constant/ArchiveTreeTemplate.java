package com.hjgd.plm.archive.constant;

import java.util.ArrayList;
import java.util.List;

/**
 * 单款产品固定档案目录树模板（强制标准，禁止自定义）
 * 对应需求文档第4章「单款产品系统自动归档固定目录树」
 */
public class ArchiveTreeTemplate {

    public static class Node {
        public String code;
        public String name;
        public String parentCode;
        public boolean leaf;

        public Node(String code, String name, String parentCode, boolean leaf) {
            this.code = code;
            this.name = name;
            this.parentCode = parentCode;
            this.leaf = leaf;
        }
    }

    public static final List<Node> TEMPLATE = new ArrayList<>();

    static {
        TEMPLATE.add(new Node("01", "01_外观评审档", null, true));
        TEMPLATE.add(new Node("02", "02_产品结构3D档案", null, true));
        TEMPLATE.add(new Node("03", "03_模流分析报告", null, true));
        TEMPLATE.add(new Node("04", "04_模具3D拆模总档", null, false));
        TEMPLATE.add(new Node("0401", "01_整套模具总装配STEP", "04", true));
        TEMPLATE.add(new Node("0402", "02_前模仁独立3D", "04", true));
        TEMPLATE.add(new Node("0403", "03_后模仁独立3D", "04", true));
        TEMPLATE.add(new Node("0404", "04_行位、斜顶、镶件拆分独立3D", "04", true));
        TEMPLATE.add(new Node("0405", "05_流道、排气槽、水路完整结构3D", "04", true));
        TEMPLATE.add(new Node("0406", "06_模胚整套3D装配文件", "04", true));
        TEMPLATE.add(new Node("05", "05_2D模具加工图纸包", null, false));
        TEMPLATE.add(new Node("0501", "01_模具总装开模图", "05", true));
        TEMPLATE.add(new Node("0502", "02_CNC模仁加工图", "05", true));
        TEMPLATE.add(new Node("0503", "03_EDM铜公放电线+电极清单", "05", true));
        TEMPLATE.add(new Node("0504", "04_线割加工图", "05", true));
        TEMPLATE.add(new Node("0505", "05_模具水路运水图", "05", true));
        TEMPLATE.add(new Node("0506", "06_排气槽、薄骨位标注图", "05", true));
        TEMPLATE.add(new Node("0507", "07_模胚加工图", "05", true));
        TEMPLATE.add(new Node("0508", "08_单件零件散件加工图", "05", true));
        TEMPLATE.add(new Node("06", "06_试模&修模履历", null, true));
        TEMPLATE.add(new Node("07", "07_注塑成型工艺标准", null, true));
        TEMPLATE.add(new Node("08", "08_2D产品加工图纸", null, true));
        TEMPLATE.add(new Node("09", "09_BOM清单(全版本)", null, true));
        TEMPLATE.add(new Node("10", "10_ECN变更记录(全历史)", null, true));
        TEMPLATE.add(new Node("11", "11_检验标准(IQC/IPQC)", null, true));
        TEMPLATE.add(new Node("12", "12_模具资产台账", null, true));
        TEMPLATE.add(new Node("13", "13_外协发图记录", null, true));
        TEMPLATE.add(new Node("14", "14_客户确认档", null, true));
    }
}
