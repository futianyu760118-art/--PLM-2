package com.hjgd.plm.ledger.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 模块/表单侧「证据区」的引用条目（AC-A3-1 同步内容清单）。
 *
 * <p>AC-A3-1 要求模块侧展示：编号、名称、类型、责任人、时间、附件入口。
 * 台账实体只存 {@code ownerId}，责任人与附件数在此装配，避免模块页各自联查。
 */
@Data
public class LedgerRefVO {

    /** 台账主键，供跳转证据详情 */
    private Long id;
    private String ledgerNo;
    private String name;
    /** 证据类型字典值 */
    private String type;
    /** 证据类型中文名 */
    private String typeLabel;
    private String status;
    private Long ownerId;
    /** 责任人姓名（sys_user.real_name） */
    private String ownerName;
    /** 发生时间 */
    private LocalDate occurDate;
    /** 上传时间 */
    private LocalDateTime uploadedAt;
    /** 附件数，>0 表示有附件入口可下载 */
    private Long attachmentCount;
    /** 台账页锚点链接（AC-A4-8 可点击跳转） */
    private String link;
}
