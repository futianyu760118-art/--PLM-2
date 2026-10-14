package com.hjgd.plm.ledger;

import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.ledger.entity.PlmLedger;
import com.hjgd.plm.ledger.mapper.PlmLedgerMapper;
import com.hjgd.plm.ledger.service.LedgerAuditService;
import com.hjgd.plm.ledger.service.LedgerNotifyService;
import com.hjgd.plm.ledger.service.LedgerSyncService;
import com.hjgd.plm.ledger.service.impl.LedgerSyncServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 双向同步：AC-A3-7（后写优先 + 通知被覆盖方）、AC-A3-8（重试与失败日志）、
 * AC-A3-3（模块侧回写逐字段合并）。
 */
@DisplayName("信息资料双向同步（能力线 A）")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LedgerSyncServiceTest {

    @Mock private PlmLedgerMapper ledgerMapper;
    @Mock private LedgerAuditService auditService;
    @Mock private LedgerNotifyService notifyService;

    private LedgerSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service = new LedgerSyncServiceImpl(ledgerMapper, auditService, notifyService,
                LedgerTestSupport.props(), txManager, List.of());
        LedgerTestSupport.login("ledger:edit");
    }

    @AfterEach
    void tearDown() {
        LedgerTestSupport.logout();
    }

    @Test
    @DisplayName("AC-A3-3 模块侧回写：只改动值不同的字段，其余字段不动")
    void appliesOnlyChangedFields() {
        PlmLedger ledger = LedgerTestSupport.ledger(1L, "SUBMITTED");
        ledger.setName("旧名称");
        ledger.setOccurDate(null);
        ledger.setLastModifiedBy("李四");
        ledger.setLastModifiedAt(LocalDateTime.now().minusHours(3));
        when(ledgerMapper.selectByObject("MATERIAL", "HJ-001")).thenReturn(List.of(ledger));

        LedgerSyncService.SyncResult result = service.applyFromModule("MATERIAL", "HJ-001",
                Map.of("name", "新名称", "type", "INSPECT_REPORT"), "MES");

        assertEquals(1, result.applied());
        assertEquals(List.of("name"), result.fields());
        assertEquals("新名称", ledger.getName());
        assertEquals("INSPECT_REPORT", ledger.getType());
        assertEquals(1, ledger.getRevision());
        verify(ledgerMapper).updateById(ledger);
        verify(auditService).log(eq("SYNC_FROM_MES"), eq("EV-20261014-0001"), any(), any());
    }

    @Test
    @DisplayName("AC-A3-7 覆盖另一方刚写入的值 → 标记冲突并通知被覆盖方")
    void lastWriteWinsAndNotifiesOverwrittenSide() {
        PlmLedger ledger = LedgerTestSupport.ledger(1L, "SUBMITTED");
        ledger.setName("台账侧刚改的名");
        ledger.setLastModifiedBy("李四");           // 另一方
        ledger.setLastModifiedAt(LocalDateTime.now().minusMinutes(1)); // 窗口内
        when(ledgerMapper.selectByObject("PROJECT", "PJ-001")).thenReturn(List.of(ledger));

        LedgerSyncService.SyncResult result = service.applyFromModule("PROJECT", "PJ-001",
                Map.of("name", "模块侧新值"), "BOM");

        assertTrue(result.conflict());
        ArgumentCaptor<Map<String, Object>> extra = ArgumentCaptor.forClass(Map.class);
        verify(notifyService).notify(eq(LedgerTestSupport.USER_ID),
                eq(LedgerNotifyService.TYPE_LEDGER_CHANGED), contains("覆盖"), eq("PROJECT"), eq("PJ-001"),
                anyList(), extra.capture());
        assertEquals(Boolean.TRUE, extra.getValue().get("overwritten"));
    }

    @Test
    @DisplayName("AC-A3-7 窗口外的旧值被覆盖不视为冲突（不打扰被覆盖方）")
    void staleWriteIsNotConflict() {
        PlmLedger ledger = LedgerTestSupport.ledger(1L, "SUBMITTED");
        ledger.setName("很久以前的值");
        ledger.setLastModifiedBy("李四");
        ledger.setLastModifiedAt(LocalDateTime.now().minusHours(2));
        when(ledgerMapper.selectByObject("PROJECT", "PJ-001")).thenReturn(List.of(ledger));

        LedgerSyncService.SyncResult result = service.applyFromModule("PROJECT", "PJ-001",
                Map.of("name", "新值"), "BOM");

        assertFalse(result.conflict());
        verify(notifyService, never()).notify(any(), anyString(), anyString(), any(), any(), anyList(), any());
    }

    @Test
    @DisplayName("AC-A3 未关联任何证据的对象 → 空结果，不写库")
    void noLinkedLedgerIsNoop() {
        when(ledgerMapper.selectByObject("PROJECT", "PJ-404")).thenReturn(List.of());

        LedgerSyncService.SyncResult result = service.applyFromModule("PROJECT", "PJ-404",
                Map.of("name", "x"), "MES");

        assertNull(result.ledgerId());
        assertEquals(0, result.applied());
        verify(ledgerMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("AC-A3-8 首次失败后重试成功")
    void retriesThenSucceeds() {
        PlmLedger ledger = LedgerTestSupport.ledger(1L, "SUBMITTED");
        when(ledgerMapper.selectByObject("PROJECT", "PJ-001"))
                .thenThrow(new RuntimeException("瞬时故障"))
                .thenReturn(List.of(ledger));

        LedgerSyncService.SyncResult result = service.applyFromModule("PROJECT", "PJ-001",
                Map.of("remark", "重试后成功"), "MES");

        assertEquals("EV-20261014-0001", result.ledgerNo());
        verify(ledgerMapper, times(1)).updateById(ledger);
        verify(auditService, never()).logFailure(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("AC-A3-8 重试耗尽 → 落失败日志并抛错")
    void exhaustedRetryLogsFailure() {
        when(ledgerMapper.selectByObject("PROJECT", "PJ-001")).thenThrow(new RuntimeException("持续故障"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.applyFromModule("PROJECT", "PJ-001", Map.of("name", "x"), "MES"));

        assertTrue(e.getMessage().contains("持续故障"));
        verify(auditService).logFailure(eq("SYNC_FAILED"), eq("PJ-001"), contains("持续故障"));
        verify(ledgerMapper, times(3)).selectByObject("PROJECT", "PJ-001");
    }

    @Test
    @DisplayName("无白名单字段可同步 → 直接拒绝")
    void rejectsNonLedgerFields() {
        assertThrows(BusinessException.class, () -> service.applyFromModule("PROJECT", "PJ-001",
                Map.of("partNo", "HJ-001"), "MES"));
        verify(ledgerMapper, never()).selectByObject(anyString(), anyString());
    }

    @Test
    @DisplayName("空对象类型/编号 → 拒绝")
    void rejectsBlankTarget() {
        assertThrows(BusinessException.class,
                () -> service.applyFromModule("  ", "PJ-001", Map.of("name", "x"), "MES"));
    }
}
