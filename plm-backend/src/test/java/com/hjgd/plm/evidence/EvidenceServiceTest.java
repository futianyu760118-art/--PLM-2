package com.hjgd.plm.evidence;

import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.evidence.entity.PlmEvidence;
import com.hjgd.plm.evidence.mapper.PlmEvidenceMapper;
import com.hjgd.plm.evidence.service.EvidenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * F09 统一证据底座：四要素强制、可穿透、不可静默通过。
 */
@DisplayName("F09 统一证据底座")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvidenceServiceTest {

    @Mock private PlmEvidenceMapper evidenceMapper;
    @InjectMocks private EvidenceService service;

    private EvidenceService.RegisterCommand cmd(String subjectType, String subjectId, String type,
                                                 String version, BigDecimal confidence) {
        return new EvidenceService.RegisterCommand(subjectType, subjectId, type,
                "PLM2", "rec-1", "/files/1", null, version, confidence,
                null, null, "HJ001", null, null);
    }

    // ---------------------------------------------------------------- 四要素强制

    @Test
    @DisplayName("四要素齐备 → 登记成功, 自动补齐 checksum 与登记人")
    void registerCompleteEvidence() {
        PlmEvidence saved = service.register(cmd("node", "9", "file", "V1", BigDecimal.ONE));

        ArgumentCaptor<PlmEvidence> cap = ArgumentCaptor.forClass(PlmEvidence.class);
        verify(evidenceMapper).insert(cap.capture());
        PlmEvidence e = cap.getValue();
        assertNotNull(e.getEvidenceId());
        assertEquals("PLM2", e.getSourceSystem());
        assertEquals("V1", e.getVersion());
        assertEquals(BigDecimal.ONE, e.getConfidence());
        assertNotNull(e.getChecksum(), "checksum 由定位要素确定性推导");
        assertNotNull(e.getSubmittedAt());
    }

    @Test
    @DisplayName("缺 version → 拒绝(EVIDENCE_INVALID)")
    void rejectMissingVersion() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.register(cmd("node", "9", "file", "  ", BigDecimal.ONE)));
        assertTrue(ex.getMessage().contains("EVIDENCE_INVALID"));
        assertTrue(ex.getMessage().contains("version"));
        verify(evidenceMapper, never()).insert(any());
    }

    @Test
    @DisplayName("缺 confidence → 拒绝(不得静默通过)")
    void rejectMissingConfidence() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.register(cmd("node", "9", "file", "V1", null)));
        assertTrue(ex.getMessage().contains("confidence"));
        verify(evidenceMapper, never()).insert(any());
    }

    @Test
    @DisplayName("confidence 越界 → 拒绝")
    void rejectOutOfRangeConfidence() {
        assertThrows(BusinessException.class,
                () -> service.register(cmd("node", "9", "file", "V1", new BigDecimal("1.5"))));
        assertThrows(BusinessException.class,
                () -> service.register(cmd("node", "9", "file", "V1", new BigDecimal("-0.1"))));
        verify(evidenceMapper, never()).insert(any());
    }

    @Test
    @DisplayName("非法 evidence_type / subject_type → 拒绝")
    void rejectUnknownTypes() {
        assertThrows(BusinessException.class,
                () -> service.register(cmd("node", "9", "pdf", "V1", BigDecimal.ONE)));
        assertThrows(BusinessException.class,
                () -> service.register(cmd("unknown", "9", "file", "V1", BigDecimal.ONE)));
        verify(evidenceMapper, never()).insert(any());
    }

    @Test
    @DisplayName("checksum 确定性: 同一证据重复登记得到同一值")
    void checksumIsDeterministic() {
        EvidenceService.RegisterCommand c = cmd("node", "9", "file", "V1", BigDecimal.ONE);
        assertEquals(EvidenceService.deriveChecksum(c, "PLM2"),
                EvidenceService.deriveChecksum(c, "PLM2"));
        assertNotEquals(EvidenceService.deriveChecksum(c, "PLM2"),
                EvidenceService.deriveChecksum(cmd("node", "10", "file", "V1", BigDecimal.ONE), "PLM2"));
        assertEquals(64, EvidenceService.sha256Hex("x").length(), "SHA-256 十六进制长度");
    }

    @Test
    @DisplayName("调用方提供的 checksum 优先(不覆盖既有档案指纹)")
    void explicitChecksumWins() {
        EvidenceService.RegisterCommand c = new EvidenceService.RegisterCommand(
                "node", "9", "file", "PLM2", "rec-1", "/files/1",
                "deadbeef", "V1", BigDecimal.ONE, null, null, "HJ001", null, null);

        service.register(c);

        ArgumentCaptor<PlmEvidence> cap = ArgumentCaptor.forClass(PlmEvidence.class);
        verify(evidenceMapper).insert(cap.capture());
        assertEquals("deadbeef", cap.getValue().getChecksum());
    }

    // ---------------------------------------------------------------- 穿透链

    @Test
    @DisplayName("穿透查询: 全部证据指向原始记录 → 断点数 0, complete=true")
    void traceIsCompleteWhenAllLinked() {
        PlmEvidence e1 = ev("EV-1", "file", "file-1");
        PlmEvidence e2 = ev("EV-2", "record", "rec-2");
        when(evidenceMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(e1, e2)));

        Map<String, Object> trace = service.trace("node", "9");

        assertEquals(2, trace.get("evidenceCount"));
        assertEquals(0, trace.get("brokenCount"));
        assertEquals(true, trace.get("complete"));
        assertEquals(2, ((List<?>) trace.get("links")).size());
    }

    @Test
    @DisplayName("穿透查询: 证据未指向原始记录 → 记断点, complete=false")
    void traceReportsBrokenLinks() {
        PlmEvidence good = ev("EV-1", "file", "file-1");
        PlmEvidence broken = ev("EV-2", "record", null);
        when(evidenceMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(good, broken)));

        Map<String, Object> trace = service.trace("node", "9");

        assertEquals(1, trace.get("brokenCount"));
        assertEquals(false, trace.get("complete"));
        assertEquals("EV-2", ((Map<?, ?>) ((List<?>) trace.get("brokenLinks")).get(0)).get("evidenceId"));
    }

    @Test
    @DisplayName("穿透查询: 无证据 → complete=false(不能凭「查无问题」判定齐备)")
    void traceWithoutEvidenceIsNotComplete() {
        when(evidenceMapper.selectList(any())).thenReturn(new ArrayList<>());

        Map<String, Object> trace = service.trace("node", "9");

        assertEquals(0, trace.get("evidenceCount"));
        assertEquals(false, trace.get("complete"));
    }

    @Test
    @DisplayName("证据条数统计")
    void countBySubject() {
        when(evidenceMapper.selectCount(any())).thenReturn(3L);
        assertEquals(3L, service.countBySubject("node", "9"));

        when(evidenceMapper.selectCount(any())).thenReturn(null);
        assertEquals(0L, service.countBySubject("node", "9"));
    }

    private PlmEvidence ev(String id, String type, String sourceRecordId) {
        PlmEvidence e = new PlmEvidence();
        e.setEvidenceId(id);
        e.setSubjectType("node");
        e.setSubjectId("9");
        e.setEvidenceType(type);
        e.setSourceRecordId(sourceRecordId);
        e.setSourceSystem("PLM2");
        e.setVersion("V1");
        e.setChecksum("sum-" + id);
        e.setConfidence(BigDecimal.ONE);
        return e;
    }
}
