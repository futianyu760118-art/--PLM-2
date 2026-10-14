package com.hjgd.plm.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.ledger.entity.PlmNotification;
import com.hjgd.plm.ledger.entity.PlmNotificationSetting;
import com.hjgd.plm.ledger.mapper.PlmNotificationMapper;
import com.hjgd.plm.ledger.mapper.PlmNotificationSettingMapper;
import com.hjgd.plm.ledger.service.LedgerNotifyService;
import com.hjgd.plm.ledger.service.impl.LedgerNotifyServiceImpl;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 站内通知：AC-A4-8（内容含对象/字段/前后值/操作人/时间/链接）、
 * AC-A4-10（5 分钟窗口聚合）、AC-A4-11（按类型开关）。
 */
@DisplayName("站内通知（能力线 A）")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LedgerNotifyServiceTest {

    @Mock private PlmNotificationMapper notificationMapper;
    @Mock private PlmNotificationSettingMapper settingMapper;

    private LedgerNotifyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LedgerNotifyServiceImpl(notificationMapper, settingMapper,
                LedgerTestSupport.props(), new ObjectMapper());
        LedgerTestSupport.login();
        when(settingMapper.selectOne(any())).thenReturn(null); // 未配置 = 开启
    }

    @AfterEach
    void tearDown() {
        LedgerTestSupport.logout();
    }

    private Map<String, Object> change(String field, Object before, Object after) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("field", field);
        row.put("before", before);
        row.put("after", after);
        return row;
    }

    @Test
    @DisplayName("AC-A4-8 通知内容含前后值/操作人/时间/链接")
    void notificationCarriesFullContext() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("ledgerNo", "EV-20261014-0001");
        extra.put("link", "/ledger/list?keyword=EV-20261014-0001");

        service.notify(100L, LedgerNotifyService.TYPE_LEDGER_CHANGED, "证据变更",
                null, null, List.of(change("remark", "旧备注", "新备注")), extra);

        ArgumentCaptor<PlmNotification> cap = ArgumentCaptor.forClass(PlmNotification.class);
        verify(notificationMapper).insert(cap.capture());
        PlmNotification saved = cap.getValue();
        assertEquals(100L, saved.getUserId());
        assertEquals("LEDGER_CHANGED", saved.getNotifyType());
        assertEquals(0, saved.getReadStatus());
        assertNotNull(saved.getAggKey());
        assertTrue(saved.getContentJson().contains("旧备注"));
        assertTrue(saved.getContentJson().contains("新备注"));
        assertTrue(saved.getContentJson().contains("EV-20261014-0001"));
        assertTrue(saved.getContentJson().contains("张三"), "操作人应落库");
    }

    @Test
    @DisplayName("AC-A4-10 同窗口同对象二次变更合并进同一行（update 而非 insert）")
    void sameWindowMergesIntoOneRow() throws Exception {
        PlmNotification existing = new PlmNotification();
        existing.setId(9L);
        existing.setUserId(100L);
        existing.setNotifyType("LEDGER_CHANGED");
        existing.setContentJson(new ObjectMapper().writeValueAsString(
                Map.of("changes", List.of(change("remark", "A", "B")))));
        existing.setAggKey("100|LEDGER_CHANGED|||<bucket>");
        when(notificationMapper.selectOne(any())).thenReturn(existing);

        service.notify(100L, LedgerNotifyService.TYPE_LEDGER_CHANGED, "证据变更",
                null, null, List.of(change("version", "V1.0", "V1.1")), Map.of());

        verify(notificationMapper).updateById(any(PlmNotification.class));
        verify(notificationMapper, never()).insert(any(PlmNotification.class));
        ArgumentCaptor<PlmNotification> cap = ArgumentCaptor.forClass(PlmNotification.class);
        verify(notificationMapper).updateById(cap.capture());
        String json = cap.getValue().getContentJson();
        assertTrue(json.contains("\"A\""), "旧明细保留");
        assertTrue(json.contains("V1.1"), "新明细追加");
    }

    @Test
    @DisplayName("AC-A4-10 聚合键含 5 分钟窗口起点，跨窗口不复用")
    void aggKeyContainsWindowBucket() {
        service.notify(100L, "LEDGER_CHANGED", "t", "PROJECT", "PJ-001", List.of(), Map.of());

        ArgumentCaptor<PlmNotification> cap = ArgumentCaptor.forClass(PlmNotification.class);
        verify(notificationMapper).insert(cap.capture());
        String aggKey = cap.getValue().getAggKey();
        String[] parts = aggKey.split("\\|");
        assertEquals(5, parts.length);
        assertEquals("100", parts[0]);
        assertEquals("PROJECT", parts[2]);
        long bucket = Long.parseLong(parts[4]);
        assertEquals(0, bucket % (5 * 60_000L), "窗口起点应为 5 分钟整数倍");
    }

    @Test
    @DisplayName("AC-A4-11 类型开关关闭 → 不写入通知")
    void disabledTypeSkipsWrite() {
        PlmNotificationSetting off = new PlmNotificationSetting();
        off.setUserId(100L);
        off.setNotifyType("LEDGER_CHANGED");
        off.setEnabled(0);
        when(settingMapper.selectOne(any())).thenReturn(off);

        service.notify(100L, "LEDGER_CHANGED", "t", null, null, List.of(), Map.of());

        verify(notificationMapper, never()).insert(any(PlmNotification.class));
    }

    @Test
    @DisplayName("开关切换：已配置则更新，未配置则新增")
    void setEnabledUpserts() {
        when(settingMapper.selectOne(any())).thenReturn(null);
        service.setEnabled(100L, "OBJ_INVALID", false);
        ArgumentCaptor<PlmNotificationSetting> created = ArgumentCaptor.forClass(PlmNotificationSetting.class);
        verify(settingMapper).insert(created.capture());
        assertEquals(0, created.getValue().getEnabled());

        PlmNotificationSetting existing = new PlmNotificationSetting();
        existing.setId(3L);
        existing.setUserId(100L);
        existing.setNotifyType("OBJ_INVALID");
        existing.setEnabled(0);
        when(settingMapper.selectOne(any())).thenReturn(existing);
        service.setEnabled(100L, "OBJ_INVALID", true);
        assertEquals(1, existing.getEnabled());
        verify(settingMapper).updateById(existing);
    }

    @Test
    @DisplayName("AC-A4-11 开关列表补齐未配置类型（默认开启）")
    void settingsFillsMissingTypes() {
        when(settingMapper.selectList(any())).thenReturn(new ArrayList<>());

        List<PlmNotificationSetting> list = service.settings(100L);

        // 取值域就是接口上公开的类型常量，按 allTypes() 的顺序全量列出。
        // 不写死条数：新增能力点向取值域里加类型时，这里应同步补上常量而不是改一个数字。
        List<String> expected = List.of(
                LedgerNotifyService.TYPE_LEDGER_CREATED, LedgerNotifyService.TYPE_LEDGER_CHANGED,
                LedgerNotifyService.TYPE_LEDGER_DELETED, LedgerNotifyService.TYPE_LEDGER_LINK,
                LedgerNotifyService.TYPE_OBJ_INVALID,
                LedgerNotifyService.TYPE_SUB_ITEM, LedgerNotifyService.TYPE_SUB_REPORT);
        assertEquals(expected, list.stream().map(PlmNotificationSetting::getNotifyType).toList());
        for (PlmNotificationSetting s : list) {
            assertEquals(1, s.getEnabled());
            assertEquals(100L, s.getUserId());
        }
    }

    @Test
    @DisplayName("群发通知跳过操作人自己")
    void notifyAllSkipsSelf() {
        service.notifyAll(List.of(LedgerTestSupport.USER_ID, 200L), "LEDGER_LINK",
                "新增关联", "PROJECT", "PJ-001", List.of(change("link", null, "PJ-001")), Map.of());

        ArgumentCaptor<PlmNotification> cap = ArgumentCaptor.forClass(PlmNotification.class);
        verify(notificationMapper, times(1)).insert(cap.capture());
        assertEquals(200L, cap.getValue().getUserId());
    }

    @Test
    @DisplayName("空接收人不写库")
    void emptyRecipientsNoop() {
        service.notifyAll(List.of(), "LEDGER_LINK", "t", null, null, List.of(), Map.of());
        service.notify(null, "LEDGER_LINK", "t", null, null, List.of(), Map.of());
        verify(notificationMapper, never()).insert(any(PlmNotification.class));
    }
}
