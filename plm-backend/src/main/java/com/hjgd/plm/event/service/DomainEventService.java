package com.hjgd.plm.event.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface DomainEventService {

    /** 兼容旧调用方：等价于以默认信封发布（object_ref 取 aggregate 值，source=PLM2，version=0）。 */
    void publish(String eventType, String aggregateType, String aggregateId, Map<String, Object> payload);

    /**
     * AEOS 八字段事件信封（CONTRACT_PACK_V1 §3 / F05）。
     *
     * <p>字段与契约一一对应：{@code event_id}（平台生成） /
     * {@code event_type} / {@code object_ref{object_type,object_id}} /
     * {@code occurred_at} / {@code source_system} / {@code correlation_id} /
     * {@code version} / {@code evidence_refs}。
     *
     * <p>为空的 {@code occurredAt} 取当前时刻；为空的 {@code sourceSystem} 取 {@code PLM2}；
     * 为空的 {@code correlationId} 生成一个新的关联号——保证信封八字段齐备，
     * 但需要跨事件串联的调用方必须自行传入同一个值才能真正关联。
     *
     * @param evidenceRefs 证据引用（{@code plm_evidence.evidence_id} 列表），可为空列表
     */
    void publish(EventEnvelope envelope);

    /** AEOS 八字段事件信封。 */
    record EventEnvelope(
            String eventType,
            String objectType,
            String objectId,
            OffsetDateTime occurredAt,
            String sourceSystem,
            String correlationId,
            Integer version,
            List<String> evidenceRefs,
            Map<String, Object> payload) {
    }
}
