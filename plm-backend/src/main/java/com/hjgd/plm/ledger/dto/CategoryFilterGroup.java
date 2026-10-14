package com.hjgd.plm.ledger.dto;

import lombok.Data;

import java.util.List;

/**
 * 一个分类维度上的筛选条件。
 *
 * <p>同维度内多个分类值取「或」（命中任一值即算命中该维度），
 * 不同维度之间取「与」——一条资料要同时满足每个已选维度，才进结果集。
 * 这样「资料类型=试验报告 且 产品线=A 系列」才是一次有效筛选（AC-B1-6、AC-B6-1）。
 *
 * <p>GET 绑定形式（资料汇总处检索）：{@code LedgerQuery.categoryFilter} 是对象、
 * {@code dims} 是列表，参数名逐段拼对象路径，valueIds 重复出现即列表元素：
 * {@code categoryFilter.dims[0].dimId=5&categoryFilter.dims[0].valueIds=12&categoryFilter.dims[0].valueIds=13}
 */
@Data
public class CategoryFilterGroup {

    private Long dimId;
    private List<Long> valueIds;
}
