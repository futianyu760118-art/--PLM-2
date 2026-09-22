package com.hjgd.plm.dataio.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 字段定义: 用于通用导入/导出/自检。
 * type: STRING / NUMBER / DATE / ENUM / BOOL
 */
@Data
@Builder
public class ColumnDef {
    private String field;        // 数据库列名
    private String label;        // 中文表头
    private String type;         // 数据类型
    private boolean required;    // 必填
    private boolean unique;      // 唯一
    private List<String> enums;  // 枚举候选值
    private String refTable;     // 引用表(关联校验)
    private String refColumn;    // 引用列
    private String refLabel;     // 引用描述(提示用)
}
