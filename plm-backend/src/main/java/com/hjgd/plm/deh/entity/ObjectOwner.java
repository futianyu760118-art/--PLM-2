package com.hjgd.plm.deh.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 唯一事实源裁定（{@code plm_object_owner}，架构 V0.2 §5.1 / F01）。
 *
 * <p>{@code object_type} 上的唯一约束即「不得存在第二事实源」的结构落点：
 * 同一类对象无法登记出第二个 Accountable Owner。
 */
@Data
@TableName("plm_object_owner")
public class ObjectOwner {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String objectType;
    /** Accountable Business Owner（角色位→姓名） */
    private String ownerRole;
    private String ownerId;
    /** 系统负责人（≠ 验收人） */
    private String systemLeadId;
    private LocalDate effectiveDate;
    /** ACTIVE | SUPERSEDED */
    private String status;
    private LocalDateTime createdAt;
}
