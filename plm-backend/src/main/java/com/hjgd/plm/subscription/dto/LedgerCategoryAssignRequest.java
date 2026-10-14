package com.hjgd.plm.subscription.dto;

import lombok.Data;

import java.util.List;

/** 一条资料的多维度分类标签（AC-B1-6：一条资料可同时打多个维度的标签） */
@Data
public class LedgerCategoryAssignRequest {

    private List<Item> items;

    @Data
    public static class Item {
        private Long dimId;
        /** 为空表示清除该维度标签 */
        private Long valueId;
    }
}
