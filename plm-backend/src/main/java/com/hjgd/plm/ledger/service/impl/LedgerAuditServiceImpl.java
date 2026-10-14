package com.hjgd.plm.ledger.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.material.service.EntityHistoryService;
import com.hjgd.plm.ledger.service.LedgerAuditService;
import com.hjgd.plm.system.entity.SysOperationLog;
import com.hjgd.plm.system.mapper.SysOperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 台账操作留痕实现（AC-A6-2 ~ AC-A6-7）。
 *
 * <p>日志写入失败不抛异常：审计是旁路，不能因为日志异常把已成功的业务操作变成失败
 * （AC-A6-4 的可追溯性由实体历史 + 操作日志双写兜底）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerAuditServiceImpl implements LedgerAuditService {

    private static final String ENTITY_TYPE = "LEDGER";

    private final SysOperationLogMapper logMapper;
    private final EntityHistoryService entityHistoryService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void log(String operation, String ledgerNo, Object before, Object after) {
        SysOperationLog record = base(operation, ledgerNo);
        record.setParams(paramsJson(before, after));
        record.setResult(1);
        insert(record);
        // 逐字段差异 + 快照，供 AC-A6-7 完整变更轨迹与回滚
        try {
            Object beforeMap = asMap(before);
            Object afterMap = asMap(after);
            if ("CREATE".equals(operation)) {
                entityHistoryService.recordCreate(ENTITY_TYPE, ledgerNo, (Map<String, Object>) afterMap,
                        "LEDGER", record.getOperator());
            } else if (beforeMap != null && afterMap != null) {
                entityHistoryService.recordUpdate(ENTITY_TYPE, ledgerNo,
                        (Map<String, Object>) beforeMap, (Map<String, Object>) afterMap,
                        "LEDGER", null, record.getOperator(), operation);
            }
        } catch (Exception e) {
            log.warn("台账实体历史写入失败 no={}: {}", ledgerNo, e.getMessage());
        }
    }

    @Override
    public void logDenied(String operation, String ledgerNo, String reason) {
        SysOperationLog record = base(operation, ledgerNo);
        record.setResult(0);
        record.setErrorMsg(reason);
        record.setParams(paramsJson(null, null));
        insert(record);
    }

    @Override
    public void logFailure(String operation, String ledgerNo, String errorMsg) {
        SysOperationLog record = base(operation, ledgerNo);
        record.setResult(0);
        record.setErrorMsg(errorMsg);
        insert(record);
    }

    @Override
    public List<Map<String, Object>> history(String ledgerNo) {
        String sql = "SELECT version, change_type, changed_fields, snapshot_after, changed_by, remark, changed_at "
                + "FROM plm_entity_history WHERE object_type = ? AND object_id = ? ORDER BY version DESC LIMIT 200";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, ENTITY_TYPE, ledgerNo);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("version", row.get("version"));
            m.put("changeType", row.get("change_type"));
            m.put("changes", parseJson(asText(row.get("changed_fields"))));
            m.put("snapshot", parseJson(asText(row.get("snapshot_after"))));
            m.put("changedBy", row.get("changed_by"));
            m.put("remark", row.get("remark"));
            m.put("changedAt", row.get("changed_at"));
            out.add(m);
        }
        return out;
    }

    @Override
    public Map<String, Object> trail(String ledgerNo) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ledgerNo", ledgerNo);
        out.put("history", history(ledgerNo));
        out.put("logs", operationLogs(ledgerNo));
        return out;
    }

    /** 操作日志按证据编号检索（AC-A6-7）：part_no 落的就是 ledger_no */
    private List<Map<String, Object>> operationLogs(String ledgerNo) {
        String sql = "SELECT operator, username, operation, ip, result, error_msg, params, created_at "
                + "FROM sys_operation_log WHERE part_no = ? ORDER BY id DESC LIMIT 200";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, ledgerNo);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("operator", row.get("operator"));
            m.put("username", row.get("username"));
            m.put("operation", row.get("operation"));
            m.put("ip", row.get("ip"));
            m.put("result", row.get("result"));
            m.put("errorMsg", row.get("error_msg"));
            m.put("before", pickFromParams(asText(row.get("params")), "before"));
            m.put("after", pickFromParams(asText(row.get("params")), "after"));
            m.put("createdAt", row.get("created_at"));
            out.add(m);
        }
        return out;
    }

    private SysOperationLog base(String operation, String ledgerNo) {
        SysOperationLog record = new SysOperationLog();
        record.setOperation(operation);
        record.setPartNo(ledgerNo);
        record.setCreatedAt(LocalDateTime.now());
        try {
            LoginUser user = SecurityUtils.getCurrentUser();
            record.setOperator(user.getRealName());
            record.setUserId(user.getUserId());
            record.setUsername(user.getUsername());
        } catch (Exception e) {
            record.setOperator("SYSTEM");
        }
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                record.setIp(clientIp(request));
                record.setMethod(request.getMethod() + " " + request.getRequestURI());
                record.setDevice(request.getHeader("User-Agent"));
            }
        } catch (Exception ignored) {
        }
        return record;
    }

    private void insert(SysOperationLog record) {
        try {
            logMapper.insert(record);
        } catch (Exception e) {
            log.error("台账操作日志写入失败 operation={} no={}", record.getOperation(), record.getPartNo(), e);
        }
    }

    private String paramsJson(Object before, Object after) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("before", asMap(before));
            m.put("after", asMap(after));
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private Object pickFromParams(String params, String key) {
        Object parsed = parseJson(params);
        if (parsed instanceof Map<?, ?> map) {
            return map.get(key);
        }
        return null;
    }

    private Object parseJson(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(s, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String asText(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip == null ? "" : (ip.contains(",") ? ip.split(",")[0].trim() : ip);
    }
}
