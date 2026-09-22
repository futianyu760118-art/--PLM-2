package com.hjgd.plm.lifecycle.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.lifecycle.service.impl.LifecycleServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
}
