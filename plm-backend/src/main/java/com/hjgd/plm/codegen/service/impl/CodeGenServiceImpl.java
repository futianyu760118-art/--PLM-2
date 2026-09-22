package com.hjgd.plm.codegen.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.codegen.service.CodeGenService;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.system.service.SequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGenServiceImpl implements CodeGenService {

    private final SequenceService sequenceService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> SEQ_MAP = Map.of(
            "PART", "PART_NO",
            "ECN", "ECN_NO",
            "BOM", "BOM_NO",
            "MOLD", "MOLD_NO",
            "ECR", "ECN_NO"
    );

    @Override
    public String preview(String objectType, Map<String, Object> context) {
        String seqKey = resolveSeq(objectType);
        // 预览不占号：基于 current+1 估算
        try {
            Long cur = jdbcTemplate.queryForObject(
                    "SELECT current_val FROM sys_sequence WHERE seq_key = ?", Long.class, seqKey);
            String prefix = jdbcTemplate.queryForObject(
                    "SELECT prefix FROM sys_sequence WHERE seq_key = ?", String.class, seqKey);
            Integer length = jdbcTemplate.queryForObject(
                    "SELECT length FROM sys_sequence WHERE seq_key = ?", Integer.class, seqKey);
            long next = (cur == null ? 0 : cur) + 1;
            String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            return prefix + date + String.format("%0" + length + "d", next);
        } catch (Exception e) {
            return "PREVIEW-" + objectType;
        }
    }

    @Override
    @Transactional
    public String allocate(String objectType, Map<String, Object> context, String objectId, String source) {
        String seqKey = resolveSeq(objectType);
        String code = sequenceService.nextNo(seqKey);
        try {
            String ctx = objectMapper.writeValueAsString(context == null ? Map.of() : context);
            Long issuer = null;
            try {
                issuer = SecurityUtils.getCurrentUserId();
            } catch (Exception ignored) {
            }
            jdbcTemplate.update(
                    "INSERT INTO plm_code_issue_log(object_type,object_id,generated_code,rule_code,context_json,issuer_id,source) VALUES(?,?,?,?,?,?,?)",
                    objectType, objectId, code, seqKey, ctx, issuer, source == null ? "UI" : source);
        } catch (Exception e) {
            log.warn("code issue log skipped: {}", e.getMessage());
        }
        return code;
    }

    private String resolveSeq(String objectType) {
        String key = SEQ_MAP.get(objectType == null ? "" : objectType.toUpperCase());
        if (key == null) {
            throw new BusinessException("不支持的编号对象类型: " + objectType);
        }
        return key;
    }
}
