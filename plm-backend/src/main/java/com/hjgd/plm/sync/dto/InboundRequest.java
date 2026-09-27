package com.hjgd.plm.sync.dto;

import lombok.Data;

import java.util.Map;
import java.util.UUID;

/** 同步入站请求 (EBMS -> PLM-2), 见 docs/m04-contracts-v1.md 2.2 */
@Data
public class InboundRequest {
    private UUID eventId;
    private String eventType;
    private String objectType;
    private String externalKey;
    private String operation;
    private String sourceSystem;
    private Integer revision;
    private UUID correlationId;
    private String occurredAt;
    private Map<String, Object> payload;
}
