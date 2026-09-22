package com.hjgd.plm.material.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.material.dto.MaterialParamView;
import com.hjgd.plm.material.service.MaterialParamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "V1 物料参数")
@RestController
@RequestMapping("/v1/materials/{partNo}/params")
@RequiredArgsConstructor
public class MaterialParamController {

    private final MaterialParamService materialParamService;

    @Operation(summary = "获取料号参数(模板定义+实际值+ENUM选项)")
    @GetMapping
    public Result<List<MaterialParamView>> list(@PathVariable String partNo) {
        return Result.success(materialParamService.listForPart(partNo));
    }

    @Operation(summary = "批量保存料号参数值")
    @PutMapping
    public Result<Void> save(@PathVariable String partNo, @RequestBody ParamValueBody body) {
        materialParamService.saveValues(partNo, body == null ? null : body.getValues());
        return Result.success();
    }

    @Data
    public static class ParamValueBody {
        private Map<String, String> values;
    }
}
