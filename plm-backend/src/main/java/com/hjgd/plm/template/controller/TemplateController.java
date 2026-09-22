package com.hjgd.plm.template.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.template.service.TemplateResolverService;
import com.hjgd.plm.template.service.TemplateResolverService.ArchiveTreeTpl;
import com.hjgd.plm.template.service.TemplateResolverService.DictOption;
import com.hjgd.plm.template.service.TemplateResolverService.ParamTpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "V1 模板(建档向导用)")
@RestController
@RequestMapping("/v1/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateResolverService templateResolverService;

    @Operation(summary = "解析档案树模板(预览建档时将生成的目录节点)")
    @GetMapping("/archive-tree")
    public Result<ArchiveTreeTpl> resolveArchiveTree(
            @RequestParam(required = false) String partCategory,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) String materialType) {
        return Result.success(templateResolverService.resolveArchiveTreeTpl(partCategory, productType, materialType));
    }

    @Operation(summary = "解析参数模板(预览该品类应填的参数项)")
    @GetMapping("/params")
    public Result<ParamTpl> resolveParams(
            @RequestParam(required = false) String partCategory,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) String materialType) {
        return Result.success(templateResolverService.resolveParamTpl(partCategory, productType, materialType));
    }

    @Operation(summary = "全部档案树模板")
    @GetMapping("/archive-tree/list")
    public Result<List<ArchiveTreeTpl>> listArchiveTree() {
        return Result.success(templateResolverService.listArchiveTreeTpls());
    }

    @Operation(summary = "全部参数模板")
    @GetMapping("/params/list")
    public Result<List<ParamTpl>> listParams() {
        return Result.success(templateResolverService.listParamTpls());
    }

    @Operation(summary = "字典候选项(参数 ENUM 选项)")
    @GetMapping("/dict/{dictType}")
    public Result<List<DictOption>> dict(@PathVariable String dictType) {
        return Result.success(templateResolverService.listDictOptions(dictType));
    }
}
