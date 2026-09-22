package com.hjgd.plm.dq.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.common.ApiErrorCodes;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.dq.dto.DqRunResult;
import com.hjgd.plm.dq.service.DataQualityService;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.enums.MaterialType;
import com.hjgd.plm.template.service.TemplateResolverService;
import com.hjgd.plm.template.service.TemplateResolverService.ParamItem;
import com.hjgd.plm.template.service.TemplateResolverService.ParamTpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataQualityServiceImpl implements DataQualityService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TemplateResolverService templateResolverService;

    private static final ExpressionParser SPEL = new SpelExpressionParser();

    private static final List<FieldRule> FALLBACK_RULES = List.of(
            new FieldRule("PART_NAME_REQUIRED", "PART", "BLOCK", "#materialName != null && #materialName != ''", "中文名称不能为空"),
            new FieldRule("PART_NO_REQUIRED", "PART", "BLOCK", "#partNo != null && #partNo != ''", "料号不能为空"),
            new FieldRule("PART_TYPE_REQUIRED", "PART", "BLOCK", "#materialType != null", "物料类型不能为空"),
            new FieldRule("PART_UNIT_WARN", "PART", "WARN", "#unit != null && #unit != ''", "建议填写单位")
    );

    @Override
    public DqRunResult runForPart(Material material, String trigger) {
        List<DqRunResult.Item> items = new ArrayList<>();
        String oid = material.getId() == null ? material.getPartNo() : String.valueOf(material.getId());

        List<FieldRule> rules = loadRulesFromDb();
        Map<String, Object> ctx = buildContext(material);

        for (FieldRule rule : rules) {
            if (!"PART".equals(rule.objectType)) continue;
            boolean pass = evaluate(rule, ctx, material);
            addResult(items, rule.ruleCode, rule.severity, pass, rule.message);
        }

        if (material.getMaterialType() == MaterialType.FINISHED) {
            addResult(items, "PART_FG_EN_NAME", "WARN",
                    StringUtils.hasText(material.getNameEn()), "成品建议填写英文名称(外贸)");
            addResult(items, "PART_FG_PRODUCT_TYPE", "WARN",
                    StringUtils.hasText(material.getProductType()), "成品建议填写产品类型");
            addResult(items, "PART_FG_IP", "WARN",
                    StringUtils.hasText(material.getIpRating()), "成品建议填写IP防护等级");
        }

        if (!"CREATE".equals(trigger)) {
            checkParamCompleteness(items, material);
        }

        int block = count(items, "BLOCK");
        int warn = count(items, "WARN");
        int info = count(items, "INFO");
        int score = Math.max(0, 100 - block * 30 - warn * 10 - info * 2);

        DqRunResult result = DqRunResult.builder()
                .objectType("PART")
                .objectId(oid)
                .score(score)
                .blockCount(block)
                .warnCount(warn)
                .infoCount(info)
                .items(items)
                .build();

        persist(result, trigger == null ? "MANUAL" : trigger);
        return result;
    }

    @Override
    public void assertNoBlock(DqRunResult result) {
        if (result != null && result.hasBlock()) {
            String msg = result.getItems().stream()
                    .filter(i -> "BLOCK".equals(i.getSeverity()) && "FAIL".equals(i.getResult()))
                    .map(DqRunResult.Item::getMessage)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("数据质量阻断");
            throw new BusinessException(409, "[" + ApiErrorCodes.DQ_BLOCKED + "] " + msg);
        }
    }

    private List<FieldRule> loadRulesFromDb() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT rule_code, name, object_type, severity, check_type, expression, message_template FROM plm_dq_rule WHERE enabled=true ORDER BY id");
            List<FieldRule> rules = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String checkType = String.valueOf(row.getOrDefault("check_type", "FIELD"));
                String expr = String.valueOf(row.getOrDefault("expression", ""));
                if ("FIELD".equals(checkType) || "SPEL".equals(checkType)) {
                    rules.add(new FieldRule(
                            String.valueOf(row.get("rule_code")),
                            String.valueOf(row.getOrDefault("object_type", "PART")),
                            String.valueOf(row.getOrDefault("severity", "WARN")),
                            expr,
                            String.valueOf(row.getOrDefault("message_template", "检查不通过"))
                    ));
                }
            }
            return rules;
        } catch (Exception e) {
            log.debug("dq_rule table unavailable, using fallback rules: {}", e.getMessage());
            List<FieldRule> fb = new ArrayList<>();
            for (FieldRule r : FALLBACK_RULES) {
                fb.add(new FieldRule(r.ruleCode, "PART", r.severity, r.expression, r.message));
            }
            return fb;
        }
    }

    private boolean evaluate(FieldRule rule, Map<String, Object> ctx, Material material) {
        try {
            Expression exp = SPEL.parseExpression(rule.expression);
            org.springframework.expression.EvaluationContext evalCtx =
                    new org.springframework.expression.spel.support.StandardEvaluationContext();
            ctx.forEach(evalCtx::setVariable);
            Boolean result = exp.getValue(evalCtx, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.debug("dq rule {} eval failed: {} - {}", rule.ruleCode, rule.expression, e.getMessage());
            return true;
        }
    }

    private Map<String, Object> buildContext(Material m) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("partNo", m.getPartNo());
        ctx.put("materialName", m.getMaterialName());
        ctx.put("nameZh", m.getMaterialName());
        ctx.put("nameEn", m.getNameEn());
        ctx.put("materialType", m.getMaterialType() == null ? null : m.getMaterialType().name());
        ctx.put("category", m.getPartCategory());
        ctx.put("partCategory", m.getPartCategory());
        ctx.put("productType", m.getProductType());
        ctx.put("unit", m.getUnit());
        ctx.put("versionNo", m.getVersionNo());
        ctx.put("ipRating", m.getIpRating());
        ctx.put("powerW", m.getPowerW());
        ctx.put("specification", m.getSpecification());
        ctx.put("projectNo", m.getProjectNo());
        ctx.put("phase", m.getPhase());
        ctx.put("status", m.getStatus() == null ? null : m.getStatus().name());
        ctx.put("supplierCode", m.getSupplierCode());
        ctx.put("makeType", m.getMakeType());
        return ctx;
    }

    private void addResult(List<DqRunResult.Item> items, String code, String severity, boolean pass, String message) {
        items.add(DqRunResult.Item.builder()
                .ruleCode(code)
                .severity(severity)
                .result(pass ? "PASS" : "FAIL")
                .message(pass ? "OK" : message)
                .build());
    }

    private int count(List<DqRunResult.Item> items, String severity) {
        return (int) items.stream()
                .filter(i -> severity.equals(i.getSeverity()) && "FAIL".equals(i.getResult()))
                .count();
    }

    private void checkParamCompleteness(List<DqRunResult.Item> items, Material material) {
        if (material == null || !StringUtils.hasText(material.getPartNo())) return;
        try {
            ParamTpl tpl = templateResolverService.resolveParamTpl(
                    material.getPartCategory(), material.getProductType(),
                    material.getMaterialType() == null ? null : material.getMaterialType().name());
            if (tpl == null || tpl.items == null) return;
            Set<String> filled = loadParamKeys(material.getPartNo());
            for (ParamItem item : tpl.items) {
                if (!item.required || filled.contains(item.paramKey)) continue;
                String sev = item.dqSeverity == null ? "INFO" : item.dqSeverity.toUpperCase();
                if (!"WARN".equals(sev) && !"INFO".equals(sev)) sev = "INFO";
                addResult(items, "PARAM_REQUIRED_" + item.paramKey.toUpperCase(), sev, false,
                        "建议填写" + item.paramName + (StringUtils.hasText(item.unit) ? "(" + item.unit + ")" : ""));
            }
        } catch (Exception e) {
            log.debug("param completeness skipped: {}", e.getMessage());
        }
    }

    private Set<String> loadParamKeys(String partNo) {
        try {
            return new HashSet<>(jdbcTemplate.queryForList(
                    "SELECT param_key FROM plm_material_param WHERE part_no=?", String.class, partNo));
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    private void persist(DqRunResult result, String trigger) {
        String batch = "DQ-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            for (DqRunResult.Item item : result.getItems()) {
                jdbcTemplate.update(
                        "INSERT INTO plm_dq_run(batch_no,trigger_type,object_type,object_id,rule_code,severity,result,message) VALUES(?,?,?,?,?,?,?,?)",
                        batch, trigger, result.getObjectType(), result.getObjectId(),
                        item.getRuleCode(), item.getSeverity(), item.getResult(), item.getMessage());
            }
            jdbcTemplate.update("""
                    INSERT INTO plm_dq_object_score(object_type,object_id,score_0_100,block_count,warn_count,info_count,last_run_at,updated_at)
                    VALUES(?,?,?,?,?,?,NOW(),NOW())
                    ON CONFLICT(object_type,object_id) DO UPDATE SET
                      score_0_100=EXCLUDED.score_0_100, block_count=EXCLUDED.block_count,
                      warn_count=EXCLUDED.warn_count, info_count=EXCLUDED.info_count,
                      last_run_at=NOW(), updated_at=NOW()
                    """,
                    result.getObjectType(), result.getObjectId(), result.getScore(),
                    result.getBlockCount(), result.getWarnCount(), result.getInfoCount());
        } catch (Exception e) {
            log.warn("dq persist skipped: {}", e.getMessage());
        }
    }

    @Override
    public int runFullScan(String trigger) {
        String trig = trigger == null ? "NIGHTLY" : trigger;
        List<Material> parts;
        try {
            parts = jdbcTemplate.query("SELECT * FROM plm_material WHERE deleted = 0",
                    new BeanPropertyRowMapper<>(Material.class));
        } catch (Exception e) {
            log.warn("dq full-scan load failed: {}", e.getMessage());
            return 0;
        }
        log.info("[DQ-FULLSCAN] trigger={} parts={}", trig, parts.size());
        int scanned = 0;
        for (Material m : parts) {
            try {
                DqRunResult r = runForPart(m, trig);
                openDebts(r);
                scanned++;
            } catch (Exception e) {
                log.warn("[DQ-FULLSCAN] part={} skipped: {}", m.getPartNo(), e.getMessage());
            }
        }
        log.info("[DQ-FULLSCAN] done scanned={}", scanned);
        return scanned;
    }

    private void openDebts(DqRunResult result) {
        if (result.getItems() == null) return;
        for (DqRunResult.Item item : result.getItems()) {
            if (!"FAIL".equals(item.getResult())) continue;
            String sev = item.getSeverity();
            if (!"WARN".equals(sev) && !"INFO".equals(sev)) continue;
            try {
                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM plm_dq_debt WHERE object_type=? AND object_id=? AND rule_code=? AND severity=? AND status='OPEN'",
                        Integer.class, result.getObjectType(), result.getObjectId(),
                        item.getRuleCode(), sev);
                if (exists != null && exists > 0) continue;
                jdbcTemplate.update(
                        "INSERT INTO plm_dq_debt(object_type,object_id,rule_code,severity,status,message) VALUES(?,?,?,?,?,?)",
                        result.getObjectType(), result.getObjectId(),
                        item.getRuleCode(), sev, "OPEN", item.getMessage());
            } catch (Exception e) {
                log.debug("debt open skipped: {}", e.getMessage());
            }
        }
    }

    private record FieldRule(String ruleCode, String objectType, String severity, String expression, String message) {}
}
