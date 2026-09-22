package com.hjgd.plm.bom.controller;

import com.hjgd.plm.bom.dto.BomDTO;
import com.hjgd.plm.bom.dto.BomItemDTO;
import com.hjgd.plm.bom.entity.Bom;
import com.hjgd.plm.bom.entity.BomItem;
import com.hjgd.plm.bom.entity.BomTemplate;
import com.hjgd.plm.bom.mapper.BomTemplateMapper;
import com.hjgd.plm.bom.service.BomService;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.log.annotation.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "BOM多级爆炸 / 三态管理")
@RestController
@RequestMapping({"/bom", "/v1/boms"})
@RequiredArgsConstructor
public class BomController {

    private final BomService bomService;
    private final BomTemplateMapper bomTemplateMapper;

    @Operation(summary = "BOM分页列表")
    @GetMapping("/page")
    public Result<PageResult<Bom>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                        @RequestParam(defaultValue = "10") Integer pageSize,
                                        @RequestParam(required = false) String rootPartNo,
                                        @RequestParam(required = false) String status) {
        return Result.success(bomService.page(pageNum, pageSize, rootPartNo, status));
    }

    @Operation(summary = "BOM详情")
    @GetMapping("/{id}")
    public Result<Bom> get(@PathVariable Long id) {
        return Result.success(bomService.getById(id));
    }

    @Operation(summary = "新建BOM(自动创建根节点)")
    @PreAuthorize("hasAuthority('bom:add')")
    @PostMapping
    public Result<Bom> create(@Valid @RequestBody BomDTO dto) {
        return Result.success(bomService.create(dto));
    }

    @Operation(summary = "获取BOM树形结构")
    @GetMapping("/{bomId}/tree")
    public Result<List<BomItem>> tree(@PathVariable Long bomId) {
        return Result.success(bomService.getTree(bomId));
    }

    @Operation(summary = "获取BOM扁平列表")
    @GetMapping("/{bomId}/flat")
    public Result<List<BomItem>> flat(@PathVariable Long bomId) {
        return Result.success(bomService.getFlatList(bomId));
    }

    @Operation(summary = "新增BOM明细(含循环引用检测)")
    @PreAuthorize("hasAuthority('bom:edit')")
    @PostMapping("/item")
    public Result<BomItem> addItem(@Valid @RequestBody BomItemDTO dto) {
        return Result.success(bomService.addItem(dto));
    }

    @Operation(summary = "修改BOM明细")
    @PreAuthorize("hasAuthority('bom:edit')")
    @PutMapping("/item")
    public Result<BomItem> updateItem(@Valid @RequestBody BomItemDTO dto) {
        return Result.success(bomService.updateItem(dto));
    }

    @Operation(summary = "删除BOM明细(含子件)")
    @PreAuthorize("hasAuthority('bom:edit')")
    @DeleteMapping("/item/{itemId}")
    public Result<Void> deleteItem(@PathVariable Long itemId) {
        bomService.deleteItem(itemId);
        return Result.success();
    }

    @Operation(summary = "BOM正式发布(含门禁+快照)")
    @OperationLog(value = "BOM正式发布", partNo = "#bomId")
    @PreAuthorize("hasAuthority('bom:add')")
    @PutMapping("/{bomId}/release")
    public Result<Void> release(@PathVariable Long bomId) {
        bomService.release(bomId);
        return Result.success();
    }

    @Operation(summary = "BOM版本归档")
    @PutMapping("/{bomId}/archive")
    public Result<Void> archive(@PathVariable Long bomId,
                                @RequestParam String ecnNo,
                                @RequestParam(required = false) String reason) {
        bomService.archiveVersion(bomId, ecnNo, reason);
        return Result.success();
    }

    @Operation(summary = "从EBOM构建MBOM")
    @PreAuthorize("hasAuthority('bom:add')")
    @PostMapping("/{ebomId}/actions/build-mbom")
    public Result<Bom> buildMbom(@PathVariable Long ebomId) {
        return Result.success(bomService.buildMbom(ebomId));
    }

    @Operation(summary = "构建SBOM(售后备件)")
    @PreAuthorize("hasAuthority('bom:add')")
    @PostMapping("/{sourceBomId}/actions/build-sbom")
    public Result<Bom> buildSbom(@PathVariable Long sourceBomId) {
        return Result.success(bomService.buildSbom(sourceBomId));
    }

    @Operation(summary = "反查where-used")
    @GetMapping("/where-used/{partNo}")
    public Result<List<Map<String, Object>>> whereUsed(@PathVariable String partNo) {
        return Result.success(bomService.whereUsed(partNo));
    }

    @Operation(summary = "成本BOM展开(CBOM)")
    @GetMapping("/cbom/{rootPartNo}")
    public Result<List<Map<String, Object>>> cbom(@PathVariable String rootPartNo) {
        return Result.success(bomService.cbomExpand(rootPartNo));
    }

    @Operation(summary = "拖拽排序:将 [itemId,...] 按顺序写入 sort_order")
    @PreAuthorize("hasAuthority('bom:add')")
    @PostMapping("/{bomId}/actions/reorder")
    public Result<Void> reorder(@PathVariable Long bomId,
                                 @RequestBody List<Long> itemIdsInOrder) {
        bomService.reorderItems(bomId, itemIdsInOrder);
        return Result.success();
    }

    @Operation(summary = "一键从模板(结构件/紧固件/包装/电子)套用创建 BOM")
    @PreAuthorize("hasAuthority('bom:add')")
    @PostMapping("/actions/create-from-templates")
    public Result<Bom> createFromTemplates(@RequestBody Map<String, Object> req) {
        String rootPartNo = String.valueOf(req.get("rootPartNo"));
        @SuppressWarnings("unchecked")
        List<String> codes = (List<String>) req.get("templateCodes");
        boolean includeOptional = Boolean.TRUE.equals(req.get("includeOptional"));
        return Result.success(bomService.createFromTemplates(rootPartNo, codes, includeOptional));
    }

    @Operation(summary = "BOM 模板清单(用于新建时选择)")
    @GetMapping("/templates/list")
    public Result<List<BomTemplate>> templates() {
        List<BomTemplate> all = bomTemplateMapper.listEnabled();
        for (BomTemplate t : all) {
            t.setItems(bomTemplateMapper.listItems(t.getId()));
        }
        return Result.success(all);
    }
}
