package com.hjgd.plm.sync.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.sync.applier.SyncApplier;
import com.hjgd.plm.sync.dto.InboundRequest;
import com.hjgd.plm.sync.entity.SyncConflict;
import com.hjgd.plm.sync.entity.SyncInbox;
import com.hjgd.plm.sync.entity.SyncObject;
import com.hjgd.plm.sync.mapper.SyncConflictMapper;
import com.hjgd.plm.sync.mapper.SyncInboxMapper;
import com.hjgd.plm.sync.mapper.SyncObjectMapper;
import com.hjgd.plm.sync.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步中枢实现。遵循 AEOS Contract/Debt 登记:
 *   - 幂等: event_id 唯一
 *   - 冲突: revision 不连续/分叉 -> plm_sync_conflict(PENDING)
 *   - 业务应用: 通过 SyncApplier 分派 (按对象类型), 未注册则仅记账
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncServiceImpl implements SyncService {

    private final SyncObjectMapper objectMapper;
    private final SyncInboxMapper inboxMapper;
    private final SyncConflictMapper conflictMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper json;
    private final List<SyncApplier> appliers;

    private static final String SRC_EBMS = "EBMS";
    private static final String TGT_PLM2 = "PLM2";

    @Override
    @Transactional
    public Map<String, Object> inbound(InboundRequest req) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (req.getEventId() == null || req.getObjectType() == null || req.getExternalKey() == null) {
            throw new IllegalArgumentException("eventId/objectType/externalKey 必填");
        }
        // 1. 幂等
        Long exists = inboxMapper.selectCount(new LambdaQueryWrapper<SyncInbox>()
                .eq(SyncInbox::getEventId, req.getEventId()));
        if (exists != null && exists > 0) {
            out.put("applied", false);
            out.put("duplicated", true);
            return out;
        }

        String payloadJson = toJson(req.getPayload());
        String checksum = md5(payloadJson);
        int remoteRev = req.getRevision() == null ? 0 : req.getRevision();

        // 2. 查映射
        SyncObject obj = objectMapper.selectOne(new LambdaQueryWrapper<SyncObject>()
                .eq(SyncObject::getObjectType, req.getObjectType())
                .eq(SyncObject::getExternalKey, req.getExternalKey()));

        boolean conflict = false;
        Long conflictId = null;
        if (obj != null && remoteRev > 0) {
            int base = obj.getRevision() == null ? 0 : obj.getRevision();
            if (remoteRev != base && remoteRev != base + 1) {
                conflict = true;
                SyncConflict c = new SyncConflict();
                c.setObjectType(req.getObjectType());
                c.setExternalKey(req.getExternalKey());
                c.setFieldName(null);
                c.setLocalValue("revision=" + base + ",checksum=" + obj.getChecksum());
                c.setRemoteValue("revision=" + remoteRev + ",checksum=" + checksum);
                c.setLocalRevision(base);
                c.setRemoteRevision(remoteRev);
                c.setLocalTs(obj.getLastSyncedAt());
                c.setRemoteTs(LocalDateTime.now());
                c.setResolution("PENDING");
                c.setNote("revision 分叉 (期望 " + (base + 1) + ", 收到 " + remoteRev + ")");
                c.setCreatedAt(LocalDateTime.now());
                conflictMapper.insert(c);
                conflictId = c.getId();
            }
        }

        if (!conflict) {
            // 3. 应用业务 (若已注册 applier)
            boolean appliedOk = applyBusiness(req);
            if (appliedOk) {
                if (obj == null) {
                    obj = new SyncObject();
                    obj.setObjectType(req.getObjectType());
                    obj.setExternalKey(req.getExternalKey());
                    obj.setTargetSystem(TGT_PLM2);
                    obj.setRevision(remoteRev);
                    obj.setChecksum(checksum);
                    obj.setSourceSystem(req.getSourceSystem());
                    obj.setLastSyncedAt(LocalDateTime.now());
                    obj.setUpdatedAt(LocalDateTime.now());
                    objectMapper.insert(obj);
                } else {
                    obj.setRevision(remoteRev);
                    obj.setChecksum(checksum);
                    obj.setSourceSystem(req.getSourceSystem());
                    obj.setLastSyncedAt(LocalDateTime.now());
                    obj.setUpdatedAt(LocalDateTime.now());
                    objectMapper.updateById(obj);
                }
            } else {
                // 无 applier 或无业务映射: 仅记录映射, 标记待应用
                conflict = false;
            }
        }

        // 4. 入站记账
        SyncInbox inbox = new SyncInbox();
        inbox.setEventId(req.getEventId());
        inbox.setCorrelationId(req.getCorrelationId());
        inbox.setObjectType(req.getObjectType());
        inbox.setExternalKey(req.getExternalKey());
        inbox.setOperation(req.getOperation());
        inbox.setSourceSystem(req.getSourceSystem() == null ? SRC_EBMS : req.getSourceSystem());
        inbox.setSourceRevision(remoteRev);
        inbox.setPayloadJson(payloadJson);
        inbox.setStatus(conflict ? "CONFLICT" : "APPLIED");
        inbox.setReceivedAt(LocalDateTime.now());
        inbox.setAppliedAt(conflict ? null : LocalDateTime.now());
        inboxMapper.insert(inbox);

        out.put("applied", !conflict);
        out.put("conflict", conflict);
        out.put("conflictId", conflictId);
        return out;
    }

    private boolean applyBusiness(InboundRequest req) {
        for (SyncApplier a : appliers) {
            if (a.objectType().equalsIgnoreCase(req.getObjectType())) {
                a.apply(req.getExternalKey(), req.getOperation(), req.getPayload());
                return true;
            }
        }
        log.debug("[sync] 无 applier 处理 objectType={}, 仅记账", req.getObjectType());
        return false;
    }

    @Override
    public List<SyncConflict> listConflicts(String resolution) {
        LambdaQueryWrapper<SyncConflict> w = new LambdaQueryWrapper<>();
        if (resolution != null && !resolution.isBlank()) {
            w.eq(SyncConflict::getResolution, resolution);
        }
        w.orderByDesc(SyncConflict::getId);
        return conflictMapper.selectList(w);
    }

    @Override
    @Transactional
    public void resolveConflict(Long id, String resolution, String note, String resolvedBy) {
        SyncConflict c = conflictMapper.selectById(id);
        if (c == null) return;
        c.setResolution(resolution);
        c.setNote(note);
        c.setResolvedBy(resolvedBy == null ? "system" : resolvedBy);
        c.setResolvedAt(LocalDateTime.now());
        conflictMapper.updateById(c);
    }

    @Override
    public List<Map<String, Object>> outboundPending(int limit) {
        int n = limit <= 0 ? 50 : Math.min(limit, 500);
        return jdbcTemplate.queryForList(
                "SELECT event_id, event_type, aggregate_type, aggregate_id, payload_json, " +
                        "correlation_id, source_system, external_key, revision, created_at " +
                        "FROM plm_domain_event WHERE status='NEW' ORDER BY id ASC LIMIT " + n);
    }

    @Override
    @Transactional
    public void ackOutbound(List<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) return;
        List<Object> args = new ArrayList<>(eventIds);
        String in = String.join(",", eventIds.stream().map(x -> "?").toList());
        jdbcTemplate.update("UPDATE plm_domain_event SET status='PUBLISHED' WHERE event_id::text IN (" + in + ")", args.toArray());
    }

    @Override
    public Map<String, Object> reconcile(String objectType) {
        Map<String, Object> out = new LinkedHashMap<>();
        Long pendingConflict = conflictMapper.selectCount(new LambdaQueryWrapper<SyncConflict>()
                .eq(SyncConflict::getResolution, "PENDING"));
        Long newInbox = inboxMapper.selectCount(new LambdaQueryWrapper<SyncInbox>()
                .eq(SyncInbox::getStatus, "NEW"));
        Long failedInbox = inboxMapper.selectCount(new LambdaQueryWrapper<SyncInbox>()
                .eq(SyncInbox::getStatus, "FAILED"));
        out.put("objectType", objectType == null ? "ALL" : objectType);
        out.put("pendingConflict", pendingConflict);
        out.put("inboxNew", newInbox);
        out.put("inboxFailed", failedInbox);
        out.put("reconciledAt", LocalDateTime.now().toString());
        return out;
    }

    @Override
    public Map<String, Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("objects", objectMapper.selectCount(null));
        out.put("inbox", inboxMapper.selectCount(null));
        out.put("conflictPending", conflictMapper.selectCount(new LambdaQueryWrapper<SyncConflict>()
                .eq(SyncConflict::getResolution, "PENDING")));
        out.put("conflictAll", conflictMapper.selectCount(null));
        return out;
    }

    private String toJson(Object o) {
        if (o == null) return "{}";
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String md5(String s) {
        return DigestUtils.md5DigestAsHex(s.getBytes(StandardCharsets.UTF_8));
    }
}
