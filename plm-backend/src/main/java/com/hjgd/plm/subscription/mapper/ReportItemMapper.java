package com.hjgd.plm.subscription.mapper;

import com.hjgd.plm.ledger.dto.CategoryFilter;
import com.hjgd.plm.ledger.entity.PlmLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报告与资料汇总处的资料检索（能力线 B）。
 *
 * <p>与能力线 A 的台账主表（{@code plm_ledger}）同源：本 mapper 只读不写，
 * 不建第二事实源；{@code deleted = 0} 由 XML 显式写出（自定义 SQL 不经 MP 逻辑删除改写）。
 */
@Mapper
public interface ReportItemMapper {

    /**
     * 周期报告的资料范围（AC-B4-3）：区间内「新增或变更」且命中分类过滤的资料。
     *
     * <p>「变更」同时认台账行与附件的更新时间——附件追加/移除不触碰台账行，
     * 若只看 {@code plm_ledger.updated_at} 会漏掉 AC-B3-2 意义上的附件更新。
     *
     * @param start  周期起点（含）
     * @param end    周期终点（不含，左闭右开避免跨周期重复计数 AC-B4-4）
     * @param filter 分类过滤条件，为空表示不按分类筛选
     * @param restricted 是否收窄行级范围（订阅人为台账管理员时 false，AC-A6-1）
     * @param scopeDeptId 本部门；为 null 时退化为「仅本人」
     * @param scopeUserId 本人（上传人）
     */
    List<PlmLedger> selectPeriodItems(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end,
                                      @Param("filter") CategoryFilter filter,
                                      @Param("restricted") boolean restricted,
                                      @Param("scopeDeptId") Long scopeDeptId,
                                      @Param("scopeUserId") Long scopeUserId);
}
