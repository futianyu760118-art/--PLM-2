package com.hjgd.plm.lifecycle.service.impl;

import com.hjgd.plm.common.ApiErrorCodes;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.lifecycle.service.LifecycleService;
import com.hjgd.plm.material.enums.MaterialStatus;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LifecycleServiceImpl implements LifecycleService {

    private final JdbcTemplate jdbcTemplate;

    private Map<String, Map<String, MaterialStatus>> partMatrix = new HashMap<>();
    private boolean dbDriven = false;

    @PostConstruct
    public void init() {
        loadFromDb();
    }

    private void loadFromDb() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT from_state, to_state, action_code FROM plm_lifecycle_transition WHERE object_type='PART' AND enabled=true");
            if (rows.isEmpty()) {
                log.info("[Lifecycle] no DB transitions found, using fallback");
                loadFallback();
                return;
            }
            partMatrix.clear();
            for (Map<String, Object> row : rows) {
                String from = String.valueOf(row.get("from_state"));
                String action = String.valueOf(row.get("action_code"));
                MaterialStatus to = MaterialStatus.valueOf(String.valueOf(row.get("to_state")));
                partMatrix.computeIfAbsent(from, k -> new HashMap<>()).put(action, to);
            }
            dbDriven = true;
            log.info("[Lifecycle] loaded {} transition groups from DB", partMatrix.size());
        } catch (Exception e) {
            log.warn("[Lifecycle] DB load failed, using fallback: {}", e.getMessage());
            loadFallback();
        }
    }

    private void loadFallback() {
        Map<String, MaterialStatus> m = new HashMap<>();
        m.put("submit_review", MaterialStatus.REVIEWING);
        m.put("release", MaterialStatus.RELEASED);
        partMatrix.put("DRAFT", m);

        m = new HashMap<>();
        m.put("release", MaterialStatus.RELEASED);
        m.put("reject_review", MaterialStatus.DRAFT);
        partMatrix.put("REVIEWING", m);

        m = new HashMap<>();
        m.put("start_change", MaterialStatus.CHANGING);
        m.put("to_production", MaterialStatus.IN_PRODUCTION);
        m.put("obsolete", MaterialStatus.OBSOLETE);
        partMatrix.put("RELEASED", m);

        m = new HashMap<>();
        m.put("start_change", MaterialStatus.CHANGING);
        m.put("obsolete", MaterialStatus.OBSOLETE);
        partMatrix.put("IN_PRODUCTION", m);

        m = new HashMap<>();
        m.put("finish_change", MaterialStatus.RELEASED);
        m.put("finish_change_mp", MaterialStatus.IN_PRODUCTION);
        partMatrix.put("CHANGING", m);

        m = new HashMap<>();
        m.put("seal", MaterialStatus.SEALED);
        partMatrix.put("OBSOLETE", m);

        dbDriven = false;
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
        if (!"PART".equalsIgnoreCase(objectType)) {
            throw new BusinessException(409, "[" + ApiErrorCodes.LIFECYCLE_DENIED + "] 不支持的对象类型: " + objectType);
        }
        Map<String, MaterialStatus> actions = partMatrix.get(from.name());
        if (actions == null || !actions.containsKey(actionCode)) {
            throw new BusinessException(409, "[" + ApiErrorCodes.LIFECYCLE_DENIED + "] 生命周期不允许: " + from + " --" + actionCode + "--> ?");
        }
        return actions.get(actionCode);
    }

    @Override
    public void recordHistory(String objectType, String objectId, String from, String to,
                              String actionCode, String operator, String comment) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO plm_lifecycle_history(object_type,object_id,from_state,to_state,action_code,operator,comment) VALUES(?,?,?,?,?,?,?)",
                    objectType, objectId, from, to, actionCode, operator, comment);
        } catch (Exception e) {
            log.debug("lifecycle history write skipped: {}", e.getMessage());
        }
    }

    public boolean isDbDriven() {
        return dbDriven;
    }

    public void reload() {
        loadFromDb();
    }
}
