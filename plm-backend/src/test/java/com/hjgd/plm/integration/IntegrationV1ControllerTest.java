package com.hjgd.plm.integration;

import com.hjgd.plm.bom.service.BomService;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.integration.controller.IntegrationV1Controller;
import com.hjgd.plm.material.service.MaterialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("IntegrationV1Controller: M02领域事件消费确认")
class IntegrationV1ControllerTest {

    private JdbcTemplate jdbcTemplate;
    private IntegrationV1Controller controller;

    @BeforeEach
    void setUp() {
        MaterialService materialService = mock(MaterialService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        BomService bomService = mock(BomService.class);
        controller = new IntegrationV1Controller(materialService, jdbcTemplate, bomService);
    }

    @Test
    @DisplayName("NEW事件确认后转PUBLISHED并写published_at")
    void shouldAckNewEvent() {
        when(jdbcTemplate.update(anyString(), eq("EVT-001"))).thenReturn(1);

        Result<Map<String, Object>> result = controller.ackEvent("EVT-001");

        assertTrue(result.isSuccess());
        assertEquals("EVT-001", result.getData().get("eventId"));
        assertEquals("PUBLISHED", result.getData().get("status"));
        assertEquals(1, result.getData().get("updated"));
        assertEquals(false, result.getData().get("idempotent"));
        verify(jdbcTemplate).update(anyString(), eq("EVT-001"));
    }

    @Test
    @DisplayName("重复ACK不重复修改且保持成功")
    void shouldAckIdempotently() {
        when(jdbcTemplate.update(anyString(), eq("EVT-001"))).thenReturn(0);

        Result<Map<String, Object>> result = controller.ackEvent("EVT-001");

        assertTrue(result.isSuccess());
        assertEquals(0, result.getData().get("updated"));
        assertEquals(true, result.getData().get("idempotent"));
    }
}
