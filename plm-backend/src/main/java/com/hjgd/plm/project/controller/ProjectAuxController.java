package com.hjgd.plm.project.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.project.entity.ProjectReview;
import com.hjgd.plm.project.entity.ProjectSalesPromotion;
import com.hjgd.plm.project.entity.ProjectSupplyIssue;
import com.hjgd.plm.project.mapper.ProjectReviewMapper;
import com.hjgd.plm.project.mapper.ProjectSalesPromotionMapper;
import com.hjgd.plm.project.mapper.ProjectSupplyIssueMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 研发自治中心 - 项目辅助模块 (供应链品质异常 / 销售推广进度 / 项目复盘 / 统计分析)。
 * 对应 EBMS 研发项目模块页签。表见 database/26_m04_sync_hub.sql。
 */
@Tag(name = "研发自治中心-项目辅助")
@RestController
@RequestMapping({"/v1/rd", "/rd"})
@RequiredArgsConstructor
public class ProjectAuxController {

    private final ProjectSupplyIssueMapper supplyMapper;
    private final ProjectSalesPromotionMapper salesMapper;
    private final ProjectReviewMapper reviewMapper;
    private final JdbcTemplate jdbcTemplate;

    // ---------------- 供应链品质异常 ----------------
    @Operation(summary = "供应链品质异常分页")
    @GetMapping("/supply-issues")
    public Result<PageResult<ProjectSupplyIssue>> supplyPage(@RequestParam(defaultValue = "1") int pageNum,
                                                             @RequestParam(defaultValue = "10") int pageSize,
                                                             @RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) String projectNo,
                                                             @RequestParam(required = false) Integer closed) {
        LambdaQueryWrapper<ProjectSupplyIssue> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(ProjectSupplyIssue::getProblemDesc, keyword)
                    .or().like(ProjectSupplyIssue::getProductName, keyword)
                    .or().like(ProjectSupplyIssue::getProposer, keyword));
        }
        w.eq(StringUtils.hasText(projectNo), ProjectSupplyIssue::getProjectNo, projectNo);
        w.eq(closed != null, ProjectSupplyIssue::getClosed, closed);
        w.orderByDesc(ProjectSupplyIssue::getId);
        return Result.success(PageResult.of(supplyMapper.selectPage(new Page<>(pageNum, pageSize), w)));
    }

    @Operation(summary = "新增供应链品质异常")
    @PostMapping("/supply-issues")
    public Result<ProjectSupplyIssue> supplyCreate(@RequestBody ProjectSupplyIssue e) {
        if (e.getClosed() == null) e.setClosed(0);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        supplyMapper.insert(e);
        return Result.success(e);
    }

    @Operation(summary = "修改供应链品质异常")
    @PutMapping("/supply-issues")
    public Result<ProjectSupplyIssue> supplyUpdate(@RequestBody ProjectSupplyIssue e) {
        e.setUpdatedAt(LocalDateTime.now());
        supplyMapper.updateById(e);
        return Result.success(supplyMapper.selectById(e.getId()));
    }

    @Operation(summary = "删除供应链品质异常")
    @DeleteMapping("/supply-issues/{id}")
    public Result<Void> supplyDelete(@PathVariable Long id) {
        supplyMapper.deleteById(id);
        return Result.success();
    }

    // ---------------- 销售推广进度 ----------------
    @Operation(summary = "销售推广进度分页")
    @GetMapping("/sales-promotion")
    public Result<PageResult<ProjectSalesPromotion>> salesPage(@RequestParam(defaultValue = "1") int pageNum,
                                                               @RequestParam(defaultValue = "10") int pageSize,
                                                               @RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) String projectNo) {
        LambdaQueryWrapper<ProjectSalesPromotion> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(ProjectSalesPromotion::getProductModel, keyword)
                    .or().like(ProjectSalesPromotion::getCustomer, keyword)
                    .or().like(ProjectSalesPromotion::getSalesperson, keyword));
        }
        w.eq(StringUtils.hasText(projectNo), ProjectSalesPromotion::getProjectNo, projectNo);
        w.orderByDesc(ProjectSalesPromotion::getId);
        return Result.success(PageResult.of(salesMapper.selectPage(new Page<>(pageNum, pageSize), w)));
    }

    @Operation(summary = "新增销售推广进度")
    @PostMapping("/sales-promotion")
    public Result<ProjectSalesPromotion> salesCreate(@RequestBody ProjectSalesPromotion e) {
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        salesMapper.insert(e);
        return Result.success(e);
    }

    @Operation(summary = "修改销售推广进度")
    @PutMapping("/sales-promotion")
    public Result<ProjectSalesPromotion> salesUpdate(@RequestBody ProjectSalesPromotion e) {
        e.setUpdatedAt(LocalDateTime.now());
        salesMapper.updateById(e);
        return Result.success(salesMapper.selectById(e.getId()));
    }

    @Operation(summary = "删除销售推广进度")
    @DeleteMapping("/sales-promotion/{id}")
    public Result<Void> salesDelete(@PathVariable Long id) {
        salesMapper.deleteById(id);
        return Result.success();
    }

    // ---------------- 项目复盘 ----------------
    @Operation(summary = "项目复盘分页")
    @GetMapping("/reviews")
    public Result<PageResult<ProjectReview>> reviewPage(@RequestParam(defaultValue = "1") int pageNum,
                                                        @RequestParam(defaultValue = "10") int pageSize,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String projectNo) {
        LambdaQueryWrapper<ProjectReview> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(ProjectReview::getProjectNo, keyword)
                    .or().like(ProjectReview::getProjectName, keyword)
                    .or().like(ProjectReview::getInsights, keyword));
        }
        w.eq(StringUtils.hasText(projectNo), ProjectReview::getProjectNo, projectNo);
        w.orderByDesc(ProjectReview::getId);
        return Result.success(PageResult.of(reviewMapper.selectPage(new Page<>(pageNum, pageSize), w)));
    }

    @Operation(summary = "新增项目复盘")
    @PostMapping("/reviews")
    public Result<ProjectReview> reviewCreate(@RequestBody ProjectReview e) {
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        reviewMapper.insert(e);
        return Result.success(e);
    }

    @Operation(summary = "修改项目复盘")
    @PutMapping("/reviews")
    public Result<ProjectReview> reviewUpdate(@RequestBody ProjectReview e) {
        e.setUpdatedAt(LocalDateTime.now());
        reviewMapper.updateById(e);
        return Result.success(reviewMapper.selectById(e.getId()));
    }

    @Operation(summary = "删除项目复盘")
    @DeleteMapping("/reviews/{id}")
    public Result<Void> reviewDelete(@PathVariable Long id) {
        reviewMapper.deleteById(id);
        return Result.success();
    }

    // ---------------- 统计分析 ----------------
    @Operation(summary = "项目统计概览")
    @GetMapping("/analysis/summary")
    public Result<Map<String, Object>> analysisSummary() {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT count(*) AS total, " +
                        "count(*) FILTER (WHERE status='ACTIVE') AS active, " +
                        "count(*) FILTER (WHERE status='CLOSED') AS closed, " +
                        "count(*) FILTER (WHERE status='ON_HOLD') AS hold, " +
                        "count(*) FILTER (WHERE status='MP') AS mp FROM plm_project");
        return Result.success(row);
    }

    @Operation(summary = "月度立项统计")
    @GetMapping("/analysis/monthly")
    public Result<List<Map<String, Object>>> analysisMonthly() {
        return Result.success(jdbcTemplate.queryForList(
                "SELECT to_char(created_at,'YYYY-MM') AS ym, count(*) AS cnt " +
                        "FROM plm_project GROUP BY 1 ORDER BY 1"));
    }

    @Operation(summary = "按类型/阶段统计")
    @GetMapping("/analysis/by-category")
    public Result<List<Map<String, Object>>> analysisByCategory() {
        return Result.success(jdbcTemplate.queryForList(
                "SELECT coalesce(project_type,'-') AS project_type, coalesce(current_gate,'-') AS gate, count(*) AS cnt " +
                        "FROM plm_project GROUP BY 1,2 ORDER BY 3 DESC"));
    }

    @Operation(summary = "逾期项目")
    @GetMapping("/analysis/delay")
    public Result<List<Map<String, Object>>> analysisDelay() {
        return Result.success(jdbcTemplate.queryForList(
                "SELECT project_no, project_name, target_date, current_gate, status " +
                        "FROM plm_project WHERE target_date IS NOT NULL AND target_date < CURRENT_DATE " +
                        "AND status NOT IN ('CLOSED','CANCELLED') ORDER BY target_date"));
    }
}
