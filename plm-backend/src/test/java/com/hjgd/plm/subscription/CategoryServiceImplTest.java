package com.hjgd.plm.subscription;

import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.ledger.dto.CategoryFilter;
import com.hjgd.plm.ledger.dto.CategoryFilterGroup;
import com.hjgd.plm.ledger.entity.PlmLedger;
import com.hjgd.plm.ledger.mapper.PlmLedgerMapper;
import com.hjgd.plm.ledger.service.LedgerAuditService;
import com.hjgd.plm.ledger.service.LedgerService;
import com.hjgd.plm.subscription.config.SubscriptionProperties;
import com.hjgd.plm.subscription.dto.CategoryDimRequest;
import com.hjgd.plm.subscription.dto.CategoryDimVO;
import com.hjgd.plm.subscription.dto.CategoryValueRequest;
import com.hjgd.plm.subscription.dto.LedgerCategoryAssignRequest;
import com.hjgd.plm.subscription.entity.PlmCategoryDim;
import com.hjgd.plm.subscription.entity.PlmCategoryValue;
import com.hjgd.plm.subscription.entity.PlmLedgerCategory;
import com.hjgd.plm.subscription.mapper.PlmCategoryDimMapper;
import com.hjgd.plm.subscription.mapper.PlmCategoryValueMapper;
import com.hjgd.plm.subscription.mapper.PlmLedgerCategoryMapper;
import com.hjgd.plm.subscription.service.impl.CategoryServiceImpl;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 资料分类维度与分类值（能力点 B1 分类、B2 权限边界）：
 * AC-B1-1/B1-3/B1-4/B1-5/B1-6/B1-7、AC-B2-1/B2-2/B2-5/B2-6。
 */
@DisplayName("资料分类（能力线 B）")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryServiceImplTest {

    @Mock private PlmCategoryDimMapper dimMapper;
    @Mock private PlmCategoryValueMapper valueMapper;
    @Mock private PlmLedgerCategoryMapper ledgerCategoryMapper;
    @Mock private PlmLedgerMapper ledgerMapper;
    @Mock private LedgerService ledgerService;
    @Mock private LedgerAuditService auditService;
    @Mock private JdbcTemplate jdbcTemplate;

    private SubscriptionProperties properties;
    private CategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = SubscriptionTestSupport.props();
        service = new CategoryServiceImpl(dimMapper, valueMapper, ledgerCategoryMapper, ledgerMapper,
                ledgerService, auditService, properties, jdbcTemplate);
        // 默认：资料上还没有任何标签。各用例需要「已有标签」时自行覆盖这一条存根。
        when(ledgerCategoryMapper.selectList(any())).thenReturn(new ArrayList<>());
    }

    @AfterEach
    void tearDown() {
        SubscriptionTestSupport.logout();
    }

    private PlmCategoryDim ownDim(Long id) {
        PlmCategoryDim dim = new PlmCategoryDim();
        dim.setId(id);
        dim.setDeptId(SubscriptionTestSupport.DEPT_ID);
        dim.setDimName("产品线");
        dim.setIsGlobal(0);
        dim.setIsShared(0);
        dim.setStatus("ENABLED");
        dim.setSortOrder(0);
        return dim;
    }

    private PlmCategoryValue value(Long id, Long dimId, String status) {
        PlmCategoryValue v = new PlmCategoryValue();
        v.setId(id);
        v.setDimId(dimId);
        v.setValueName("吸顶灯");
        v.setStatus(status);
        v.setSortOrder(0);
        return v;
    }

    // ===================== 维度维护（AC-B1） =====================

    @Test
    @DisplayName("AC-B1-2 单部门维度上限 10 个，超出拒绝")
    void dimLimitPerDept() {
        SubscriptionTestSupport.login("category:manage");
        properties.setMaxDimsPerDept(10);
        // 第 1 次 selectCount 是重名校验（0 表示不重名），第 2 次才是本部门维度计数
        when(dimMapper.selectCount(any())).thenReturn(0L, 10L);

        CategoryDimRequest req = new CategoryDimRequest();
        req.setDimName("新维度");
        BusinessException e = assertThrows(BusinessException.class, () -> service.createDim(req));
        assertTrue(e.getMessage().contains("10"));
        verify(dimMapper, never()).insert(any(PlmCategoryDim.class));
    }

    @Test
    @DisplayName("AC-B1-5 同部门维度重名被拒")
    void duplicateDimNameRejected() {
        SubscriptionTestSupport.login("category:manage");
        when(dimMapper.selectCount(any())).thenReturn(1L);

        CategoryDimRequest req = new CategoryDimRequest();
        req.setDimName("产品线");
        BusinessException e = assertThrows(BusinessException.class, () -> service.createDim(req));
        assertTrue(e.getMessage().contains("已存在"));
    }

    @Test
    @DisplayName("AC-B1-1 维度名超 20 字被拒；分类值超 30 字被拒")
    void nameLengthLimitsEnforced() {
        SubscriptionTestSupport.login("category:manage");
        when(dimMapper.selectCount(any())).thenReturn(0L);

        CategoryDimRequest longDim = new CategoryDimRequest();
        longDim.setDimName("x".repeat(properties.getDimNameMax() + 1));
        assertThrows(BusinessException.class, () -> service.createDim(longDim));

        CategoryDimRequest longValue = new CategoryDimRequest();
        longValue.setDimName("产品线");
        longValue.setValues(List.of("y".repeat(properties.getValueNameMax() + 1)));
        assertThrows(BusinessException.class, () -> service.createDim(longValue));
    }

    @Test
    @DisplayName("AC-B1-1 维度值逐项去空、去重、去首尾空白后落库")
    void valuesAreTrimmedAndDeduped() {
        SubscriptionTestSupport.login("category:manage");
        when(dimMapper.selectCount(any())).thenReturn(0L);
        when(valueMapper.selectCount(any())).thenReturn(0L);

        CategoryDimRequest req = new CategoryDimRequest();
        req.setDimName("  产品线  ");
        req.setValues(List.of(" 吸顶灯 ", "吸顶灯", "  ", "吊灯"));
        service.createDim(req);

        ArgumentCaptor<PlmCategoryDim> dimCap = ArgumentCaptor.forClass(PlmCategoryDim.class);
        verify(dimMapper).insert(dimCap.capture());
        assertEquals("产品线", dimCap.getValue().getDimName());

        ArgumentCaptor<PlmCategoryValue> valueCap = ArgumentCaptor.forClass(PlmCategoryValue.class);
        verify(valueMapper, times(2)).insert(valueCap.capture());
        List<String> names = valueCap.getAllValues().stream().map(PlmCategoryValue::getValueName).toList();
        assertEquals(List.of("吸顶灯", "吊灯"), names);
    }

    @Test
    @DisplayName("AC-B1-3 单维度分类值上限 50 个，超出拒绝")
    void valueLimitPerDim() {
        SubscriptionTestSupport.login("category:manage");
        properties.setMaxValuesPerDim(3);
        when(dimMapper.selectCount(any())).thenReturn(0L);

        CategoryDimRequest req = new CategoryDimRequest();
        req.setDimName("产品线");
        req.setValues(List.of("a", "b", "c", "d"));
        assertThrows(BusinessException.class, () -> service.createDim(req));
    }

    @Test
    @DisplayName("AC-B1-4 分类值只能停用不能删除；停用后不进入新建可选项")
    void valueStatusToggleOnly() {
        SubscriptionTestSupport.login("category:manage");
        when(dimMapper.selectById(1L)).thenReturn(ownDim(1L));
        when(valueMapper.selectById(11L)).thenReturn(value(11L, 1L, "ENABLED"));

        CategoryValueRequest req = new CategoryValueRequest();
        req.setStatus("disabled");
        service.updateValue(1L, 11L, req);

        assertEquals("DISABLED", valueMapper.selectById(11L).getStatus());
        verify(valueMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("非法分类值状态被拒")
    void invalidValueStatusRejected() {
        SubscriptionTestSupport.login("category:manage");
        when(dimMapper.selectById(1L)).thenReturn(ownDim(1L));
        when(valueMapper.selectById(11L)).thenReturn(value(11L, 1L, "ENABLED"));

        CategoryValueRequest req = new CategoryValueRequest();
        req.setStatus("ARCHIVED");
        BusinessException e = assertThrows(BusinessException.class, () -> service.updateValue(1L, 11L, req));
        assertTrue(e.getMessage().contains("非法状态"));
    }

    @Test
    @DisplayName("分类值必须属于该维度")
    void valueMustBelongToDim() {
        SubscriptionTestSupport.login("category:manage");
        when(dimMapper.selectById(1L)).thenReturn(ownDim(1L));
        when(valueMapper.selectById(11L)).thenReturn(value(11L, 2L, "ENABLED"));

        CategoryValueRequest req = new CategoryValueRequest();
        req.setStatus("DISABLED");
        assertThrows(BusinessException.class, () -> service.updateValue(1L, 11L, req));
    }

    // ===================== 删除维度（AC-B1-7） =====================

    @Test
    @DisplayName("AC-B1-7 删除维度需二次确认，并把「影响 N 条资料」带进提示")
    void deleteDimRequiresConfirmWithImpact() {
        SubscriptionTestSupport.login("category:manage");
        when(dimMapper.selectById(1L)).thenReturn(ownDim(1L));
        when(ledgerCategoryMapper.selectList(any())).thenReturn(List.of(tag(100L), tag(200L)));

        BusinessException e = assertThrows(BusinessException.class, () -> service.deleteDim(1L, false));
        assertTrue(e.getMessage().contains("2 条资料"), "提示里必须含影响条数: " + e.getMessage());
        verify(dimMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("AC-B1-7 确认后：摘标签 → 删值 → 删维度，全程留痕")
    void deleteDimCascadesAndAudits() {
        SubscriptionTestSupport.login("category:manage");
        when(dimMapper.selectById(1L)).thenReturn(ownDim(1L));
        PlmLedgerCategory tag = tag(100L);
        tag.setId(55L);
        when(ledgerCategoryMapper.selectList(any())).thenReturn(List.of(tag));
        PlmCategoryValue v = value(11L, 1L, "ENABLED");
        when(valueMapper.selectList(any())).thenReturn(List.of(v));

        service.deleteDim(1L, true);

        verify(ledgerCategoryMapper).deleteById(55L);
        verify(valueMapper).deleteById(11L);
        verify(dimMapper).deleteById(1L);
        verify(auditService).log(eq("CATEGORY_DIM_DELETE"), eq("产品线"), any(), any());
    }

    private PlmLedgerCategory tag(Long ledgerId) {
        PlmLedgerCategory tag = new PlmLedgerCategory();
        tag.setLedgerId(ledgerId);
        tag.setDimId(1L);
        tag.setValueId(11L);
        return tag;
    }

    // ===================== 跨部门 / 全局边界（AC-B2） =====================

    @Test
    @DisplayName("AC-B2-2 管理他部门维度被拒并留痕")
    void crossDeptDimDenied() {
        SubscriptionTestSupport.login("category:manage");
        PlmCategoryDim other = ownDim(1L);
        other.setDeptId(888L);
        when(dimMapper.selectById(1L)).thenReturn(other);

        assertThrows(BusinessException.class, () -> service.updateDim(1L, new CategoryDimRequest()));
        verify(auditService).logDenied(eq("DENIED_CATEGORY_CROSS_DEPT"), eq("产品线"), any());
    }

    @Test
    @DisplayName("AC-B2-6 全局维度对任何部门都只读")
    void globalDimReadOnly() {
        SubscriptionTestSupport.login("category:manage");
        PlmCategoryDim global = ownDim(1L);
        global.setDeptId(null);
        global.setIsGlobal(1);
        when(dimMapper.selectById(1L)).thenReturn(global);

        assertThrows(BusinessException.class, () -> service.updateDim(1L, new CategoryDimRequest()));
        verify(auditService).logDenied(eq("DENIED_CATEGORY_GLOBAL"), eq("产品线"), any());
    }

    @Test
    @DisplayName("AC-B2-1 无管理权的人看得到维度但看不到编辑入口")
    void manageListHidesEditEntryWithoutPermission() {
        SubscriptionTestSupport.login("category:view");
        when(dimMapper.selectList(any())).thenReturn(List.of(ownDim(1L)));
        when(valueMapper.selectList(any())).thenReturn(List.of());
        when(ledgerCategoryMapper.selectList(any())).thenReturn(new ArrayList<>());

        CategoryDimVO vo = service.listForManage().get(0);
        assertEquals(Boolean.FALSE, vo.getEditable());

        SubscriptionTestSupport.login("category:manage");
        assertEquals(Boolean.TRUE, service.listForManage().get(0).getEditable());
    }

    @Test
    @DisplayName("AC-B2-3 未归属部门的账号不能定义维度")
    void noDeptCannotCreateDim() {
        SubscriptionTestSupport.login("category:manage");
        com.hjgd.plm.auth.security.LoginUser user = com.hjgd.plm.auth.security.SecurityUtils.getCurrentUser();
        user.getUser().setDeptId(null);

        CategoryDimRequest req = new CategoryDimRequest();
        req.setDimName("产品线");
        assertThrows(BusinessException.class, () -> service.createDim(req));
    }

    @Test
    @DisplayName("AC-B2-5 共享维度他部门可用但不可改")
    void sharedDimUsableButNotEditable() {
        SubscriptionTestSupport.login("category:view", "ledger:edit");
        PlmCategoryDim shared = ownDim(1L);
        shared.setDeptId(888L);
        shared.setIsShared(1);
        when(dimMapper.selectById(1L)).thenReturn(shared);
        when(ledgerMapper.selectById(100L)).thenReturn(new PlmLedger());
        PlmCategoryValue v = value(11L, 1L, "ENABLED");
        when(valueMapper.selectById(11L)).thenReturn(v);
        when(ledgerCategoryMapper.selectOne(any())).thenReturn(null);

        LedgerCategoryAssignRequest.Item item = new LedgerCategoryAssignRequest.Item();
        item.setDimId(1L);
        item.setValueId(11L);
        LedgerCategoryAssignRequest req = new LedgerCategoryAssignRequest();
        req.setItems(List.of(item));

        assertDoesNotThrow(() -> service.assign(100L, req));
        verify(ledgerCategoryMapper).insert(any(PlmLedgerCategory.class));

        // 但改它的定义不行
        assertThrows(BusinessException.class, () -> service.updateDim(1L, new CategoryDimRequest()));
    }

    // ===================== 资料打标签（AC-B1-6） =====================

    @Test
    @DisplayName("AC-B1-6 一条资料同一维度只能有一个值，重复维度直接拒绝")
    void assignRejectsDuplicateDim() {
        SubscriptionTestSupport.login("ledger:edit");
        when(ledgerMapper.selectById(100L)).thenReturn(new PlmLedger());
        when(dimMapper.selectById(1L)).thenReturn(ownDim(1L));
        when(valueMapper.selectById(11L)).thenReturn(value(11L, 1L, "ENABLED"));
        when(valueMapper.selectById(12L)).thenReturn(value(12L, 1L, "ENABLED"));

        LedgerCategoryAssignRequest.Item a = new LedgerCategoryAssignRequest.Item();
        a.setDimId(1L);
        a.setValueId(11L);
        LedgerCategoryAssignRequest.Item b = new LedgerCategoryAssignRequest.Item();
        b.setDimId(1L);
        b.setValueId(12L);
        LedgerCategoryAssignRequest req = new LedgerCategoryAssignRequest();
        req.setItems(List.of(a, b));

        BusinessException e = assertThrows(BusinessException.class, () -> service.assign(100L, req));
        assertTrue(e.getMessage().contains("同一维度"));
        verify(ledgerCategoryMapper, never()).insert(any(PlmLedgerCategory.class));
    }

    @Test
    @DisplayName("AC-B1-6 已停用的分类值不能新打标签（但历史标签照常回显）")
    void assignRejectsDisabledValue() {
        SubscriptionTestSupport.login("ledger:edit");
        when(ledgerMapper.selectById(100L)).thenReturn(new PlmLedger());
        when(dimMapper.selectById(1L)).thenReturn(ownDim(1L));
        when(valueMapper.selectById(11L)).thenReturn(value(11L, 1L, "DISABLED"));
        when(ledgerCategoryMapper.selectOne(any())).thenReturn(null);

        LedgerCategoryAssignRequest.Item item = new LedgerCategoryAssignRequest.Item();
        item.setDimId(1L);
        item.setValueId(11L);
        LedgerCategoryAssignRequest req = new LedgerCategoryAssignRequest();
        req.setItems(List.of(item));

        BusinessException e = assertThrows(BusinessException.class, () -> service.assign(100L, req));
        assertTrue(e.getMessage().contains("已停用"));
    }

    @Test
    @DisplayName("AC-B1-6 valueId 传空表示清除该维度标签；已有标签被清掉")
    void assignWithNullValueClearsTag() {
        SubscriptionTestSupport.login("ledger:edit");
        when(ledgerMapper.selectById(100L)).thenReturn(new PlmLedger());
        when(dimMapper.selectById(1L)).thenReturn(ownDim(1L));
        PlmLedgerCategory existing = tag(100L);
        existing.setId(55L);
        when(ledgerCategoryMapper.selectOne(any())).thenReturn(existing);

        LedgerCategoryAssignRequest.Item item = new LedgerCategoryAssignRequest.Item();
        item.setDimId(1L);
        item.setValueId(null);
        LedgerCategoryAssignRequest req = new LedgerCategoryAssignRequest();
        req.setItems(List.of(item));

        service.assign(100L, req);

        verify(ledgerCategoryMapper).deleteById(55L);
        verify(ledgerCategoryMapper, never()).insert(any(PlmLedgerCategory.class));
    }

    @Test
    @DisplayName("打标签走台账同一套写权限判定（AC-B1-6 不越权改资料）")
    void assignChecksLedgerWritePermission() {
        SubscriptionTestSupport.login("ledger:edit");
        when(ledgerMapper.selectById(100L)).thenReturn(new PlmLedger());
        doThrow(new BusinessException(com.hjgd.plm.common.ResultCode.FORBIDDEN, "无权限"))
                .when(ledgerService).requireWritable(100L);

        LedgerCategoryAssignRequest req = new LedgerCategoryAssignRequest();
        req.setItems(List.of());
        assertThrows(BusinessException.class, () -> service.assign(100L, req));
    }

    // ===================== 订阅条件校验（AC-B2-4） =====================

    @Test
    @DisplayName("AC-B2-4 分类条件越界被拒；可见范围内的条件放行")
    void validateFilterScope() {
        SubscriptionTestSupport.login("subscription:manage");
        when(dimMapper.selectList(any())).thenReturn(List.of(ownDim(1L)));
        when(valueMapper.selectById(11L)).thenReturn(value(11L, 1L, "ENABLED"));

        CategoryFilter ok = new CategoryFilter();
        CategoryFilterGroup g = new CategoryFilterGroup();
        g.setDimId(1L);
        g.setValueIds(List.of(11L));
        ok.setDims(List.of(g));
        assertDoesNotThrow(() -> service.validateFilter(ok));

        CategoryFilter bad = new CategoryFilter();
        CategoryFilterGroup g2 = new CategoryFilterGroup();
        g2.setDimId(999L);
        g2.setValueIds(List.of(11L));
        bad.setDims(List.of(g2));
        BusinessException e = assertThrows(BusinessException.class, () -> service.validateFilter(bad));
        assertTrue(e.getMessage().contains("不可见"));
    }

    @Test
    @DisplayName("空分类条件不校验（周期订阅表示本部门全部资料）")
    void emptyFilterSkipsValidation() {
        SubscriptionTestSupport.login("subscription:manage");
        assertDoesNotThrow(() -> service.validateFilter(null));
        assertDoesNotThrow(() -> service.validateFilter(new CategoryFilter()));
    }

    @Test
    @DisplayName("分类条件生成人可读文案：同维度内用「/」、跨维度用「且」")
    void describeFilterIsHumanReadable() {
        SubscriptionTestSupport.login("subscription:manage");
        when(dimMapper.selectById(1L)).thenReturn(ownDim(1L));
        PlmCategoryValue a = value(11L, 1L, "ENABLED");
        PlmCategoryValue b = value(12L, 1L, "ENABLED");
        b.setValueName("吊灯");
        when(valueMapper.selectBatchIds(any())).thenReturn(List.of(a, b));

        CategoryFilter filter = new CategoryFilter();
        CategoryFilterGroup g = new CategoryFilterGroup();
        g.setDimId(1L);
        g.setValueIds(List.of(11L, 12L));
        filter.setDims(List.of(g));

        assertEquals("产品线:吸顶灯/吊灯", service.describeFilter(filter));
        assertEquals("本部门全部资料", service.describeFilter(new CategoryFilter()));
    }

    @Test
    @DisplayName("AC-B1-7 维度使用情况：资料条数去重、分类值计数")
    void dimUsageReportsImpact() {
        SubscriptionTestSupport.login("category:view", "category:manage");
        when(dimMapper.selectById(1L)).thenReturn(ownDim(1L));
        when(ledgerCategoryMapper.selectList(any())).thenReturn(List.of(tag(100L), tag(100L), tag(200L)));
        when(valueMapper.selectCount(any())).thenReturn(3L);

        Map<String, Object> usage = service.dimUsage(1L);

        assertEquals(2L, usage.get("usedCount"), "同一条资料的多个标签只算一条");
        assertEquals(3L, usage.get("valueCount"));
    }
}
