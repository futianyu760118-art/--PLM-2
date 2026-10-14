package com.hjgd.plm.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 证据台账主表（能力线 A）。
 *
 * <p>「证据台账」与「资料汇总处」是同一实体的两个视图：两侧读写同一行，
 * 因此 AC-A3-3「两侧逐字段恒一致」由数据模型天然保证，不做数据复制。
 */
@Data
@TableName("plm_ledger")
public class PlmLedger extends BaseEntity {

    /** 证据编号 EV-yyyyMMdd-NNNN，sys_sequence(LEDGER_NO) 生成，全局唯一（AC-A2-5） */
    private String ledgerNo;
    private String name;
    /** 证据类型，字典 ledger_type（AC-A2-6） */
    private String type;
    private Long ownerId;
    private LocalDate occurDate;
    private String version;
    private Long deptId;
    /** DRAFT 草稿 / SUBMITTED 已提交 / ARCHIVED 已归档 / VOID 作废 */
    private String status;
    private String remark;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
    /** 最后修改人（模块侧修改后刷新为模块侧操作人，AC-A3-4） */
    private String lastModifiedBy;
    private LocalDateTime lastModifiedAt;
    /** 乐观锁版本，每次写 +1（AC-A3-7 并发冲突判定依据之一） */
    private Integer revision;
}
