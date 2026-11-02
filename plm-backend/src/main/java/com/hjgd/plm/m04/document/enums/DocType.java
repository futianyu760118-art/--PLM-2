package com.hjgd.plm.m04.document.enums;

/**
 * 文档类型（对应 plm_document.doc_type）。
 *
 * <p>老 PLM 五区里的文档并不区分类型，只有目录；新系统按类型分流，
 * 便于发布时套用不同的审批流（图纸走工程审批，体系文件走文控审批）。
 */
public enum DocType {

    /** 图纸（2D/3D） */
    DRAWING,
    /** 标准（国标/行标/企标） */
    STANDARD,
    /** 工艺文件（作业指导书/工艺卡） */
    PROCESS,
    /** 规格书（产品规格/客户规格） */
    SPEC,
    /** 说明书（安装/使用） */
    MANUAL,
    /** 体系文件（程序文件/表单/管理办法） */
    SYSTEM,
    /** 其他 */
    OTHER
}
