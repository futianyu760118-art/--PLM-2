package com.hjgd.plm.governance;

import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.governance.entity.DataProduct;
import com.hjgd.plm.governance.entity.MetricObject;
import com.hjgd.plm.governance.mapper.DataProductMapper;
import com.hjgd.plm.governance.mapper.MetricObjectMapper;
import com.hjgd.plm.governance.service.RegistryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * F13 指标对象治理 / F14 数据产品注册：缺项计数 = 0 才算治理闭环。
 */
@DisplayName("F13/F14 指标对象与数据产品治理")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistryServiceTest {

    @Mock private MetricObjectMapper metricObjectMapper;
    @Mock private DataProductMapper dataProductMapper;
    @InjectMocks private RegistryService service;

    // ---------------------------------------------------------------- F13

    private MetricObject metric(String code) {
        MetricObject m = new MetricObject();
        m.setMetricCode(code);
        m.setName("项目健康分");
        m.setBusinessDefinition("项目进度健康分");
        m.setOwnerRole("业务与产品负责人");
        m.setVersion("V1");
        m.setStatus("ACTIVE");
        m.setQualityRule("{\"min\":0,\"max\":100}");
        m.setBaselineMethod("按项目逐条计算");
        return m;
    }

    @Test
    @DisplayName("指标对象: 治理要素齐备 → 保存成功")
    void saveCompleteMetricObject() {
        MetricObject req = metric("M_PROJECT_HEALTH");
        when(metricObjectMapper.selectById("M_PROJECT_HEALTH")).thenReturn(null, req);

        MetricObject out = service.saveMetricObject(req);

        verify(metricObjectMapper).insert(req);
        assertEquals("M_PROJECT_HEALTH", out.getMetricCode());
    }

    @Test
    @DisplayName("指标对象: 缺 Owner/定义/质量规则 → 拒绝保存")
    void rejectIncompleteMetricObject() {
        MetricObject req = metric("M_X");
        req.setOwnerRole(null);
        req.setBusinessDefinition("  ");
        req.setQualityRule(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveMetricObject(req));
        assertTrue(ex.getMessage().contains("owner_role"));
        assertTrue(ex.getMessage().contains("business_definition"));
        assertTrue(ex.getMessage().contains("quality_rule"));
        verify(metricObjectMapper, never()).insert(any());
    }

    @Test
    @DisplayName("指标对象: 无历史基线时采集方法与阈值至少须有一项")
    void metricObjectRequiresBaselineOrThreshold() {
        MetricObject req = metric("M_X");
        req.setBaselineMethod(null);
        req.setThreshold(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveMetricObject(req));
        assertTrue(ex.getMessage().contains("baseline_method"));
    }

    @Test
    @DisplayName("指标对象完整性核对: 缺项计数与明细")
    void checkMetricObjects() {
        MetricObject ok = metric("M_OK");
        MetricObject bad = metric("M_BAD");
        bad.setOwnerRole(null);
        when(metricObjectMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(ok, bad)));

        Map<String, Object> res = service.checkMetricObjects();

        assertEquals(2, res.get("total"));
        assertEquals(1, res.get("incomplete"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) res.get("rows");
        assertEquals(true, rows.get(0).get("complete"));
        assertEquals(false, rows.get(1).get("complete"));
    }

    @Test
    @DisplayName("指标对象: 不存在时抛业务异常")
    void metricObjectNotFound() {
        when(metricObjectMapper.selectById("M_NONE")).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getMetricObject("M_NONE"));
    }

    // ---------------------------------------------------------------- F14

    private DataProduct product(String code) {
        DataProduct p = new DataProduct();
        p.setProductCode(code);
        p.setName("项目360");
        p.setDomain("项目管理");
        p.setOwnerRole("业务与产品负责人");
        p.setAuthoritativeSource("PLM2 plm_project (唯一)");
        p.setServiceContract("{\"version\":\"V1\",\"returns\":[\"source\",\"version\",\"evidence_refs\"]}");
        p.setSla("{\"freshness\":\"T+1\"}");
        p.setPermission("{\"roles\":[\"RD_LEAD\"]}");
        p.setStatus("ACTIVE");
        return p;
    }

    @Test
    @DisplayName("数据产品: 要素齐备 → 保存成功")
    void saveCompleteDataProduct() {
        DataProduct req = product("Project360");
        when(dataProductMapper.selectById("Project360")).thenReturn(null, req);

        DataProduct out = service.saveDataProduct(req);

        verify(dataProductMapper).insert(req);
        assertEquals("Project360", out.getProductCode());
    }

    @Test
    @DisplayName("数据产品: 缺唯一真相源/服务契约 → 拒绝保存")
    void rejectDataProductWithoutAuthoritativeSource() {
        DataProduct req = product("X360");
        req.setAuthoritativeSource(null);
        req.setServiceContract("  ");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveDataProduct(req));
        assertTrue(ex.getMessage().contains("authoritative_source"));
        assertTrue(ex.getMessage().contains("service_contract"));
        verify(dataProductMapper, never()).insert(any());
    }

    @Test
    @DisplayName("数据产品完整性核对: 缺项计数")
    void checkDataProducts() {
        DataProduct ok = product("Project360");
        DataProduct bad = product("BOM360");
        bad.setSla(null);
        when(dataProductMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(ok, bad)));

        Map<String, Object> res = service.checkDataProducts();

        assertEquals(2, res.get("total"));
        assertEquals(1, res.get("incomplete"));
    }

    @Test
    @DisplayName("数据产品: 已存在时走更新而非重复插入")
    void updateExistingDataProduct() {
        DataProduct req = product("Project360");
        when(dataProductMapper.selectById("Project360")).thenReturn(product("Project360"), req);

        service.saveDataProduct(req);

        verify(dataProductMapper).updateById(req);
        verify(dataProductMapper, never()).insert(any());
    }
}
