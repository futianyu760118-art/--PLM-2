package com.hjgd.plm.event.service;

import java.util.Map;

public interface DomainEventService {
    void publish(String eventType, String aggregateType, String aggregateId, Map<String, Object> payload);
}
