package com.hjgd.plm.material.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.material.entity.EntityHistory;
import com.hjgd.plm.material.mapper.EntityHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * 通用实体变更历史服务: 每字段差异比对 + 快照(支持回滚)。
 * 任何 update 接口都应该调用 recordUpdate,自动找出 before/after 字段差异。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityHistoryService {

    private final EntityHistoryMapper mapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 记录一次变更。
     * @param before 变更前实体快照(Map.of("field","value"))
     * @param after  变更后实体快照(Map.of("field","value"))
     */
    public EntityHistory recordUpdate(String objectType, String objectId,
                                      Map<String, Object> before, Map<String, Object> after,
                                      String changeSource, String changeRef,
                                      String changedBy, String remark) {
        if (after == null) return null;
        List<Map<String, Object>> diff = new ArrayList<>();
        Set<String> allFields = new LinkedHashSet<>();
        if (before != null) allFields.addAll(before.keySet());
        allFields.addAll(after.keySet());
        for (String field : allFields) {
            Object b = before == null ? null : before.get(field);
            Object a = after.get(field);
            // 空值规范化: null 与 "" 视为等价(数据库/JSON 反序列化差异)
            Object nb = normalize(b);
            Object na = normalize(a);
            if (!Objects.equals(nb, na)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("field", field);
                row.put("before", b);
                row.put("after", a);
                row.put("changedBy", changedBy);
                row.put("changedAt", OffsetDateTime.now().toString());
                diff.add(row);
            }
        }
        if (diff.isEmpty()) return null;

        EntityHistory h = new EntityHistory();
        h.setObjectType(objectType);
        h.setObjectId(objectId);
        h.setVersion(mapper.maxVersion(objectType, objectId) + 1);
        h.setChangeType("UPDATE");
        h.setChangeSource(changeSource == null ? "USER" : changeSource);
        h.setChangeRef(changeRef);
        h.setChangedBy(changedBy);
        h.setRemark(remark);
        try { h.setChangedFields(objectMapper.writeValueAsString(diff)); }
        catch (Exception e) { log.warn("history writeValueAsString failed: {}", e.getMessage()); }
        try { h.setSnapshotAfter(objectMapper.writeValueAsString(after)); }
        catch (Exception e) { log.warn("history snapshot writeValueAsString failed: {}", e.getMessage()); }
        mapper.insert(h);
        return h;
    }

    /** CREATE 时记录"无 before"的初始版本 */
    public EntityHistory recordCreate(String objectType, String objectId,
                                      Map<String, Object> snapshot,
                                      String changeSource, String changedBy) {
        if (snapshot == null) return null;
        EntityHistory h = new EntityHistory();
        h.setObjectType(objectType);
        h.setObjectId(objectId);
        h.setVersion(1);
        h.setChangeType("CREATE");
        h.setChangeSource(changeSource == null ? "USER" : changeSource);
        h.setChangedBy(changedBy);
        try {
            List<Map<String, Object>> diff = new ArrayList<>();
            for (Map.Entry<String, Object> e : snapshot.entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("field", e.getKey());
                row.put("before", null);
                row.put("after", e.getValue());
                row.put("changedBy", changedBy);
                row.put("changedAt", OffsetDateTime.now().toString());
                diff.add(row);
            }
            h.setChangedFields(objectMapper.writeValueAsString(diff));
            h.setSnapshotAfter(objectMapper.writeValueAsString(snapshot));
        } catch (Exception e) { log.warn("history writeValueAsString failed: {}", e.getMessage()); }
        mapper.insert(h);
        return h;
    }

    public List<EntityHistory> history(String objectType, String objectId) {
        return mapper.listByObject(objectType, objectId);
    }

    /** 取出某版本的完整快照(JSON 字符串),供回滚或对比 */
    public String snapshotAt(String objectType, String objectId, int version) {
        EntityHistory h = mapper.listByObject(objectType, objectId).stream()
                .filter(x -> x.getVersion() == version).findFirst().orElse(null);
        return h == null ? null : h.getSnapshotAfter();
    }

    private static Object normalize(Object o) {
        if (o == null) return "";
        if (o instanceof String s && s.isEmpty()) return "";
        return o;
    }
}