package com.hjgd.plm.m04.codegen.controller;

import com.hjgd.plm.m04.codegen.service.CodeGenService;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * 编码规则管理（T04）
 *
 * <p>规则存 {@code plm_code_rule} + {@code plm_code_rule_segment}，改完即生效，
 * 不需要发版 —— CodeGenServiceImpl 每次取号都实时读表。</p>
 */
@Slf4j
@Tag(name = "编码规则管理")
@RestController
@RequestMapping("/v1/coderule")
@RequiredArgsConstructor
public class CodeRuleController {

    private final JdbcTemplate jdbcTemplate;
    private final CodeGenService codeGenService;

    private static final String LIST_SQL =
            "SELECT id, rule_code, object_type, name, version_no, status, pattern, seq_key, reset_policy, " +
            "uniqueness_scope, preview_enabled, allow_manual_override, sample_code, priority, " +
            "effective_from, effective_to, created_at, updated_at " +
            "FROM plm_code_rule WHERE 1=1 ";

    @Operation(summary = "规则列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String objectType,
                                                  @RequestParam(required = false) String keyword) {
        StringBuilder sql = new StringBuilder(LIST_SQL);
        List<Object> args = new java.util.ArrayList<>();
        if (objectType != null && !objectType.isBlank()) {
            sql.append("AND object_type = ? ");
            args.add(objectType.trim().toUpperCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND (rule_code ILIKE ? OR name ILIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append("ORDER BY object_type, priority, id");
        return Result.success(jdbcTemplate.queryForList(sql.toString(), args.toArray()));
    }

    @Operation(summary = "规则详情（含分段）")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        List<Map<String, Object>> rules = jdbcTemplate.queryForList(LIST_SQL + "AND id = ?", id);
        if (rules.isEmpty()) {
            throw new BusinessException("规则不存在: " + id);
        }
        Map<String, Object> rule = rules.get(0);
        rule.put("segments", jdbcTemplate.queryForList(
                "SELECT id, seq_no, segment_type, value_expr, pad_length, dict_type, field_path, transform " +
                "FROM plm_code_rule_segment WHERE rule_id = ? ORDER BY seq_no", id));
        return Result.success(rule);
    }

    @Operation(summary = "新增规则（segments 可选，随主记录一起提交）")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Result<Long> create(@RequestBody CodeRuleReq req) {
        validate(req);
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO plm_code_rule(rule_code, object_type, name, version_no, status, pattern, seq_key, " +
                    "reset_policy, uniqueness_scope, preview_enabled, allow_manual_override, sample_code, priority) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, req.getRuleCode());
            ps.setString(2, req.getObjectType().toUpperCase());
            ps.setString(3, req.getName());
            ps.setString(4, orDefault(req.getVersionNo(), "V1"));
            ps.setString(5, orDefault(req.getStatus(), "DRAFT"));
            ps.setString(6, req.getPattern());
            ps.setString(7, req.getSeqKey());
            ps.setString(8, orDefault(req.getResetPolicy(), "NEVER"));
            ps.setString(9, orDefault(req.getUniquenessScope(), "GLOBAL"));
            ps.setBoolean(10, req.getPreviewEnabled() == null || req.getPreviewEnabled());
            ps.setBoolean(11, Boolean.TRUE.equals(req.getAllowManualOverride()));
            ps.setString(12, req.getSampleCode());
            ps.setInt(13, req.getPriority() == null ? 100 : req.getPriority());
            return ps;
        }, kh);
        Long id = kh.getKey() == null ? null : kh.getKey().longValue();
        replaceSegments(id, req.getSegments());
        return Result.success(id);
    }

    @Operation(summary = "修改规则")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Result<Void> update(@PathVariable Long id, @RequestBody CodeRuleReq req) {
        validate(req);
        int n = jdbcTemplate.update(
                "UPDATE plm_code_rule SET object_type=?, name=?, version_no=?, status=?, pattern=?, seq_key=?, " +
                "reset_policy=?, uniqueness_scope=?, preview_enabled=?, allow_manual_override=?, sample_code=?, " +
                "priority=?, updated_at=NOW() WHERE id=?",
                req.getObjectType().toUpperCase(), req.getName(), orDefault(req.getVersionNo(), "V1"),
                orDefault(req.getStatus(), "DRAFT"), req.getPattern(), req.getSeqKey(),
                orDefault(req.getResetPolicy(), "NEVER"), orDefault(req.getUniquenessScope(), "GLOBAL"),
                req.getPreviewEnabled() == null || req.getPreviewEnabled(),
                Boolean.TRUE.equals(req.getAllowManualOverride()),
                req.getSampleCode(), req.getPriority() == null ? 100 : req.getPriority(), id);
        if (n == 0) {
            throw new BusinessException("规则不存在: " + id);
        }
        if (req.getSegments() != null) {
            replaceSegments(id, req.getSegments());
        }
        return Result.success();
    }

    @Operation(summary = "启用/停用")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam String status) {
        String s = status == null ? "" : status.trim().toUpperCase();
        if (!"ACTIVE".equals(s) && !"DRAFT".equals(s) && !"DISABLED".equals(s)) {
            throw new BusinessException("状态只能是 ACTIVE / DRAFT / DISABLED");
        }
        int n = jdbcTemplate.update(
                "UPDATE plm_code_rule SET status=?, updated_at=NOW() WHERE id=?", s, id);
        if (n == 0) {
            throw new BusinessException("规则不存在: " + id);
        }
        return Result.success();
    }

    @Operation(summary = "删除规则")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM plm_code_rule WHERE id = ?", String.class, id);
        if ("ACTIVE".equals(status)) {
            throw new BusinessException("生效中的规则不能删除，请先停用");
        }
        jdbcTemplate.update("DELETE FROM plm_code_rule WHERE id = ?", id);
        return Result.success();
    }

    @Operation(summary = "预览编号（不占号）")
    @PostMapping("/preview")
    public Result<Map<String, String>> preview(@RequestBody CodeGenController.CodeReq req) {
        return Result.success(Map.of("code",
                codeGenService.preview(req.getObjectType(), req.getContext())));
    }

    // ---------------------------------------------------------------- 内部

    private void replaceSegments(Long ruleId, List<CodeRuleReq.Segment> segments) {
        if (ruleId == null || segments == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM plm_code_rule_segment WHERE rule_id = ?", ruleId);
        int i = 1;
        for (CodeRuleReq.Segment seg : segments) {
            jdbcTemplate.update(
                    "INSERT INTO plm_code_rule_segment(rule_id, seq_no, segment_type, value_expr, pad_length, dict_type, field_path, transform) " +
                    "VALUES(?,?,?,?,?,?,?,?)",
                    ruleId, seg.getSeqNo() == null ? i : seg.getSeqNo(),
                    seg.getSegmentType(), seg.getValueExpr(), seg.getPadLength(),
                    seg.getDictType(), seg.getFieldPath(), seg.getTransform());
            i++;
        }
    }

    private void validate(CodeRuleReq req) {
        if (req.getRuleCode() == null || req.getRuleCode().isBlank()) {
            throw new BusinessException("规则编码不能为空");
        }
        if (req.getObjectType() == null || req.getObjectType().isBlank()) {
            throw new BusinessException("对象类型不能为空");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BusinessException("规则名称不能为空");
        }
        if (req.getPattern() == null || req.getPattern().isBlank()) {
            throw new BusinessException("格式描述不能为空（仅作展示，实际按分段拼装）");
        }
        if (req.getSeqKey() == null || req.getSeqKey().isBlank()) {
            throw new BusinessException("序列键不能为空（需在 sys_sequence 中存在）");
        }
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_sequence WHERE seq_key = ?", Integer.class, req.getSeqKey());
        if (n == null || n == 0) {
            throw new BusinessException("sys_sequence 中不存在序列: " + req.getSeqKey()
                    + "，请先补序列种子");
        }
        if (req.getSegments() != null) {
            long seqCount = req.getSegments().stream()
                    .filter(s -> "SEQ".equalsIgnoreCase(s.getSegmentType())).count();
            if (seqCount > 1) {
                throw new BusinessException("一条规则只能有 1 个 SEQ 段");
            }
        }
    }

    private static String orDefault(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    @Data
    public static class CodeRuleReq {
        private String ruleCode;
        private String objectType;
        private String name;
        private String versionNo;
        private String status;
        private String pattern;
        private String seqKey;
        private String resetPolicy;
        private String uniquenessScope;
        private Boolean previewEnabled;
        private Boolean allowManualOverride;
        private String sampleCode;
        private Integer priority;
        private List<Segment> segments;

        @Data
        public static class Segment {
            private Integer seqNo;
            private String segmentType;
            private String valueExpr;
            private Integer padLength;
            private String dictType;
            private String fieldPath;
            private String transform;
        }
    }
}
