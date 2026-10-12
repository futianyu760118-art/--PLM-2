package com.hjgd.plm.lifecycle;

import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.lifecycle.service.impl.LifecycleServiceImpl;
import com.hjgd.plm.material.enums.MaterialStatus;
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

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * F04 生命周期非法跳转留痕。
 *
 * <p>修复前只写成功迁移，非法跳转在库里查无此事，「连续 3 次非法跳转」无法验证；
 * 现在被拒绝的迁移同样落 {@code result='DENIED'} 记录，且拒绝结果不受留痕失败影响。
 *
 * <p>注意：{@code to_state} 与 {@code object_id} 必须允许为空 —— 被拒绝的迁移没有目标状态，
 * 旧调用方也拿不到对象标识。DDL 侧由 {@code database/42_m04_autonomy_slice2.sql} 放开约束，
 * 本测试锁定的是「留痕内容契约」（哪个参数放 NULL、哪个必须带上）。
 */
@DisplayName("F04 生命周期非法跳转留痕")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LifecycleServiceImplTest {

    @Mock private JdbcTemplate jdbcTemplate;
    private LifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(new ArrayList<>());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        service = new LifecycleServiceImpl(jdbcTemplate);
        // @PostConstruct 在单元测试中不会自动触发；空查询结果 → 走内置迁移矩阵
        service.init();
    }

    private Object[] capturedHistoryInsert() {
        ArgumentCaptor<Object[]> cap = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), cap.capture());
        return cap.getValue();
    }

    private String capturedSql() {
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(cap.capture(), any(Object[].class));
        return cap.getValue();
    }

    @Test
    @DisplayName("合法迁移: 返回目标状态, 不留 DENIED 记录")
    void legalTransitionResolvesWithoutAudit() {
        assertEquals(MaterialStatus.REVIEWING,
                service.resolveToState("PART", MaterialStatus.DRAFT, "submit_review"));

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("非法跳转: 抛 LIFECYCLE_DENIED, 同时落一条 DENIED 留痕")
    void illegalTransitionIsAudited() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resolveToState("PART", MaterialStatus.DRAFT, "obsolete"));

        assertTrue(ex.getMessage().contains("LIFECYCLE_DENIED"));
        assertTrue(capturedSql().contains("'DENIED'"));
        assertTrue(capturedSql().contains("plm_lifecycle_history"));
    }

    @Test
    @DisplayName("DENIED 留痕契约: to_state 必须为 NULL(无目标状态), from_state 保留原状态")
    void deniedAuditLeavesToStateNull() {
        assertThrows(BusinessException.class,
                () -> service.resolveToState("PART", "HJ001", MaterialStatus.DRAFT, "obsolete"));

        Object[] a = capturedHistoryInsert();
        assertEquals("PART", a[0], "object_type");
        assertEquals("HJ001", a[1], "object_id 由 4 参重载带入");
        assertEquals("DRAFT", a[2], "from_state 保留拒绝时的原状态");
        assertNull(a[3], "to_state 无目标状态 → NULL(约束已放开)");
        assertEquals("obsolete", a[4], "action_code 保留被拒的动作");
    }

    @Test
    @DisplayName("旧 3 参调用方: object_id 为 NULL 也要留得下痕")
    void deniedAuditToleratesMissingObjectId() {
        assertThrows(BusinessException.class,
                () -> service.resolveToState("PART", MaterialStatus.DRAFT, "obsolete"));

        assertNull(capturedHistoryInsert()[1], "object_id 缺省为 NULL");
    }

    @Test
    @DisplayName("action_code 为 NULL 时填空串, 不因 NOT NULL 丢掉整条留痕")
    void deniedAuditNeverLosesRowToNotNullActionCode() {
        assertThrows(BusinessException.class,
                () -> service.resolveToState("PART", MaterialStatus.DRAFT, null));

        Object[] a = capturedHistoryInsert();
        assertNotNull(a[4], "action_code 不可为 NULL");
        assertEquals("", a[4]);
    }

    @Test
    @DisplayName("不支持的对象类型: 同样按非法跳转留痕")
    void unsupportedObjectTypeIsAudited() {
        assertThrows(BusinessException.class,
                () -> service.resolveToState("BOM", MaterialStatus.DRAFT, "release"));

        assertTrue(capturedSql().contains("'DENIED'"));
    }

    @Test
    @DisplayName("留痕写库失败不掩盖拒绝: 仍抛 LIFECYCLE_DENIED")
    void auditFailureDoesNotMaskDenial() {
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db down"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resolveToState("PART", MaterialStatus.DRAFT, "obsolete"));
        assertTrue(ex.getMessage().contains("LIFECYCLE_DENIED"));
    }

    @Test
    @DisplayName("assertTransition 与 resolveToState 判定一致(同一实现)")
    void assertTransitionSharesSingleImplementation() {
        assertDoesNotThrow(() ->
                service.assertTransition("PART", "HJ001", MaterialStatus.DRAFT, "submit_review"));
        assertThrows(BusinessException.class, () ->
                service.assertTransition("PART", "HJ001", MaterialStatus.DRAFT, "obsolete"));
    }
}
