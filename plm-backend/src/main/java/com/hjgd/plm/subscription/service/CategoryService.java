package com.hjgd.plm.subscription.service;

import com.hjgd.plm.ledger.dto.CategoryFilter;
import com.hjgd.plm.subscription.dto.CategoryDimRequest;
import com.hjgd.plm.subscription.dto.CategoryDimVO;
import com.hjgd.plm.subscription.dto.CategoryValueRequest;
import com.hjgd.plm.subscription.dto.CategoryValueVO;
import com.hjgd.plm.subscription.dto.LedgerCategoryAssignRequest;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 资料分类维度与分类值（能力点 B1、B2）。
 *
 * <p>两套可见性刻意分开命名，避免用错：
 * <ul>
 *   <li>{@link #listForSelect()} —— 能「用」的维度：本部门 + 共享 + 全局，只能选值；</li>
 *   <li>{@link #listForManage()} —— 能「改」的维度：仅本部门自定义的，全局维度永不在内。</li>
 * </ul>
 */
public interface CategoryService {

    /** 全部维度的来源标记 */
    String SOURCE_GLOBAL = "GLOBAL";
    String SOURCE_OWN = "OWN";
    String SOURCE_SHARED = "SHARED";

    // ---------- 维度查询 ----------

    /** 打标签/筛资料时可选用的维度（本部门 + 共享 + 全局；只含启用的分类值，AC-B2-4/5/6） */
    List<CategoryDimVO> listForSelect();

    /** 分类管理页可编辑的维度（仅本部门自定义；AC-B2-1/2/6） */
    List<CategoryDimVO> listForManage();

    // ---------- 维度维护（AC-B1-1 ~ AC-B1-5、AC-B1-7、AC-B2-2） ----------

    CategoryDimVO createDim(CategoryDimRequest request);

    CategoryDimVO updateDim(Long dimId, CategoryDimRequest request);

    /** 维度使用情况：用于删除前的二次确认提示（AC-B1-7） */
    Map<String, Object> dimUsage(Long dimId);

    /** 删除维度（含其分类值与资料上的该维度标签）；未带确认时拒绝（AC-B1-7） */
    void deleteDim(Long dimId, boolean confirm);

    CategoryValueVO addValue(Long dimId, CategoryValueRequest request);

    CategoryValueVO updateValue(Long dimId, Long valueId, CategoryValueRequest request);

    // ---------- 资料打标签（AC-B1-6） ----------

    /** 覆盖式写入一条资料的各维度标签；调用前会做资料写权限校验 */
    void assign(Long ledgerId, LedgerCategoryAssignRequest request);

    List<String> labelsOf(Long ledgerId);

    Map<Long, List<String>> labelsByLedgerIds(Collection<Long> ledgerIds);

    // ---------- 供订阅与报告复用 ----------

    /** 当前用户可见的维度 id（用于校验订阅里填的分类条件是否越界） */
    List<Long> visibleDimIds();

    /** 校验分类条件里的维度与值都在当前用户可见范围内（AC-B2-4） */
    void validateFilter(CategoryFilter filter);

    /** 分类条件的人可读文案，如「资料类型:试验报告,检验报告 且 产品线:A 系列」 */
    String describeFilter(CategoryFilter filter);
}
