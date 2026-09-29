package com.hjgd.plm.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.event.service.DomainEventService;
import com.hjgd.plm.event.service.impl.DomainEventServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * F05 跨域契约：AEOS 八字段事件信封必须齐备。
 *
 * <p>八字段：event_id / event_type / object_ref{object_type,object_id} /
 * occurred_at / source_system / correlation_id / version / evidence_refs。
 */
@DisplayName("F05 事件八字段信封")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DomainEventServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    private DomainEventServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DomainEventServiceImpl(jdbcTemplate, new ObjectMapper());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    }

    /** 取 INSERT 绑定的参数。 */
    private Object[] capturedArgs() {
        ArgumentCaptor<Object[]> cap = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), cap.capture());
        return cap.getValue();
    }

    @Test
    @DisplayName("完整信封: 八字段全部落库, 含 evidence_refs")
    void publishFullEnvelope() {
        OffsetDateTime at = OffsetDateTime.parse("2026-09-29T10:00:00+08:00");
        service.publish(new DomainEventService.EventEnvelope(
                "part.released", "PART", "HJ-001", at, "PLM2", "CORR-1", 3,
                List.of("EV-1", "EV-2"), Map.of("k", "v")));

        Object[] a = capturedArgs();
        assertEquals("part.released", a[1], "event_type");
        assertEquals("PART", a[2], "aggregate_type(兼容列)");
        assertEquals("HJ-001", a[3], "aggregate_id(兼容列)");
        assertEquals("PART", a[4], "object_ref.object_type");
        assertEquals("HJ-001", a[5], "object_ref.object_id");
        assertEquals(at, a[6], "occurred_at");
        assertEquals("PLM2", a[7], "source_system");
        assertEquals("CORR-1", a[8], "correlation_id");
        assertEquals(3, a[9], "version");
        assertEquals("[\"EV-1\",\"EV-2\"]", a[10], "evidence_refs");
    }

    @Test
    @DisplayName("默认补齐: occurred_at/source_system/correlation_id/version 不为空")
    void publishFillsDefaults() {
        service.publish(new DomainEventService.EventEnvelope(
                "bom.changed", "BOM", "B-1", null, null, null, null, null, null));

        Object[] a = capturedArgs();
        assertNotNull(a[6], "occurred_at 默认当前时刻");
        assertEquals("PLM2", a[7], "source_system 默认 PLM2");
        assertNotNull(a[8], "correlation_id 不得为空");
        assertEquals(0, a[9], "version 默认 0");
        assertEquals("[]", a[10], "evidence_refs 默认空数组");
    }

    @Test
    @DisplayName("兼容旧调用方: aggregate 同时写入 object_ref")
    void legacyPublishStillWritesObjectRef() {
        service.publish("m04.project_node.updated", "PROJECT_NODE", "HJ001:MOLD", Map.of("status", "DONE"));

        Object[] a = capturedArgs();
        assertEquals("m04.project_node.updated", a[1]);
        assertEquals("PROJECT_NODE", a[4], "object_type 取 aggregate_type");
        assertEquals("HJ001:MOLD", a[5], "object_id 取 aggregate_id");
        assertEquals("PLM2", a[7]);
    }

    @Test
    @DisplayName("event_type 为空 → 直接丢弃, 不写脏事件")
    void dropEventWithoutType() {
        service.publish(new DomainEventService.EventEnvelope(
                "  ", "PART", "HJ-001", null, null, null, null, null, null));

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("写库失败不抛异常(事件不阻断业务)")
    void writeFailureIsSwallowed() {
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> service.publish(
                new DomainEventService.EventEnvelope("x.y", "X", "1", null, null, null, null, null, null)));
    }
}
