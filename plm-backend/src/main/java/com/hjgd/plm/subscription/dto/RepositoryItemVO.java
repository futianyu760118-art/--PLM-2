package com.hjgd.plm.subscription.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 资料汇总处列表行，同时也是报告里的一条资料条目（AC-B6-2、AC-B5 报告内容）。
 *
 * <p>刻意只用一个 VO：报告里的字段与汇总处列表里的字段必须逐一对得上，
 * 否则「按报告去汇总处找这条」就会因为字段口径不同而找不到。
 */
@Data
public class RepositoryItemVO {

    private Long ledgerId;
    /** 来源证据编号（AC-B6-2） */
    private String ledgerNo;
    /** 资料名称 */
    private String name;
    private String type;
    /** 资料类型中文名，取自字典 ledger_type */
    private String typeName;
    private String status;
    /** 分类标签，形如「资料类型:试验报告」；多维度并列（AC-B1-6、AC-B6-2） */
    private List<String> categories = new ArrayList<>();
    /** 责任人姓名 */
    private String ownerName;
    private Long ownerId;
    private Long deptId;
    private String deptName;
    private LocalDateTime uploadedAt;
    /** 变更时间（AC-B6-1 的时间范围可按变更时间筛） */
    private LocalDateTime lastModifiedAt;
    private String lastModifiedBy;
    private String remark;
    /** 发生时间与版本：汇总处沿用台账已有的展示列，避免换接口后旧列消失 */
    private LocalDate occurDate;
    private String version;

    private Integer attachmentCount;
    /** 本条资料的全部附件（报告里取第一个做下载入口，汇总处逐条列出） */
    private List<RepoFileVO> files = new ArrayList<>();

    /** 指向资料汇总处该条资料的链接（AC-B5-2/3/4：一次点击直达详情） */
    private String link;
}
