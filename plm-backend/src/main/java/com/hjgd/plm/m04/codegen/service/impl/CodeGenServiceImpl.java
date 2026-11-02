package com.hjgd.plm.m04.codegen.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.m04.codegen.service.CodeGenService;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.system.service.SequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 编码生成服务 —— 规则表驱动（T01）
 *
 * <p>取号流程：</p>
 * <ol>
 *   <li>按 objectType 查 {@code plm_code_rule} 中 ACTIVE 且有效期内的规则（priority 小者优先）</li>
 *   <li>有规则 → 按 {@code plm_code_rule_segment} 逐段拼装（PREFIX / CONST / DATE / SEQ / DICT / FIELD）</li>
 *   <li>无规则 → 回落 {@code sys_sequence} 兼容模式（老行为，避免影响存量调用点）</li>
 * </ol>
 *
 * <p>改规则只需 UPDATE 表 + 清缓存，不需要发版 —— 这是 T01 的验收标准。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGenServiceImpl implements CodeGenService {

    private final SequenceService sequenceService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 无规则配置时的兜底序列映射。
     * <p>T02 修复：ECR 原来错误指向 ECN_NO，会与变更单撞号，已改 ECR_NO。</p>
     * <p>MOLD 指向 MOLD_NO（60 号脚本已补序列种子，此前缺失导致建模具 500）。</p>
     */
    private static final Map<String, String> FALLBACK_SEQ = Map.of(
            "PART", "PART_NO",
            "ECN", "ECN_NO",
            "BOM", "BOM_NO",
            "MOLD", "MOLD_NO",
            "ECR", "ECR_NO"
    );

    private static final String RULE_SQL =
            "SELECT id, rule_code, version_no, pattern, seq_key, reset_policy, allow_manual_override " +
            "FROM plm_code_rule " +
            "WHERE object_type = ? AND status = 'ACTIVE' " +
            "  AND (effective_from IS NULL OR effective_from <= NOW()) " +
            "  AND (effective_to   IS NULL OR effective_to   >= NOW()) " +
            "ORDER BY priority, id LIMIT 1";

    private static final String SEG_SQL =
            "SELECT segment_type, value_expr, pad_length, dict_type, field_path, transform " +
            "FROM plm_code_rule_segment WHERE rule_id = ? ORDER BY seq_no";

    @Override
    public String preview(String objectType, Map<String, Object> context) {
        return doGenerate(objectType, context, false);
    }

    @Override
    @Transactional
    public String allocate(String objectType, Map<String, Object> context, String objectId, String source) {
        String type = normalizeType(objectType);
        Generated gen = doGenerateInternal(type, context, true);

        try {
            String ctx = objectMapper.writeValueAsString(context == null ? Map.of() : context);
            Long issuer = null;
            try {
                issuer = SecurityUtils.getCurrentUserId();
            } catch (Exception ignored) {
            }
            jdbcTemplate.update(
                    "INSERT INTO plm_code_issue_log(object_type,object_id,generated_code,rule_code,rule_id,rule_version,context_json,issuer_id,source) " +
                    "VALUES(?,?,?,?,?,?,?,?,?)",
                    type, objectId, gen.code, gen.ruleCode, gen.ruleId, gen.ruleVersion,
                    ctx, issuer, source == null ? "UI" : source);
        } catch (Exception e) {
            log.warn("code issue log skipped: {}", e.getMessage());
        }
        return gen.code;
    }

    // ------------------------------------------------------------------ 内部

    private String doGenerate(String objectType, Map<String, Object> context, boolean consume) {
        return doGenerateInternal(normalizeType(objectType), context, consume).code;
    }

    private Generated doGenerateInternal(String type, Map<String, Object> context, boolean consume) {
        Map<String, Object> rule = queryRule(type);
        if (rule == null) {
            return fallback(type, consume);
        }
        Long ruleId = ((Number) rule.get("id")).longValue();
        List<Map<String, Object>> segs = jdbcTemplate.queryForList(SEG_SQL, ruleId);
        if (segs.isEmpty()) {
            log.warn("规则 {} 无分段配置，回落 sys_sequence", rule.get("rule_code"));
            return fallback(type, consume);
        }

        Map<String, Object> ctx = context == null ? Map.of() : context;
        String seqKey = (String) rule.get("seq_key");
        String resetPolicy = (String) rule.get("reset_policy");

        // 先占号（若需要），避免拼装中途异常导致号段错乱
        Long seqVal = null;
        for (Map<String, Object> seg : segs) {
            if ("SEQ".equalsIgnoreCase(str(seg.get("segment_type")))) {
                seqVal = consume
                        ? sequenceService.nextValue(seqKey, resetPolicy)
                        : sequenceService.peekValue(seqKey, resetPolicy);
                break;
            }
        }

        StringBuilder sb = new StringBuilder(32);
        String prefixCache = null;
        for (Map<String, Object> seg : segs) {
            String segType = str(seg.get("segment_type"));
            String valueExpr = str(seg.get("value_expr"));
            Integer padLength = seg.get("pad_length") == null ? null : ((Number) seg.get("pad_length")).intValue();
            String fieldPath = str(seg.get("field_path"));
            String transform = str(seg.get("transform"));

            String piece;
            switch (segType == null ? "" : segType.toUpperCase()) {
                case "PREFIX":
                    // 前缀从 sys_sequence 实时读取，避免与线上实际值（EX / IQ / MO / LX …）脱节
                    if (prefixCache == null) {
                        List<String> p = jdbcTemplate.queryForList(
                                "SELECT prefix FROM sys_sequence WHERE seq_key = ?", String.class, seqKey);
                        prefixCache = p.isEmpty() || p.get(0) == null ? "" : p.get(0);
                    }
                    piece = prefixCache;
                    break;
                case "CONST":
                    piece = valueExpr == null ? "" : valueExpr;
                    break;
                case "DATE":
                    piece = LocalDateTime.now().format(
                            DateTimeFormatter.ofPattern(valueExpr == null || valueExpr.isBlank() ? "yyyyMMdd" : valueExpr));
                    break;
                case "SEQ":
                    if (seqVal == null) {
                        throw new BusinessException("编码规则 " + rule.get("rule_code") + " 的 SEQ 段取号失败");
                    }
                    piece = pad(seqVal, padLength);
                    break;
                case "DICT":
                case "FIELD": {
                    Object raw = fieldPath == null ? null : ctx.get(fieldPath);
                    String v = raw == null ? valueExpr : String.valueOf(raw);
                    piece = applyTransform(v, transform);
                    if (piece != null && padLength != null && piece.matches("\\d+")) {
                        piece = pad(Long.parseLong(piece), padLength);
                    }
                    break;
                }
                default:
                    throw new BusinessException("编码规则 " + rule.get("rule_code") + " 存在未知分段类型: " + segType);
            }
            if (piece != null) {
                sb.append(piece);
            }
        }

        Generated gen = new Generated();
        gen.code = sb.toString();
        gen.ruleCode = (String) rule.get("rule_code");
        gen.ruleId = ruleId;
        gen.ruleVersion = (String) rule.get("version_no");
        return gen;
    }

    private Map<String, Object> queryRule(String type) {
        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(RULE_SQL, type);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            // 规则表未创建（未执行 10 号脚本）时不影响主流程
            log.debug("code rule table unavailable: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 兜底：老行为（prefix + 日期 + 序号）。
     * <p>解析顺序：显式映射 → objectType_NO → 报错并列出可用类型。</p>
     */
    private Generated fallback(String type, boolean consume) {
        String seqKey = FALLBACK_SEQ.get(type);
        if (seqKey == null) {
            String guess = type + "_NO";
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_sequence WHERE seq_key = ?", Integer.class, guess);
            if (n != null && n > 0) {
                seqKey = guess;
            }
        }
        if (seqKey == null) {
            List<String> known = new ArrayList<>(FALLBACK_SEQ.keySet());
            try {
                known.addAll(jdbcTemplate.queryForList(
                        "SELECT DISTINCT object_type FROM plm_code_rule WHERE status = 'ACTIVE' ORDER BY object_type",
                        String.class));
            } catch (Exception e) {
                log.debug("无法读取规则表补充类型清单: {}", e.getMessage());
            }
            throw new BusinessException("不支持的编号对象类型: " + type
                    + "，已配置的类型: " + String.join(", ", new java.util.TreeSet<>(known)));
        }

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT prefix, length, separator FROM sys_sequence WHERE seq_key = ?", seqKey);
        String prefix = str(row.get("prefix"));
        Integer length = row.get("length") == null ? 4 : ((Number) row.get("length")).intValue();
        String separator = str(row.get("separator"));

        long val = consume
                ? sequenceService.nextValue(seqKey, "NEVER")
                : sequenceService.peekValue(seqKey, "NEVER");

        Generated gen = new Generated();
        gen.code = (prefix == null ? "" : prefix)
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + (separator == null ? "" : separator)
                + pad(val, length);
        gen.ruleCode = seqKey;
        gen.ruleVersion = "LEGACY";
        return gen;
    }

    private String normalizeType(String objectType) {
        String t = objectType == null ? "" : objectType.trim().toUpperCase();
        if (t.isEmpty()) {
            throw new BusinessException("编号对象类型不能为空");
        }
        return t;
    }

    private static String pad(Long v, Integer width) {
        String s = String.valueOf(v);
        if (width == null || width <= 0) {
            return s;
        }
        StringBuilder b = new StringBuilder();
        for (int i = s.length(); i < width; i++) {
            b.append('0');
        }
        return b.append(s).toString();
    }

    /** 变换：只实现确定语义的几种，不认识的原样返回，不猜 */
    private static String applyTransform(String v, String transform) {
        if (v == null || transform == null || transform.isBlank()) {
            return v;
        }
        switch (transform.trim().toUpperCase()) {
            case "UPPER":  return v.toUpperCase();
            case "LOWER":  return v.toLowerCase();
            case "TRIM":   return v.trim();
            case "DIGITS": return v.replaceAll("[^0-9]", "");
            default:       return v;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** 一次生成的结果，带上规则溯源信息，便于写发放日志 */
    private static final class Generated {
        private String code;
        private String ruleCode;
        private Long ruleId;
        private String ruleVersion;
    }
}
