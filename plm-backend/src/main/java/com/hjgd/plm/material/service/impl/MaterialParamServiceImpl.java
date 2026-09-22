package com.hjgd.plm.material.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.material.dto.MaterialParamView;
import com.hjgd.plm.material.entity.MaterialParam;
import com.hjgd.plm.material.mapper.MaterialParamMapper;
import com.hjgd.plm.material.service.MaterialParamService;
import com.hjgd.plm.template.service.TemplateResolverService;
import com.hjgd.plm.template.service.TemplateResolverService.DictOption;
import com.hjgd.plm.template.service.TemplateResolverService.ParamItem;
import com.hjgd.plm.template.service.TemplateResolverService.ParamTpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialParamServiceImpl implements MaterialParamService {

    private final MaterialParamMapper materialParamMapper;
    private final TemplateResolverService templateResolverService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<MaterialParamView> listForPart(String partNo) {
        String[] cpm = lookupCategoryMaterialType(partNo);
        ParamTpl tpl = templateResolverService.resolveParamTpl(cpm[0], cpm[1], cpm[2]);

        Map<String, String> values = loadValues(partNo);
        List<MaterialParamView> views = new ArrayList<>();
        for (ParamItem item : tpl.items) {
            MaterialParamView v = new MaterialParamView();
            v.setParamKey(item.paramKey);
            v.setParamName(item.paramName);
            v.setUnit(item.unit);
            v.setDataType(item.dataType);
            v.setDictType(item.dictType);
            v.setRequired(item.required);
            v.setDqSeverity(item.dqSeverity);
            v.setSortOrder(item.sortOrder);
            v.setValue(values.get(item.paramKey));
            if ("ENUM".equalsIgnoreCase(item.dataType) && item.dictType != null) {
                List<DictOption> opts = templateResolverService.listDictOptions(item.dictType);
                v.setOptions(opts);
            }
            views.add(v);
        }
        return views;
    }

    @Override
    @Transactional
    public void saveValues(String partNo, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        String operator;
        try {
            operator = SecurityUtils.getCurrentRealName();
        } catch (Exception e) {
            operator = "SYSTEM";
        }
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) {
                continue;
            }
            String val = e.getValue() == null ? "" : e.getValue().trim();
            materialParamMapper.upsert(partNo, e.getKey().trim(), val, operator);
        }
        log.info("物料参数保存 part={} keys={}", partNo, values.keySet());
    }

    private Map<String, String> loadValues(String partNo) {
        List<MaterialParam> rows = materialParamMapper.selectList(
                new LambdaQueryWrapper<MaterialParam>().eq(MaterialParam::getPartNo, partNo));
        return rows.stream().collect(Collectors.toMap(
                MaterialParam::getParamKey,
                p -> p.getParamValue() == null ? "" : p.getParamValue(),
                (a, b) -> a));
    }

    private String[] lookupCategoryMaterialType(String partNo) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT part_category, product_type, material_type FROM plm_material WHERE part_no=?",
                    partNo);
            return new String[]{
                    row.get("part_category") == null ? null : String.valueOf(row.get("part_category")),
                    row.get("product_type") == null ? null : String.valueOf(row.get("product_type")),
                    row.get("material_type") == null ? null : String.valueOf(row.get("material_type"))
            };
        } catch (Exception e) {
            log.warn("lookup material meta failed part={}: {}", partNo, e.getMessage());
            return new String[]{null, null, null};
        }
    }
}
