package com.hjgd.plm.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.bom.entity.Bom;
import com.hjgd.plm.bom.mapper.BomMapper;
import com.hjgd.plm.ecn.entity.Ecn;
import com.hjgd.plm.ecn.enums.EcnStatus;
import com.hjgd.plm.ecn.mapper.EcnMapper;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.enums.MaterialStatus;
import com.hjgd.plm.material.mapper.MaterialMapper;
import com.hjgd.plm.mold.entity.Mold;
import com.hjgd.plm.mold.enums.MoldStatus;
import com.hjgd.plm.mold.mapper.MoldMapper;
import com.hjgd.plm.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "仪表盘统计")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final MaterialMapper materialMapper;
    private final EcnMapper ecnMapper;
    private final BomMapper bomMapper;
    private final MoldMapper moldMapper;
    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "仪表盘统计数据")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new HashMap<>();
        data.put("materialCount", materialMapper.selectCount(null));
        data.put("releasedMaterial", materialMapper.selectCount(
                new LambdaQueryWrapper<Material>().in(Material::getStatus,
                        MaterialStatus.RELEASED, MaterialStatus.IN_PRODUCTION)));
        data.put("ecnCount", ecnMapper.selectCount(null));
        data.put("pendingEcn", ecnMapper.selectCount(
                new LambdaQueryWrapper<Ecn>().in(Ecn::getStatus,
                        EcnStatus.PENDING_L1, EcnStatus.PENDING_L2)));
        data.put("effectiveEcn", ecnMapper.selectCount(
                new LambdaQueryWrapper<Ecn>().eq(Ecn::getStatus, EcnStatus.EFFECTIVE)));
        data.put("bomCount", bomMapper.selectCount(null));
        data.put("moldCount", moldMapper.selectCount(null));
        data.put("productionMold", moldMapper.selectCount(
                new LambdaQueryWrapper<Mold>().eq(Mold::getStatus, MoldStatus.IN_PRODUCTION)));
        data.put("fileCount", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plm_file", Integer.class));
        data.put("todayOps", jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE created_at >= ?",
                Integer.class, LocalDateTime.now().with(LocalTime.MIN)));
        return Result.success(data);
    }

    @Operation(summary = "物料类型分布")
    @GetMapping("/material-type-distribution")
    public Result<List<Map<String, Object>>> materialTypeDist() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT material_type AS type, COUNT(*) AS count FROM plm_material WHERE deleted = 0 GROUP BY material_type ORDER BY count DESC");
        return Result.success(rows);
    }

    @Operation(summary = "ECN 审批趋势(近7天)")
    @GetMapping("/ecn-trend")
    public Result<List<Map<String, Object>>> ecnTrend() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT DATE(created_at) AS date, COUNT(*) AS count " +
                "FROM plm_ecn WHERE created_at >= CURRENT_DATE - INTERVAL '6 day' " +
                "GROUP BY DATE(created_at) ORDER BY date");
        return Result.success(rows);
    }

    @Operation(summary = "物料状态分布")
    @GetMapping("/material-status-distribution")
    public Result<List<Map<String, Object>>> materialStatusDist() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT status, COUNT(*) AS count FROM plm_material WHERE deleted = 0 GROUP BY status ORDER BY count DESC");
        return Result.success(rows);
    }
}
