package com.hjgd.plm.ledger;

import com.hjgd.plm.ledger.service.LedgerObjectResolver;
import com.hjgd.plm.ledger.service.impl.LedgerObjectResolverImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 关联对象解析：AC-A4-5「目标对象失效时给出原因、不静默丢失」。
 */
@DisplayName("关联对象解析器")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LedgerObjectResolverTest {

    @Mock private JdbcTemplate jdbcTemplate;
    private LedgerObjectResolverImpl resolver;

    @BeforeEach
    void setUp() {
        resolver = new LedgerObjectResolverImpl(jdbcTemplate);
    }

    private Map<String, Object> row(String no, String name, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("object_no", no);
        m.put("object_name", name);
        m.put("object_status", status);
        return m;
    }

    @Test
    @DisplayName("注册表覆盖 7 类对象")
    void registryCoversSevenTypes() {
        assertEquals(7, resolver.supportedTypes().size());
        assertTrue(resolver.supportedTypes().containsAll(
                List.of("PROJECT", "REQUIREMENT", "MATERIAL", "BOM", "ECN", "TASK", "REVIEW")));
    }

    @Test
    @DisplayName("对象存在且状态正常 → valid=true，回填编号与名称")
    void resolveActiveObject() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(row("PJ-001", "投光灯项目", "ACTIVE")));

        LedgerObjectResolver.ObjectRef ref = resolver.resolve("PROJECT", "PJ-001");

        assertTrue(ref.valid());
        assertNull(ref.invalidReason());
        assertEquals("PJ-001", ref.objectNo());
        assertEquals("投光灯项目", ref.objectName());
    }

    @Test
    @DisplayName("对象不存在或已软删 → valid=false, 原因 DELETED")
    void resolveDeletedObject() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        LedgerObjectResolver.ObjectRef ref = resolver.resolve("MATERIAL", "HJ-404");

        assertFalse(ref.valid());
        assertEquals("DELETED", ref.invalidReason());
        assertEquals("HJ-404", ref.objectId());
    }

    @Test
    @DisplayName("对象状态为作废/取消 → valid=false, 原因 OBSOLETE（保留编号名称）")
    void resolveObsoleteObject() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(row("ECN-001", "变更单", "CANCELLED")));

        LedgerObjectResolver.ObjectRef ref = resolver.resolve("ECN", "ECN-001");

        assertFalse(ref.valid());
        assertEquals("OBSOLETE", ref.invalidReason());
        assertEquals("ECN-001", ref.objectNo());
    }

    @Test
    @DisplayName("未注册类型 / 空键 → 一律 DELETED，不查库")
    void unknownTypeIsInvalid() {
        assertFalse(resolver.resolve("UNKNOWN", "X").valid());
        assertFalse(resolver.resolve("PROJECT", "  ").valid());
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("目标模块查询异常 → 不抛出，标记 DELETED（AC-A3-8 不阻断主链路）")
    void queryFailureDegradesToInvalid() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenThrow(new RuntimeException("db down"));

        LedgerObjectResolver.ObjectRef ref = resolver.resolve("PROJECT", "PJ-001");

        assertFalse(ref.valid());
        assertEquals("DELETED", ref.invalidReason());
    }

    @Test
    @DisplayName("检索：命中编号或名称，返回有效候选")
    void searchReturnsCandidates() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("object_id", "PJ-001");
        m.put("object_no", "PJ-001");
        m.put("object_name", "投光灯项目");
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(m));

        List<LedgerObjectResolver.ObjectRef> found = resolver.search("PROJECT", "投光", 20);

        assertEquals(1, found.size());
        assertEquals("PJ-001", found.get(0).objectId());
        assertTrue(found.get(0).valid());
    }

    @Test
    @DisplayName("检索：未注册类型返回空集，不查库")
    void searchUnknownTypeEmpty() {
        assertTrue(resolver.search("NOPE", "x", 20).isEmpty());
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
    }
}
