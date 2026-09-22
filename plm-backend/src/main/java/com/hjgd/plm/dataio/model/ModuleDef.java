package com.hjgd.plm.dataio.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 模块定义: 一个业务模块的导入/导出/自检配置。
 */
@Data
@Builder
public class ModuleDef {
    private String code;                 // 模块编码(前端传参)
    private String name;                 // 模块名称
    private String table;                // 主表
    private String keyField;             // 业务主键(展示/查重)
    private boolean logicDelete;         // 是否有 deleted 逻辑删除列
    private List<ColumnDef> columns;     // 列定义(顺序即导入导出顺序)
    private String startField;           // 起始日期字段(用于日期先后校验)
    private String endField;             // 结束日期字段
}
