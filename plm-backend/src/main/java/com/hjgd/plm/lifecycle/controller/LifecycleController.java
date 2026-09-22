package com.hjgd.plm.lifecycle.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.lifecycle.dto.LifecycleTransitionDTO;
import com.hjgd.plm.lifecycle.service.LifecycleTransitionService;
import com.hjgd.plm.lifecycle.service.impl.LifecycleServiceImpl;
import com.hjgd.plm.log.annotation.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "V1 生命周期")
@RestController
@RequestMapping("/v1/lifecycle")
@RequiredArgsConstructor
public class LifecycleController {

    private final LifecycleServiceImpl lifecycleService;
    private final LifecycleTransitionService lifecycleTransitionService;
    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "转换矩阵配置")
    @GetMapping("/transitions")
    public Result<List<Map<String, Object>>> transitions(@RequestParam(defaultValue = "PART") String objectType) {
        try {
            return Result.success(jdbcTemplate.queryForList(
                    "SELECT object_type, from_state, to_state, action_code, roles, require_dq, enabled FROM plm_lifecycle_transition WHERE object_type=? ORDER BY from_state",
                    objectType));
        } catch (Exception e) {
            return Result.success(List.of());
        }
    }

    @Operation(summary = "对象流转历史")
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history(@RequestParam String objectType,
                                                     @RequestParam String objectId) {
        try {
            return Result.success(jdbcTemplate.queryForList(
                    "SELECT * FROM plm_lifecycle_history WHERE object_type=? AND object_id=? ORDER BY id DESC LIMIT 100",
                    objectType, objectId));
        } catch (Exception e) {
            return Result.success(List.of());
        }
    }

    @Operation(summary = "重载转换矩阵(管理员)")
    @PostMapping("/reload")
    public Result<Map<String, Object>> reload() {
        lifecycleService.reload();
        return Result.success(Map.of("dbDriven", lifecycleService.isDbDriven()));
    }

    /**
     * 通用流转 (v5 §4.1 / api-spec §9): 守卫 + 动作 + 事件, 单事务。
     * 已实现动作: PART submit_review/release/to_production/obsolete/seal/start_change, BOM release;
     * 其余动作码明确返回 409 而非静默改状态。非法跳转返回 409, 越权返回 403, 均由 @OperationLog 留痕。
     */
    @Operation(summary = "通用生命周期流转(守卫+动作+事件)")
    @OperationLog(value = "生命周期流转", partNo = "#dto.objectId")
    @PostMapping("/transition")
    public Result<LifecycleTransitionService.TransitionResult> transition(
            @Valid @RequestBody LifecycleTransitionDTO dto) {
        return Result.success(lifecycleTransitionService.transition(
                dto.getObjectType(), dto.getObjectId(), dto.getAction(), dto.getComment(),
                Boolean.TRUE.equals(dto.getForce())));
    }
}
