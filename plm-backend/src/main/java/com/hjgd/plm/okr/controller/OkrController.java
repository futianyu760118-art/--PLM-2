package com.hjgd.plm.okr.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.okr.entity.*;
import com.hjgd.plm.okr.mapper.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Tag(name = "V1 OKR")
@RestController
@RequestMapping("/v1/okrs")
@RequiredArgsConstructor
public class OkrController {

    private final OkrCycleMapper cycleMapper;
    private final OkrObjectiveMapper objectiveMapper;
    private final OkrKeyResultMapper krMapper;
    private final OkrCheckinMapper checkinMapper;
    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "周期列表")
    @GetMapping("/cycles")
    public Result<List<OkrCycle>> cycles() {
        return Result.success(cycleMapper.selectList(
                new LambdaQueryWrapper<OkrCycle>().orderByDesc(OkrCycle::getStartDate)));
    }

    @Operation(summary = "创建周期")
    @PostMapping("/cycles")
    @Transactional
    public Result<OkrCycle> createCycle(@RequestBody OkrCycle cycle) {
        if (cycle.getStatus() == null) cycle.setStatus("ACTIVE");
        cycle.setCreatedAt(LocalDateTime.now());
        cycleMapper.insert(cycle);
        return Result.success(cycle);
    }

    @Operation(summary = "目标树(含KR)")
    @GetMapping("/cycles/{cycleId}/tree")
    public Result<List<Map<String, Object>>> tree(@PathVariable Long cycleId) {
        List<OkrObjective> objectives = objectiveMapper.selectList(
                new LambdaQueryWrapper<OkrObjective>().eq(OkrObjective::getCycleId, cycleId));
        List<OkrKeyResult> allKrs = krMapper.selectList(
                new LambdaQueryWrapper<OkrKeyResult>().in(OkrKeyResult::getObjectiveId,
                        objectives.stream().map(OkrObjective::getId).toList()));

        Map<Long, List<OkrKeyResult>> krByObj = new HashMap<>();
        for (OkrKeyResult kr : allKrs) {
            krByObj.computeIfAbsent(kr.getObjectiveId(), k -> new ArrayList<>()).add(kr);
        }

        List<Map<String, Object>> tree = new ArrayList<>();
        for (OkrObjective obj : objectives) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("objective", obj);
            node.put("keyResults", krByObj.getOrDefault(obj.getId(), List.of()));
            tree.add(node);
        }
        return Result.success(tree);
    }

    @Operation(summary = "创建目标")
    @PostMapping("/objectives")
    @Transactional
    public Result<OkrObjective> createObjective(@RequestBody OkrObjective obj) {
        if (obj.getStatus() == null) obj.setStatus("ON_TRACK");
        if (obj.getProgressPct() == null) obj.setProgressPct(BigDecimal.ZERO);
        if (obj.getLevel() == null) obj.setLevel("DEPT");
        obj.setCreatedAt(LocalDateTime.now());
        objectiveMapper.insert(obj);
        return Result.success(obj);
    }

    @Operation(summary = "创建KR")
    @PostMapping("/key-results")
    @Transactional
    public Result<OkrKeyResult> createKr(@RequestBody OkrKeyResult kr) {
        if (kr.getStatus() == null) kr.setStatus("ON_TRACK");
        if (kr.getWeight() == null) kr.setWeight(BigDecimal.ONE);
        if (kr.getCurrentValue() == null) kr.setCurrentValue(kr.getBaseline());
        kr.setCreatedAt(LocalDateTime.now());
        krMapper.insert(kr);
        return Result.success(kr);
    }

    @Operation(summary = "KR打卡")
    @PostMapping("/key-results/{krId}/checkins")
    @Transactional
    public Result<OkrCheckin> checkin(@PathVariable Long krId, @RequestBody OkrCheckin checkin) {
        checkin.setKrId(krId);
        if (checkin.getCheckinDate() == null) checkin.setCheckinDate(LocalDate.now());
        checkin.setUserId(SecurityUtils.getCurrentUserId());
        checkin.setCreatedAt(LocalDateTime.now());
        checkinMapper.insert(checkin);

        OkrKeyResult kr = krMapper.selectById(krId);
        if (kr != null && checkin.getValue() != null) {
            kr.setCurrentValue(checkin.getValue());
            kr.setLastSyncAt(LocalDateTime.now());
            recalcKrStatus(kr);
            krMapper.updateById(kr);
            recalcObjectiveProgress(kr.getObjectiveId());
        }
        return Result.success(checkin);
    }

    @Operation(summary = "从KPI同步KR当前值")
    @PostMapping("/sync-from-kpi")
    public Result<Map<String, Object>> syncFromKpi() {
        List<OkrKeyResult> krs = krMapper.selectList(null);
        int synced = 0;
        for (OkrKeyResult kr : krs) {
            if (kr.getKpiCode() == null || kr.getKpiCode().isBlank()) continue;
            try {
                Map<String, Object> row = jdbcTemplate.queryForMap(
                        "SELECT actual_value FROM plm_kpi_value WHERE kpi_code=? ORDER BY period_key DESC LIMIT 1",
                        kr.getKpiCode());
                Object val = row.get("actual_value");
                if (val != null) {
                    kr.setCurrentValue(new BigDecimal(val.toString()));
                    kr.setLastSyncAt(LocalDateTime.now());
                    recalcKrStatus(kr);
                    krMapper.updateById(kr);
                    synced++;
                }
            } catch (Exception e) {
                log.debug("KR sync {} failed: {}", kr.getKpiCode(), e.getMessage());
            }
        }
        Set<Long> objIds = new HashSet<>();
        for (OkrKeyResult kr : krs) objIds.add(kr.getObjectiveId());
        objIds.forEach(this::recalcObjectiveProgress);
        return Result.success(Map.of("synced", synced));
    }

    private void recalcKrStatus(OkrKeyResult kr) {
        if (kr.getTarget() == null || kr.getCurrentValue() == null) return;
        BigDecimal progress = kr.getCurrentValue().divide(kr.getTarget(), 4, java.math.RoundingMode.HALF_UP);
        int pct = progress.multiply(BigDecimal.valueOf(100)).intValue();
        kr.setStatus(pct >= 100 ? "COMPLETED" : pct >= 70 ? "ON_TRACK" : pct >= 40 ? "AT_RISK" : "BEHIND");
    }

    private void recalcObjectiveProgress(Long objectiveId) {
        List<OkrKeyResult> krs = krMapper.selectList(
                new LambdaQueryWrapper<OkrKeyResult>().eq(OkrKeyResult::getObjectiveId, objectiveId));
        if (krs.isEmpty()) return;
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal weightedProgress = BigDecimal.ZERO;
        boolean anyBehind = false;
        for (OkrKeyResult kr : krs) {
            BigDecimal w = kr.getWeight() == null ? BigDecimal.ONE : kr.getWeight();
            totalWeight = totalWeight.add(w);
            if (kr.getTarget() != null && kr.getCurrentValue() != null && kr.getTarget().compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal p = kr.getCurrentValue().divide(kr.getTarget(), 4, java.math.RoundingMode.HALF_UP)
                        .min(BigDecimal.ONE).multiply(w);
                weightedProgress = weightedProgress.add(p);
            }
            if ("BEHIND".equals(kr.getStatus()) || "AT_RISK".equals(kr.getStatus())) anyBehind = true;
        }
        OkrObjective obj = objectiveMapper.selectById(objectiveId);
        if (obj == null) return;
        if (totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            obj.setProgressPct(weightedProgress.divide(totalWeight, 2, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)));
        }
        obj.setStatus(anyBehind ? "AT_RISK" : "ON_TRACK");
        objectiveMapper.updateById(obj);
    }

    @Data
    public static class CheckinReq {
        private BigDecimal value;
        private Integer confidence;
        private String note;
    }
}
