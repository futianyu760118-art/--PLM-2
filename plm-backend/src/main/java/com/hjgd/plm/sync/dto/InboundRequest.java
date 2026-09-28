package com.hjgd.plm.sync.dto;

import lombok.Data;

import java.util.Map;

/** 同步入站请求 (EBMS -> PLM-2), 见 docs/m04-contracts-v1.md 2.2 */
@Data
public class InboundRequest {
    private String eventId;
    private String eventType;
    private String objectType;
    private String externalKey;
    private String operation;
    private String sourceSystem;
    private Integer revision;
    private String correlationId;
    private String occurredAt;
    private Map<String, Object> payload;
}
