package com.hjgd.plm.subscription.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import lombok.Data;

/**
 * 资料分类标签（AC-B1-6）。
 *
 * <p>一条资料可同时打多个维度的标签；同一维度只取一个值（唯一索引 uk_ledger_cat）。
 * 资料即 {@code plm_ledger} 一行，资料汇总处与证据台账同源。
 */
@Data
@TableName("plm_ledger_category")
public class PlmLedgerCategory extends BaseEntity {

    private Long ledgerId;
    private Long dimId;
    private Long valueId;
}
