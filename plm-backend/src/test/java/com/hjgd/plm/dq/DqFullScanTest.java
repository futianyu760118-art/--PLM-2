package com.hjgd.plm.dq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.dq.dto.DqRunResult;
import com.hjgd.plm.dq.service.impl.DataQualityServiceImpl;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.enums.MaterialType;
import com.hjgd.plm.template.service.TemplateResolverService;
import com.hjgd.plm.template.service.TemplateResolverService.ParamItem;
import com.hjgd.plm.template.service.TemplateResolverService.ParamTpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DQ 全量夜检：容错、债务幂等、参数模板必填项检查。
 */
@DisplayName("DQ 全量夜检 V1.1")
@ExtendWith(MockitoExtension.class)
class DqFullScanTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private TemplateResolverService templateResolverService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private DataQualityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DataQualityServiceImpl(jdbcTemplate, objectMapper, templateResolverService);
    }

    @Test
    @DisplayName("加载料号失败时安全返回 0，不抛异常")
    void loadFailureReturnsZero() {
        when(jdbcTemplate.query(anyString(), any(BeanPropertyRowMapper.class)))
                .thenThrow(new RuntimeException("db down"));
        assertEquals(0, service.runFullScan("NIGHTLY"));
    }

    @Test
    @DisplayName("WARN 债务已存在 OPEN 则不重复开")
    void debtIsIdempotent() {
        // 用成品但缺 ipRating/nameEn → 内置 PART_FG_IP/PART_FG_EN_NAME WARN 项(不依赖 DB 规则)
        Material m = finishedLuminaire("HJ_DEBT_001");
        when(jdbcTemplate.query(anyString(), any(BeanPropertyRowMapper.class)))
                .thenReturn(List.of(m));
        // openDebts 调带 bind 参数的 varargs queryForObject(sql, Class, Object...)
        when(jdbcTemplate.queryForObject(contains("FROM plm_dq_debt"), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);

        int n = service.runFullScan("NIGHTLY");

        assertEquals(1, n);
        verify(jdbcTemplate, never()).update(contains("INSERT INTO plm_dq_debt"), (Object[]) any());
    }

    @Test
    @DisplayName("缺必填灯具参数(IP等级)产生 WARN 项")
    void missingRequiredParamProducesWarnItem() {
        Material m = finishedLuminaire("HJ_FL_001");
        when(templateResolverService.resolveParamTpl("PRODUCT", "FL", "FINISHED"))
                .thenReturn(luminaireTpl(true, "WARN"));
        when(jdbcTemplate.queryForList(eq("SELECT param_key FROM plm_material_param WHERE part_no=?"),
                eq(String.class), eq("HJ_FL_001"))).thenReturn(List.of());

        DqRunResult result = service.runForPart(m, "MANUAL");

        boolean hasParamWarn = result.getItems().stream().anyMatch(i ->
                "PARAM_REQUIRED_IP_RATING".equals(i.getRuleCode())
                        && "WARN".equals(i.getSeverity()) && "FAIL".equals(i.getResult()));
        assertTrue(hasParamWarn, "缺必填 IP 等级应产生 PARAM_REQUIRED_IP_RATING WARN 项");
    }

    @Test
    @DisplayName("必填参数已填则不产生债务项")
    void filledRequiredParamNoDebt() {
        Material m = finishedLuminaire("HJ_FL_002");
        when(templateResolverService.resolveParamTpl("PRODUCT", "FL", "FINISHED"))
                .thenReturn(luminaireTpl(true, "WARN"));
        when(jdbcTemplate.queryForList(eq("SELECT param_key FROM plm_material_param WHERE part_no=?"),
                eq(String.class), eq("HJ_FL_002"))).thenReturn(List.of("ip_rating"));

        DqRunResult result = service.runForPart(m, "MANUAL");

        boolean hasParamWarn = result.getItems().stream().anyMatch(i ->
                "PARAM_REQUIRED_IP_RATING".equals(i.getRuleCode()));
        assertFalse(hasParamWarn, "IP 等级已填不应产生参数债务项");
    }

    @Test
    @DisplayName("CREATE 时机不查参数(新建料号尚无参数，避免误判)")
    void createTriggerSkipsParamCheck() {
        Material m = finishedLuminaire("HJ_FL_003");
        // 模板有必填项，但 CREATE 时机不应触发
        lenient().when(templateResolverService.resolveParamTpl(any(), any(), any()))
                .thenReturn(luminaireTpl(true, "WARN"));

        DqRunResult result = service.runForPart(m, "CREATE");

        boolean hasParam = result.getItems().stream().anyMatch(i ->
                i.getRuleCode() != null && i.getRuleCode().startsWith("PARAM_REQUIRED_"));
        assertFalse(hasParam, "CREATE 时机不应做参数完整性检查");
    }

    private Material finishedLuminaire(String partNo) {
        Material m = new Material();
        m.setId(2L);
        m.setPartNo(partNo);
        m.setMaterialName("投光灯100W");
        m.setMaterialType(MaterialType.FINISHED);
        m.setPartCategory("PRODUCT");
        m.setProductType("FL");
        m.setUnit("PCS");
        return m;
    }

    private ParamTpl luminaireTpl(boolean required, String severity) {
        return new ParamTpl("LUMINAIRE_PARAM", "灯具参数模板", List.of(
                new ParamItem("ip_rating", "IP防护等级", null, "ENUM", "ip_rating", required, severity, 1)
        ));
    }
}
