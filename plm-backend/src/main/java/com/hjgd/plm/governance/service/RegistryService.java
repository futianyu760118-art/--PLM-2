package com.hjgd.plm.governance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.governance.entity.DataProduct;
import com.hjgd.plm.governance.entity.MetricObject;
import com.hjgd.plm.governance.mapper.DataProductMapper;
import com.hjgd.plm.governance.mapper.MetricObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标对象与数据产品治理（F13 / F14 / ISS-009 / ISS-010）。
 *
 * <p>这一层解决的是「管理数字从哪来、谁负责、口径是什么」：
 * 指标对象回答「这个数是什么、谁负责、阈值多少」，
 * 数据产品回答「这个数据从哪取、谁是唯一真相源、SLA 与权限是什么」。
 * 两者都提供完整性核对（缺项计数），缺项计数不为 0 即治理未闭环。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistryService {

    private final MetricObjectMapper metricObjectMapper;
    private final DataProductMapper dataProductMapper;

    // ------------------------------------------------------------ 指标对象

    public List<MetricObject> listMetricObjects() {
        LambdaQueryWrapper<MetricObject> w = new LambdaQueryWrapper<>();
        w.orderByAsc(MetricObject::getMetricCode);
        return metricObjectMapper.selectList(w);
    }

    public MetricObject getMetricObject(String metricCode) {
        MetricObject m = metricObjectMapper.selectById(metricCode);
        if (m == null) throw new BusinessException("指标对象不存在: " + metricCode);
        return m;
    }

    @Transactional
    public MetricObject saveMetricObject(MetricObject req) {
        if (req == null || !StringUtils.hasText(req.getMetricCode())) {
            throw new BusinessException("metric_code 不得为空");
        }
        List<String> missing = missingMetricFields(req);
        if (!missing.isEmpty()) {
            throw new BusinessException("指标对象治理要素缺失: " + missing);
        }
        if (metricObjectMapper.selectById(req.getMetricCode()) == null) {
            metricObjectMapper.insert(req);
        } else {
            metricObjectMapper.updateById(req);
        }
        return metricObjectMapper.selectById(req.getMetricCode());
    }

    /** 指标对象完整性核对：返回缺项清单（缺项计数 = 0 才满足治理验收）。 */
    public Map<String, Object> checkMetricObjects() {
        List<Map<String, Object>> rows = new ArrayList<>();
        int withMissing = 0;
        for (MetricObject m : listMetricObjects()) {
            List<String> missing = missingMetricFields(m);
            if (!missing.isEmpty()) withMissing++;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("metricCode", m.getMetricCode());
            r.put("name", m.getName());
            r.put("ownerRole", m.getOwnerRole());
            r.put("version", m.getVersion());
            r.put("missing", missing);
            r.put("complete", missing.isEmpty());
            rows.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", rows.size());
        out.put("incomplete", withMissing);
        out.put("rows", rows);
        return out;
    }

    /**
     * 指标对象的必备治理要素。
     * <p>阈值（threshold）可为空 —— 无历史基线时 AEOS 要求先定义采集方法、
     * 不编造数值；但 {@code baselineMethod} 必须给出，二者至少有一个说明数据怎么来。
     */
    private static List<String> missingMetricFields(MetricObject m) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(m.getMetricCode())) missing.add("metric_code");
        if (!StringUtils.hasText(m.getName())) missing.add("name");
        if (!StringUtils.hasText(m.getBusinessDefinition())) missing.add("business_definition");
        if (!StringUtils.hasText(m.getOwnerRole())) missing.add("owner_role(Accountable Business Owner)");
        if (!StringUtils.hasText(m.getVersion())) missing.add("version");
        if (!StringUtils.hasText(m.getStatus())) missing.add("status");
        if (!StringUtils.hasText(m.getQualityRule())) missing.add("quality_rule");
        if (!StringUtils.hasText(m.getBaselineMethod()) && !StringUtils.hasText(m.getThreshold())) {
            missing.add("baseline_method 或 threshold（至少其一，不得两者皆空）");
        }
        return missing;
    }

    // ------------------------------------------------------------ 数据产品

    public List<DataProduct> listDataProducts() {
        LambdaQueryWrapper<DataProduct> w = new LambdaQueryWrapper<>();
        w.orderByAsc(DataProduct::getProductCode);
        return dataProductMapper.selectList(w);
    }

    public DataProduct getDataProduct(String productCode) {
        DataProduct p = dataProductMapper.selectById(productCode);
        if (p == null) throw new BusinessException("数据产品不存在: " + productCode);
        return p;
    }

    @Transactional
    public DataProduct saveDataProduct(DataProduct req) {
        if (req == null || !StringUtils.hasText(req.getProductCode())) {
            throw new BusinessException("product_code 不得为空");
        }
        List<String> missing = missingDataProductFields(req);
        if (!missing.isEmpty()) {
            throw new BusinessException("数据产品治理要素缺失: " + missing);
        }
        if (dataProductMapper.selectById(req.getProductCode()) == null) {
            dataProductMapper.insert(req);
        } else {
            dataProductMapper.updateById(req);
        }
        return dataProductMapper.selectById(req.getProductCode());
    }

    /** 数据产品完整性核对。 */
    public Map<String, Object> checkDataProducts() {
        List<Map<String, Object>> rows = new ArrayList<>();
        int withMissing = 0;
        for (DataProduct p : listDataProducts()) {
            List<String> missing = missingDataProductFields(p);
            if (!missing.isEmpty()) withMissing++;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("productCode", p.getProductCode());
            r.put("name", p.getName());
            r.put("domain", p.getDomain());
            r.put("authoritativeSource", p.getAuthoritativeSource());
            r.put("missing", missing);
            r.put("complete", missing.isEmpty());
            rows.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", rows.size());
        out.put("incomplete", withMissing);
        out.put("rows", rows);
        return out;
    }

    private static List<String> missingDataProductFields(DataProduct p) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(p.getProductCode())) missing.add("product_code");
        if (!StringUtils.hasText(p.getName())) missing.add("name");
        if (!StringUtils.hasText(p.getDomain())) missing.add("domain");
        if (!StringUtils.hasText(p.getOwnerRole())) missing.add("owner_role");
        if (!StringUtils.hasText(p.getAuthoritativeSource())) missing.add("authoritative_source(唯一真相源)");
        if (!StringUtils.hasText(p.getServiceContract())) missing.add("service_contract(含 version/evidence_refs)");
        if (!StringUtils.hasText(p.getSla())) missing.add("sla");
        if (!StringUtils.hasText(p.getPermission())) missing.add("permission");
        if (!StringUtils.hasText(p.getStatus())) missing.add("status");
        return missing;
    }
}
