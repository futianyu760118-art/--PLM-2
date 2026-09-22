package com.hjgd.plm.bom;

import com.hjgd.plm.bom.entity.Bom;
import com.hjgd.plm.bom.entity.BomVersion;
import com.hjgd.plm.bom.mapper.BomItemMapper;
import com.hjgd.plm.bom.mapper.BomMapper;
import com.hjgd.plm.bom.mapper.BomVersionMapper;
import com.hjgd.plm.bom.service.impl.BomServiceImpl;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.material.service.MaterialService;
import com.hjgd.plm.system.service.SequenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Step 3 ECN 升版：bumpVersionForEcn 直接测试。
 *
 * 行为：
 *  - 优先找 rootPartNo 下状态 RELEASED 的 EBOM
 *  - 找不到则找任意版本；都没有则返回 null(不抛异常)
 *  - 找到后写版本快照 + 设置 ecnNo + 设置 status=CHANGING + 更新 versionNo
 *  - 返回旧版本号(用于 ECN.versionBefore)
 */
@DisplayName("BOM 升版(ECN)")
@ExtendWith(MockitoExtension.class)
class BomBumpVersionForEcnTest {

    @Mock private BomMapper bomMapper;
    @Mock private BomItemMapper bomItemMapper;
    @Mock private BomVersionMapper bomVersionMapper;
    @Mock private com.hjgd.plm.bom.mapper.BomTemplateMapper bomTemplateMapper;
    @Mock private SequenceService sequenceService;
    @Mock private MaterialService materialService;
    @Mock private ObjectMapper objectMapper;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private com.hjgd.plm.lifecycle.service.LifecycleService lifecycleService;

    private BomServiceImpl bomService;

    @BeforeEach
    void setUp() {
        bomService = new BomServiceImpl(bomMapper, bomItemMapper, bomVersionMapper, bomTemplateMapper,
                sequenceService, materialService, objectMapper, jdbcTemplate, lifecycleService);
        com.hjgd.plm.auth.security.LoginUser mockUser = mock(com.hjgd.plm.auth.security.LoginUser.class);
        lenient().when(mockUser.getRealName()).thenReturn("测试工程师");
        lenient().when(mockUser.getUserId()).thenReturn(1L);
        lenient().when(mockUser.getUsername()).thenReturn("engineer");
        lenient().when(mockUser.getPrimaryRole()).thenReturn("ENGINEER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(mockUser, null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("happy path: RELEASED EBOM 升版→ CHANGING, 返回旧版本号")
    void releasedBomBumpsVersion() throws Exception {
        Bom released = new Bom();
        released.setId(10L);
        released.setBomNo("BOM202601010001");
        released.setRootPartNo("HJ001");
        released.setBomType("EBOM");
        released.setStatus("RELEASED");
        released.setVersionNo("V1.0");
        when(bomMapper.selectOne(any())).thenReturn(released);
        // saveVersionSnapshot 内需 ObjectMapper.writeValueAsString 生成 JSON
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        String oldVersion = bomService.bumpVersionForEcn("HJ001", "ECN202601010001", "V1.1");

        assertEquals("V1.0", oldVersion);
        ArgumentCaptor<Bom> captor = ArgumentCaptor.forClass(Bom.class);
        verify(bomMapper).updateById(captor.capture());
        Bom updated = captor.getValue();
        assertEquals("CHANGING", updated.getStatus());
        assertEquals("V1.1", updated.getVersionNo());
        assertEquals("ECN202601010001", updated.getEcnNo());
        verify(bomVersionMapper, times(1)).insert(any(BomVersion.class));
    }

    @Test
    @DisplayName("无 RELEASED 但有任意 BOM → 升版")
    void fallbackToAnyBom() throws Exception {
        Bom draft = new Bom();
        draft.setId(20L);
        draft.setRootPartNo("HJ002");
        draft.setBomType("EBOM");
        draft.setStatus("DRAFT");
        draft.setVersionNo("V0.5");
        when(bomMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(draft);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        String oldVersion = bomService.bumpVersionForEcn("HJ002", "ECN202601010002", null);

        assertEquals("V0.5", oldVersion);
        ArgumentCaptor<Bom> captor = ArgumentCaptor.forClass(Bom.class);
        verify(bomMapper).updateById(captor.capture());
        Bom updated = captor.getValue();
        assertEquals("CHANGING", updated.getStatus());
        // 未传 newVersionNo → 保持原版本 V0.5
        assertEquals("V0.5", updated.getVersionNo());
        assertEquals("ECN202601010002", updated.getEcnNo());
        verify(bomVersionMapper, times(1)).insert(any(BomVersion.class));
    }

    @Test
    @DisplayName("无 BOM 时返回 null, 不抛异常")
    void noBomReturnsNull() {
        when(bomMapper.selectOne(any())).thenReturn(null);

        String oldVersion = bomService.bumpVersionForEcn("UNKNOWN", "ECN202601010003", "V1.0");

        assertNull(oldVersion);
        verify(bomMapper, never()).updateById(any(Bom.class));
        verify(bomVersionMapper, never()).insert(any(BomVersion.class));
    }

    @Test
    @DisplayName("快照保存包含 ECN 号与版本号")
    void snapshotRecordsEcnNo() throws Exception {
        Bom released = new Bom();
        released.setId(30L);
        released.setBomNo("BOM-X");
        released.setRootPartNo("HJ003");
        released.setBomType("EBOM");
        released.setStatus("RELEASED");
        released.setVersionNo("V2.0");
        when(bomMapper.selectOne(any())).thenReturn(released);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        bomService.bumpVersionForEcn("HJ003", "ECN-X", "V3.0");

        ArgumentCaptor<BomVersion> cap = ArgumentCaptor.forClass(BomVersion.class);
        verify(bomVersionMapper).insert(cap.capture());
        BomVersion snap = cap.getValue();
        assertEquals(30L, snap.getBomId());
        assertEquals("ECN-X", snap.getEcnNo());
        assertEquals("V2.0", snap.getVersionNo());
    }
}