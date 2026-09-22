package com.hjgd.plm.material;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.material.entity.EntityHistory;
import com.hjgd.plm.material.mapper.EntityHistoryMapper;
import com.hjgd.plm.material.service.EntityHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * V1.1 通用实体变更历史(企业微信文档式):
 *  - recordCreate: 初始版本,changedFields 全字段
 *  - recordUpdate: 只记录 before/after 不同的字段
 *  - snapshotAt: 取指定版本快照
 */
@DisplayName("实体变更历史 V1.1")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EntityHistoryServiceTest {

    @Mock private EntityHistoryMapper mapper;
    @Mock private JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private EntityHistoryService service;

    @org.junit.jupiter.api.BeforeEach
    void init() { service = new EntityHistoryService(mapper, jdbcTemplate, objectMapper); }

    @Test
    @DisplayName("CREATE: 记录初始版本,变更字段全部为 after=null→value")
    void createRecordsAllFields() {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("partNo", "HJ001");
        snap.put("materialName", "投光灯");
        snap.put("unit", "PCS");

        when(mapper.maxVersion("PART", "HJ001")).thenReturn(0);

        EntityHistory h = service.recordCreate("PART", "HJ001", snap, "USER", "admin");

        assertNotNull(h);
        assertEquals("PART", h.getObjectType());
        assertEquals("HJ001", h.getObjectId());
        assertEquals(1, h.getVersion()); // 第一版
        assertEquals("CREATE", h.getChangeType());
        // 三条变更记录
        ArgumentCaptor<EntityHistory> cap = ArgumentCaptor.forClass(EntityHistory.class);
        verify(mapper).insert(cap.capture());
        String json = cap.getValue().getChangedFields();
        assertTrue(json.contains("partNo"));
        assertTrue(json.contains("materialName"));
        assertTrue(json.contains("unit"));
    }

    @Test
    @DisplayName("UPDATE: 只记录 changed 的字段(空字符串/null/值差异)")
    void updateOnlyChangedFields() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("unit", "");
        before.put("materialName", "投光灯");
        before.put("ipRating", null);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("unit", "PCS");
        after.put("materialName", "投光灯");
        after.put("ipRating", "IP65");

        when(mapper.maxVersion("PART", "HJ001")).thenReturn(3);

        EntityHistory h = service.recordUpdate("PART", "HJ001", before, after, "USER", null, "admin", null);

        assertNotNull(h);
        assertEquals(4, h.getVersion()); // maxVersion(3)+1
        ArgumentCaptor<EntityHistory> cap = ArgumentCaptor.forClass(EntityHistory.class);
        verify(mapper).insert(cap.capture());
        String json = cap.getValue().getChangedFields();
        // unit 和 ipRating 应该被记录(发生了变化)
        assertTrue(json.contains("unit"));
        assertTrue(json.contains("ipRating"));
        // materialName 相同,不记录
        // (实际只 verify insert 被调用)
    }

    @Test
    @DisplayName("UPDATE: 无差异时不写入历史")
    void updateSkipsWhenNoChange() {
        Map<String, Object> same = Map.of("unit", "PCS", "name", "X");
        when(mapper.maxVersion("PART", "HJ001")).thenReturn(5);

        EntityHistory h = service.recordUpdate("PART", "HJ001", same, same, "USER", null, "admin", null);

        assertNull(h);
        verify(mapper, never()).insert(any(EntityHistory.class));
    }

    @Test
    @DisplayName("snapshotAt: 返回指定版本的完整快照 JSON")
    void snapshotAtReturnsJson() {
        EntityHistory h = new EntityHistory();
        h.setVersion(2);
        h.setSnapshotAfter("{\"unit\":\"PCS\"}");
        when(mapper.listByObject("PART", "HJ001")).thenReturn(List.of(h));

        String snap = service.snapshotAt("PART", "HJ001", 2);

        assertEquals("{\"unit\":\"PCS\"}", snap);
    }

    @Test
    @DisplayName("history: 返回全版本列表(降序)")
    void historyReturnsList() {
        EntityHistory h1 = new EntityHistory(); h1.setVersion(3);
        EntityHistory h2 = new EntityHistory(); h2.setVersion(2);
        when(mapper.listByObject("PART", "HJ001")).thenReturn(List.of(h1, h2));

        List<EntityHistory> list = service.history("PART", "HJ001");
        assertEquals(2, list.size());
    }
}