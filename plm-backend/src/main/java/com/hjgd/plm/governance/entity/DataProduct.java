package com.hjgd.plm.governance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据产品注册（F14 / ISS-010）：每个对外数据产品必须有域、Owner、
 * 唯一真相源、服务契约（含版本与 source/version/evidence_refs）、SLA 与权限。
 * 「唯一真相源」字段是硬约束 —— 分析数据集不得冒充正式数据产品。
 */
@Data
@TableName("plm_data_product")
public class DataProduct {

    @TableId(type = IdType.INPUT)
    private String productCode;
    private String name;
    private String domain;
    private String ownerRole;
    private String ownerId;
    /** 唯一真相源，不得引入第二事实源。 */
    private String authoritativeSource;
    /** 服务契约，JSON 文本（含 version 与 source/version/evidence_refs）。 */
    private String serviceContract;
    /** SLA，JSON 文本。 */
    private String sla;
    /** 权限，JSON 文本。 */
    private String permission;
    private String status;
    private LocalDateTime createdAt;
}
