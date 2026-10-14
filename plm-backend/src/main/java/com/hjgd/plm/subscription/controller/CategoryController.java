package com.hjgd.plm.subscription.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.subscription.dto.CategoryDimRequest;
import com.hjgd.plm.subscription.dto.CategoryDimVO;
import com.hjgd.plm.subscription.dto.CategoryValueRequest;
import com.hjgd.plm.subscription.dto.CategoryValueVO;
import com.hjgd.plm.subscription.dto.LedgerCategoryAssignRequest;
import com.hjgd.plm.subscription.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 资料分类维度与分类值（能力点 B1、B2）。
 *
 * <p>两级权限刻意分开：{@code category:view} 只读（能看本部门维度、能按维度筛选），
 * {@code category:manage} 才能改。行级范围（只能管本部门）在服务层判定，
 * 越权调用会被拒绝并留痕（AC-B2-2/B2-3）。
 */
@Tag(name = "资料分类")
@RestController
@RequestMapping("/subscription/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "可用的分类维度（打标签/筛选用；本部门 + 共享 + 全局，AC-B2-4/5/6）")
    @GetMapping("/select")
    @PreAuthorize("hasAuthority('category:view')")
    public Result<List<CategoryDimVO>> select() {
        return Result.success(categoryService.listForSelect());
    }

    @Operation(summary = "本部门分类维度（管理页；仅本部门，AC-B2-1）")
    @GetMapping("/manage")
    @PreAuthorize("hasAuthority('category:view')")
    public Result<List<CategoryDimVO>> manage() {
        return Result.success(categoryService.listForManage());
    }

    @Operation(summary = "新增分类维度（AC-B1-1~B1-3）")
    @PostMapping("/dim")
    @PreAuthorize("hasAuthority('category:manage')")
    public Result<CategoryDimVO> createDim(@RequestBody CategoryDimRequest request) {
        return Result.success(categoryService.createDim(request));
    }

    @Operation(summary = "修改分类维度（改名/共享/排序，AC-B1-5、AC-B2-5）")
    @PutMapping("/dim/{id}")
    @PreAuthorize("hasAuthority('category:manage')")
    public Result<CategoryDimVO> updateDim(@PathVariable Long id, @RequestBody CategoryDimRequest request) {
        return Result.success(categoryService.updateDim(id, request));
    }

    @Operation(summary = "维度使用情况（删除前二次确认提示，AC-B1-7）")
    @GetMapping("/dim/{id}/usage")
    @PreAuthorize("hasAuthority('category:view')")
    public Result<Map<String, Object>> usage(@PathVariable Long id) {
        return Result.success(categoryService.dimUsage(id));
    }

    @Operation(summary = "删除分类维度（需 confirm=true，AC-B1-7）")
    @DeleteMapping("/dim/{id}")
    @PreAuthorize("hasAuthority('category:manage')")
    public Result<Void> deleteDim(@PathVariable Long id,
                                  @RequestParam(defaultValue = "false") boolean confirm) {
        categoryService.deleteDim(id, confirm);
        return Result.success();
    }

    @Operation(summary = "新增分类值（AC-B1-3）")
    @PostMapping("/dim/{id}/value")
    @PreAuthorize("hasAuthority('category:manage')")
    public Result<CategoryValueVO> addValue(@PathVariable Long id, @RequestBody CategoryValueRequest request) {
        return Result.success(categoryService.addValue(id, request));
    }

    @Operation(summary = "修改/停用分类值（AC-B1-4）")
    @PutMapping("/dim/{id}/value/{valueId}")
    @PreAuthorize("hasAuthority('category:manage')")
    public Result<CategoryValueVO> updateValue(@PathVariable Long id, @PathVariable Long valueId,
                                               @RequestBody CategoryValueRequest request) {
        return Result.success(categoryService.updateValue(id, valueId, request));
    }

    @Operation(summary = "为资料打分类标签（AC-B1-6，覆盖式写入所选维度）")
    @PostMapping("/ledger/{ledgerId}")
    @PreAuthorize("hasAuthority('ledger:edit')")
    public Result<Void> assign(@PathVariable Long ledgerId,
                              @RequestBody LedgerCategoryAssignRequest request) {
        categoryService.assign(ledgerId, request);
        return Result.success();
    }
}
