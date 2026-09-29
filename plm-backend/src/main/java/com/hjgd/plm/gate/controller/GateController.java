package com.hjgd.plm.gate.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.gate.domain.GateResult;
import com.hjgd.plm.gate.entity.GateInstance;
import com.hjgd.plm.gate.service.GateService;
import com.hjgd.plm.log.annotation.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 统一 Gate 接口（F08 / ISS-004）。
 *
 * <p>「判定」与「查询」分离：{@code GET .../peek} 只读预检不留痕，
 * {@code POST .../evaluate} 正式判定并落 {@code plm_gate_instance}。
 * 两者结果语义完全一致（同一引擎），区别仅在于是否产生放行判定证据。
 */
@Tag(name = "AEOS-统一 Gate")
@RestController
@RequestMapping("/gates")
@RequiredArgsConstructor
public class GateController {

    private final GateService gateService;

    @Operation(summary = "Gate 定义清单")
    @GetMapping
    public Result<List<Map<String, Object>>> catalog() {
        return Result.success(gateService.catalog());
    }

    @Operation(summary = "只读预检（不留痕）")
    @GetMapping("/{gateCode}/{objectType}/{objectId}")
    public Result<GateResult> peek(@PathVariable String gateCode,
                                   @PathVariable String objectType,
                                   @PathVariable String objectId) {
        return Result.success(gateService.peek(gateCode, objectType, objectId));
    }

    @Operation(summary = "正式判定并留痕")
    @OperationLog(value = "Gate 判定")
    @PostMapping("/{gateCode}/{objectType}/{objectId}/evaluate")
    public Result<GateResult> evaluate(@PathVariable String gateCode,
                                       @PathVariable String objectType,
                                       @PathVariable String objectId) {
        return Result.success(gateService.evaluate(gateCode, objectType, objectId));
    }

    @Operation(summary = "放行判定历史（留痕）")
    @GetMapping("/{gateCode}/{objectType}/{objectId}/history")
    public Result<List<GateInstance>> history(@PathVariable String gateCode,
                                              @PathVariable String objectType,
                                              @PathVariable String objectId) {
        return Result.success(gateService.history(gateCode, objectType, objectId));
    }
}
