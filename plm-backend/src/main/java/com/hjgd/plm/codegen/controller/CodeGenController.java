package com.hjgd.plm.codegen.controller;

import com.hjgd.plm.codegen.service.CodeGenService;
import com.hjgd.plm.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "V1 自动编号")
@RestController
@RequestMapping("/v1/codegen")
@RequiredArgsConstructor
public class CodeGenController {

    private final CodeGenService codeGenService;

    @Operation(summary = "预览编号(不占号)")
    @PostMapping("/preview")
    public Result<Map<String, String>> preview(@RequestBody CodeReq req) {
        String code = codeGenService.preview(req.getObjectType(), req.getContext());
        return Result.success(Map.of("code", code, "objectType", req.getObjectType()));
    }

    @Operation(summary = "分配编号(落库日志)")
    @PostMapping("/allocate")
    public Result<Map<String, String>> allocate(@RequestBody CodeReq req) {
        String code = codeGenService.allocate(req.getObjectType(), req.getContext(), req.getObjectId(), req.getSource());
        return Result.success(Map.of("code", code, "objectType", req.getObjectType()));
    }

    @Data
    public static class CodeReq {
        private String objectType;
        private Map<String, Object> context;
        private String objectId;
        private String source;
    }
}
