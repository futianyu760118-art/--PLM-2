package com.hjgd.plm.lifecycle.service.impl;

import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.ApiErrorCodes;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.lifecycle.service.LifecycleService;
import com.hjgd.plm.material.enums.MaterialStatus;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 生命周期引擎: 表驱动转换矩阵 (plm_lifecycle_transition), 守卫 = 矩阵 + 角色 + DQ。
 * v5 §4.1 所有受控对象共用同一引擎, 按 object_type 分区。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LifecycleServiceImpl implements LifecycleService {

    /** objectType -> fromState -> actionCode -> 规则 */
    private Map<String, Map<String, Map<String, LifecycleService.TransitionRule>>> matrix = new HashMap<>();
    private boolean dbDriven = false;

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        loadFromDb();
    }

    private void loadFromDb() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT object_type, from_state, to_state, action_code, roles, require_dq "
                            + "FROM plm_lifecycle_transition WHERE enabled=true");
            if (rows.isEmpty()) {
                log.info("[Lifecycle] no DB transitions found, using fallback");
                loadFallback();
                return;
            }
            Map<String, Map<String, Map<String, LifecycleService.TransitionRule>>> loaded = new HashMap<>();
            for (Map<String, Object> row : rows) {
                String objectType = String.valueOf(row.get("object_type"));
                String from = String.valueOf(row.get("from_state"));
                String action = String.valueOf(row.get("action_code"));
                String to = String.valueOf(row.get("to_state"));
                String roles = row.get("roles") == null ? null : String.valueOf(row.get("roles"));
                boolean requireDq = row.get("require_dq") == null
                        || Boolean.parseBoolean(String.valueOf(row.get("require_dq")));
                LifecycleService.TransitionRule rule = new LifecycleService.TransitionRule(
                        objectType, from, to, action, parseRoles(roles), requireDq);
                loaded.computeIfAbsent(objectType, k -> new HashMap<>())
                        .computeIfAbsent(from, k -> new HashMap<>())
                        .put(action, rule);
            }
            matrix = loaded;
            dbDriven = true;
            log.info("[Lifecycle] loaded {} object types from DB", matrix.size());
        } catch (Exception e) {
            log.warn("[Lifecycle] DB load failed, using fallback: {}", e.getMessage());
            loadFallback();
        }
    }

    private Set<String> parseRoles(String roles) {
        Set<String> out = new LinkedHashSet<>();
        if (!StringUtils.hasText(roles)) {
            return out;
        }
        for (String r : roles.split(",")) {
            if (StringUtils.hasText(r)) {
                out.add(r.trim().toUpperCase());
            }
        }
        return out;
    }

    /** DB 不可用时的最小可用矩阵(PART + BOM), 保证引擎始终可判定 */
    private void loadFallback() {
        Map<String, Map<String, Map<String, LifecycleService.TransitionRule>>> fb = new HashMap<>();
        part(fb, "DRAFT", "submit_review", "IN_REVIEW", "ENGINEER,ADMIN,RD_LEAD", true);
        part(fb, "DRAFT", "release", "RELEASED", "RD_LEAD,ADMIN", true);
        part(fb, "IN_REVIEW", "release", "RELEASED", "RD_LEAD,ADMIN", true);
        part(fb, "IN_REVIEW", "reject_review", "DRAFT", "RD_LEAD,ADMIN", false);
        part(fb, "RELEASED", "start_change", "CHANGING", "SYSTEM,ENGINEER,ADMIN", false);
        part(fb, "IN_PRODUCTION", "start_change", "CHANGING", "SYSTEM,ENGINEER,ADMIN", false);
        part(fb, "CHANGING", "finish_change", "RELEASED", "SYSTEM,ADMIN", true);
        part(fb, "CHANGING", "finish_change_mp", "IN_PRODUCTION", "SYSTEM,ADMIN", true);
        part(fb, "RELEASED", "to_production", "IN_PRODUCTION", "RD_LEAD,ADMIN,SCM_LEAD", true);
        part(fb, "RELEASED", "obsolete", "OBSOLETE", "RD_LEAD,ADMIN", true);
        part(fb, "IN_PRODUCTION", "obsolete", "OBSOLETE", "RD_LEAD,ADMIN", true);
        part(fb, "OBSOLETE", "seal", "SEALED", "ADMIN", false);

        bom(fb, "DRAFT", "release", "RELEASED", "ENGINEER,RD_LEAD,ADMIN", true);
        bom(fb, "RELEASED", "start_change", "CHANGING", "SYSTEM,ENGINEER,ADMIN", false);
        bom(fb, "CHANGING", "finish_change", "RELEASED", "SYSTEM,ENGINEER,ADMIN", true);
        bom(fb, "RELEASED", "obsolete", "OBSOLETE", "RD_LEAD,ADMIN", false);
        bom(fb, "CHANGING", "obsolete", "OBSOLETE", "RD_LEAD,ADMIN", false);

        matrix = fb;
        dbDriven = false;
    }

    private void part(Map<String, Map<String, Map<String, LifecycleService.TransitionRule>>> fb,
                      String from, String action, String to, String roles, boolean dq) {
        put(fb, "PART", from, action, to, roles, dq);
    }

    private void bom(Map<String, Map<String, Map<String, LifecycleService.TransitionRule>>> fb,
                     String from, String action, String to, String roles, boolean dq) {
        put(fb, "BOM", from, action, to, roles, dq);
    }

    private void put(Map<String, Map<String, Map<String, LifecycleService.TransitionRule>>> fb,
                     String objectType, String from, String action, String to, String roles, boolean dq) {
        fb.computeIfAbsent(objectType, k -> new HashMap<>())
                .computeIfAbsent(from, k -> new HashMap<>())
                .put(action, new LifecycleService.TransitionRule(
                        objectType, from, to, action, parseRoles(roles), dq));
    }

    public static boolean isEditLocked(MaterialStatus status) {
        return status == MaterialStatus.RELEASED
                || status == MaterialStatus.IN_PRODUCTION
                || status == MaterialStatus.SEALED
                || status == MaterialStatus.CHANGING;
    }

    public static boolean isDeleteBlocked(MaterialStatus status) {
        return status == MaterialStatus.RELEASED
                || status == MaterialStatus.IN_PRODUCTION
                || status == MaterialStatus.SEALED;
    }

    @Override
    public void assertTransition(String objectType, MaterialStatus from, String actionCode) {
        resolveToState(objectType, from, actionCode);
    }

    @Override
    public MaterialStatus resolveToState(String objectType, MaterialStatus from, String actionCode) {
        return MaterialStatus.valueOf(resolveToState(objectType, from.name(), actionCode));
    }

    @Override
    public void assertTransition(String objectType, String fromState, String actionCode) {
        resolveToState(objectType, fromState, actionCode);
    }

    @Override
    public String resolveToState(String objectType, String fromState, String actionCode) {
        return rule(objectType, fromState, actionCode).toState();
    }

    @Override
    public Set<String> allowedRoles(String objectType, String fromState, String actionCode) {
        return rule(objectType, fromState, actionCode).roles();
    }

    @Override
    public boolean requiresDq(String objectType, String fromState, String actionCode) {
        return rule(objectType, fromState, actionCode).requireDq();
    }

    @Override
    public boolean isRoleAllowed(String objectType, String fromState, String actionCode) {
        Set<String> roles = allowedRoles(objectType, fromState, actionCode);
        if (roles.isEmpty()) {
            return true;
        }
        String current = SecurityUtils.getCurrentRole();
        if (StringUtils.hasText(current) && roles.contains(current.trim().toUpperCase())) {
            return true;
        }
        // 系统自动流转(SYSTEM)与管理员不受角色码限制
        return SecurityUtils.hasRole("ADMIN") || roles.contains("SYSTEM");
    }

    @Override
    public List<LifecycleService.TransitionRule> listRules(String objectType) {
        List<LifecycleService.TransitionRule> out = new ArrayList<>();
        Map<String, Map<String, LifecycleService.TransitionRule>> byFrom =
                matrix.get(objectType == null ? "" : objectType.toUpperCase());
        if (byFrom == null) {
            return out;
        }
        byFrom.values().forEach(byAction -> out.addAll(byAction.values()));
        return out;
    }

    private LifecycleService.TransitionRule rule(String objectType, String fromState, String actionCode) {
        String type = objectType == null ? "" : objectType.toUpperCase();
        Map<String, Map<String, LifecycleService.TransitionRule>> byFrom = matrix.get(type);
        Map<String, LifecycleService.TransitionRule> byAction = byFrom == null ? null : byFrom.get(fromState);
        LifecycleService.TransitionRule r = byAction == null ? null : byAction.get(actionCode);
        if (r == null) {
            throw new BusinessException(409, "[" + ApiErrorCodes.LIFECYCLE_DENIED + "] 生命周期不允许: "
                    + type + " " + fromState + " --" + actionCode + "--> ?");
        }
        return r;
    }

    @Override
    public void recordHistory(String objectType, String objectId, String from, String to,
                             String actionCode, String operator, String comment) {
        // 不吞异常: 生命周期流转必须留痕, 写失败即整笔失败(与状态变更同事务)
        jdbcTemplate.update(
                "INSERT INTO plm_lifecycle_history(object_type,object_id,from_state,to_state,action_code,operator,comment) VALUES(?,?,?,?,?,?,?)",
                objectType, objectId, from, to, actionCode, operator, comment);
    }

    public boolean isDbDriven() {
        return dbDriven;
    }

    public void reload() {
        loadFromDb();
    }
}
