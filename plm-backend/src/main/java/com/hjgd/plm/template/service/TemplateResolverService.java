package com.hjgd.plm.template.service;

import java.util.List;

/**
 * 档案树模板 / 参数模板 解析器。
 * 按 part_category + product_type + material_type 匹配最佳模板。
 */
public interface TemplateResolverService {

    ArchiveTreeTpl resolveArchiveTreeTpl(String category, String productType, String materialType);

    ParamTpl resolveParamTpl(String category, String productType, String materialType);

    List<ArchiveTreeTpl> listArchiveTreeTpls();

    List<ParamTpl> listParamTpls();

    /** 获取字典候选项(供参数模板 ENUM 项使用) */
    List<DictOption> listDictOptions(String dictType);

    class TplNode {
        public String code;
        public String name;
        public String parentCode;
        public int levelNo;
        public int sortOrder;
        public boolean leaf;
        public TplNode() {}
        public TplNode(String code, String name, String parentCode, int levelNo, int sortOrder, boolean leaf) {
            this.code = code; this.name = name; this.parentCode = parentCode;
            this.levelNo = levelNo; this.sortOrder = sortOrder; this.leaf = leaf;
        }
    }

    class ArchiveTreeTpl {
        public String tplCode;
        public String tplName;
        public List<TplNode> nodes;
        public boolean fallback;
        public ArchiveTreeTpl() {}
        public ArchiveTreeTpl(String tplCode, String tplName, List<TplNode> nodes, boolean fallback) {
            this.tplCode = tplCode; this.tplName = tplName; this.nodes = nodes; this.fallback = fallback;
        }
    }

    class ParamItem {
        public String paramKey;
        public String paramName;
        public String unit;
        public String dataType;
        public String dictType;
        public boolean required;
        public String dqSeverity;
        public int sortOrder;
        public List<DictOption> options;
        public ParamItem() {}
        public ParamItem(String paramKey, String paramName, String unit, String dataType, String dictType,
                         boolean required, String dqSeverity, int sortOrder) {
            this.paramKey = paramKey; this.paramName = paramName; this.unit = unit; this.dataType = dataType;
            this.dictType = dictType; this.required = required; this.dqSeverity = dqSeverity; this.sortOrder = sortOrder;
        }
    }

    class ParamTpl {
        public String tplCode;
        public String tplName;
        public List<ParamItem> items;
        public ParamTpl() {}
        public ParamTpl(String tplCode, String tplName, List<ParamItem> items) {
            this.tplCode = tplCode; this.tplName = tplName; this.items = items;
        }
    }

    class DictOption {
        public String dictType;
        public String label;
        public String value;
        public int sortOrder;
        public DictOption() {}
        public DictOption(String dictType, String label, String value, int sortOrder) {
            this.dictType = dictType; this.label = label; this.value = value; this.sortOrder = sortOrder;
        }
    }
}
