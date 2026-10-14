package com.hjgd.plm.ledger.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分类筛选条件（有序分组，跨维度取「与」，见 {@link CategoryFilterGroup}）。
 *
 * <p>同一个结构同时用于两处，避免「检索到的」与「报告里的」口径不一致：
 * 资料汇总处检索（AC-B6-1）与周期报告的资料范围（AC-B4-3）。
 */
@Data
public class CategoryFilter {

    private List<CategoryFilterGroup> dims = new ArrayList<>();

    public boolean isEmpty() {
        if (dims == null || dims.isEmpty()) {
            return true;
        }
        return dims.stream().noneMatch(g -> g != null && g.getDimId() != null
                && g.getValueIds() != null && !g.getValueIds().isEmpty());
    }

    /** 去掉空分组后的有效条件，供 SQL 的 &lt;foreach&gt; 直接使用 */
    public List<CategoryFilterGroup> effective() {
        if (dims == null) {
            return List.of();
        }
        return dims.stream()
                .filter(g -> g != null && g.getDimId() != null
                        && g.getValueIds() != null && !g.getValueIds().isEmpty())
                .collect(Collectors.toList());
    }
}
