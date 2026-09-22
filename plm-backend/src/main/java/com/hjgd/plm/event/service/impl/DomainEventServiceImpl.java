package com.hjgd.plm.event.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.event.service.DomainEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
        String eventId = UUID.randomUUID().toString();
        try {
            String json = objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
            jdbcTemplate.update(
                    "INSERT INTO plm_domain_event(event_id,event_type,aggregate_type,aggregate_id,payload_json,status) VALUES(?,?,?,?,?,'NEW')",
                    eventId, eventType, aggregateType, aggregateId, json);
            log.info("domain-event {} {}/{} id={}", eventType, aggregateType, aggregateId, eventId);
        } catch (Exception e) {
            log.warn("domain-event write skipped: {} - {}", eventType, e.getMessage());
        }
    }
}
