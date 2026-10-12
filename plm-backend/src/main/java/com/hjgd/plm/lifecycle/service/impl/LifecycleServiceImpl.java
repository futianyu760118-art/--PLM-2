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
    public void assertTransition(String objectType, String objectId, MaterialStatus from, String actionCode) {
        resolveToState(objectType, objectId, from, actionCode);
    }

    @Override
    public MaterialStatus resolveToState(String objectType, MaterialStatus from, String actionCode) {
        return resolve(objectType, null, from, actionCode);
    }

    @Override
    public MaterialStatus resolveToState(String objectType, String objectId, MaterialStatus from, String actionCode) {
        return resolve(objectType, objectId, from, actionCode);
    }

    /**
     * 迁移解析（唯一实现）。
     *
     * <p>F04：非法跳转不再只抛 409 —— 同时向 {@code plm_lifecycle_history} 写一条
     * {@code result='DENIED'} 的记录，使「被拒绝」也留下可计数的证据。
     * 此前实现只写成功迁移，非法跳转在库里查无此事，无法验证「连续 3 次非法跳转」。
     *
     * @param objectId 可为空；为空时留痕记录 object_id 为空，仍可用于按对象类型的拒统计数
     */
    private MaterialStatus resolve(String objectType, String objectId, MaterialStatus from, String actionCode) {
        if (!"PART".equalsIgnoreCase(objectType)) {
            String msg = "不支持的对象类型: " + objectType;
            recordDenied(objectType, objectId, from, actionCode, msg);
            throw new BusinessException(409, "[" + ApiErrorCodes.LIFECYCLE_DENIED + "] " + msg);
        }
        Map<String, MaterialStatus> actions = partMatrix.get(from.name());
        if (actions == null || !actions.containsKey(actionCode)) {
            String msg = "生命周期不允许: " + from + " --" + actionCode + "--> ?";
            recordDenied(objectType, objectId, from, actionCode, msg);
            throw new BusinessException(409, "[" + ApiErrorCodes.LIFECYCLE_DENIED + "] " + msg);
        }
        return actions.get(actionCode);
    }

    /**
     * 非法跳转留痕：写失败不影响拒绝本身（拒绝结果仍由异常保证）。
     *
     * <p>{@code object_id} / {@code to_state} 本就该为空（无目标状态、旧调用方拿不到对象标识），
     * 约束已放开；{@code action_code} 仍是 NOT NULL，故缺省填空串 —— 被拒的动作是什么，
     * 由 {@code comment} 一并说明，留痕不会因为一个空动作码而整条丢失。
     */
    private void recordDenied(String objectType, String objectId, MaterialStatus from,
                              String actionCode, String reason) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO plm_lifecycle_history(object_type,object_id,from_state,to_state,action_code,operator,comment,result) "
                            + "VALUES(?,?,?,?,?,?,?, 'DENIED')",
                    objectType, objectId, from == null ? null : from.name(), null,
                    actionCode == null ? "" : actionCode, currentOperator(), reason);
        } catch (Exception e) {
            log.debug("lifecycle DENIED history write skipped: {}", e.getMessage());
        }
    }

    /** 生命周期拒统计数（按对象/对象类型），供自检核对「非法跳转是否被记录」。 */
    public long countDenied(String objectType, String objectId) {
        try {
            Long n = objectId == null
                    ? jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM plm_lifecycle_history WHERE object_type=? AND result='DENIED'",
                            Long.class, objectType)
                    : jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM plm_lifecycle_history WHERE object_type=? AND object_id=? AND result='DENIED'",
                            Long.class, objectType, objectId);
            return n == null ? 0L : n;
        } catch (Exception e) {
            return 0L;
        }
    }

    private String currentOperator() {
        try {
            return com.hjgd.plm.auth.security.SecurityUtils.getCurrentRealName();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void recordHistory(String objectType, String objectId, String from, String to,
                              String actionCode, String operator, String comment) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO plm_lifecycle_history(object_type,object_id,from_state,to_state,action_code,operator,comment,result) VALUES(?,?,?,?,?,?,?,'OK')",
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
