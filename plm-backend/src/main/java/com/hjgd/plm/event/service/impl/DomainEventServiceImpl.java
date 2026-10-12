package com.hjgd.plm.event.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.event.service.DomainEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DomainEventServiceImpl implements DomainEventService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(String eventType, String aggregateType, String aggregateId, Map<String, Object> payload) {
        publish(new EventEnvelope(eventType, aggregateType, aggregateId,
                null, null, null, null, null, payload));
    }

    @Override
    public void publish(EventEnvelope envelope) {
        if (envelope == null || !StringUtils.hasText(envelope.eventType())) {
            log.warn("domain-event 丢弃: event_type 为空");
            return;
        }
        String eventId = UUID.randomUUID().toString();
        OffsetDateTime occurredAt = envelope.occurredAt() != null ? envelope.occurredAt() : OffsetDateTime.now();
        String sourceSystem = StringUtils.hasText(envelope.sourceSystem()) ? envelope.sourceSystem() : "PLM2";
        String correlationId = StringUtils.hasText(envelope.correlationId())
                ? envelope.correlationId() : UUID.randomUUID().toString();
        int version = envelope.version() != null ? envelope.version() : 0;
        try {
            String payloadJson = writeJson(envelope.payload() == null ? Map.of() : envelope.payload());
            String evidenceJson = writeJson(envelope.evidenceRefs() == null ? List.of() : envelope.evidenceRefs());
            jdbcTemplate.update(
                    "INSERT INTO plm_domain_event(event_id,event_type,aggregate_type,aggregate_id,"
                            + "object_type,object_id,occurred_at,source_system,correlation_id,version,"
                            + "evidence_refs,payload_json,status) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'NEW')",
                    eventId, envelope.eventType(), envelope.objectType(), envelope.objectId(),
                    envelope.objectType(), envelope.objectId(), occurredAt, sourceSystem, correlationId,
                    version, evidenceJson, payloadJson);
            log.info("domain-event {} {}/{} id={} corr={}", envelope.eventType(),
                    envelope.objectType(), envelope.objectId(), eventId, correlationId);
        } catch (Exception e) {
            log.warn("domain-event write skipped: {} - {}", envelope.eventType(), e.getMessage());
        }
    }

    private String writeJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
