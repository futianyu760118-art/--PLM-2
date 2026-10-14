package com.hjgd.plm.ledger.dto;

import lombok.Data;

import java.util.List;

/**
 * 证据台账 新建/编辑 入参（AC-A2，单界面提交）。
 *
 * <p>附件走两段式：先 {@code POST /file/upload} 拿到 fileId，再随本请求一起提交，
 * 因此整条证据仍是一次表单提交、零页面跳转（AC-A1-1）。
 *
 * <p>{@code status=DRAFT} 时跳过必填校验，只校验字段长度与格式（AC-A2-7）。
 */
@Data
public class LedgerRequest {

    /** 证据名称，≤100（AC-A2-3） */
    private String name;
    /** 证据类型，字典 ledger_type（AC-A2-6） */
    private String type;
    /** 责任人（AC-A2-2） */
    private Long ownerId;
    /** 发生时间 yyyy-MM-dd，不得晚于当前日期（AC-A2-4） */
    private String occurDate;
    /** 版本，≤20（AC-A2-3） */
    private String version;
    /** 来源部门（AC-A2-3） */
    private Long deptId;
    /** DRAFT 草稿 / SUBMITTED 已提交；缺省 SUBMITTED */
    private String status;
    /** 备注，≤500（AC-A2-3） */
    private String remark;

    /** 已上传附件 ID 列表，≥1（AC-A1-3），≤maxAttachments（AC-A1-4） */
    private List<Long> fileIds;
    /** 关联对象，≥1（AC-A2-2/A4-4），≤maxLinks（AC-A4-1） */
    private List<LinkRequest> links;
}
