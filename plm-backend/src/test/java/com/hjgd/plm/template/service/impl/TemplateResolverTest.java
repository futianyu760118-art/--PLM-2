package com.hjgd.plm.template.service.impl;

import com.hjgd.plm.template.service.impl.TemplateResolverServiceImpl.TplMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 模板解析逻辑白盒单测：与实现同包，访问包级私有纯函数 pickBest / matches / TplMeta。
 * 镜像 SQL 13 种子模板元信息，验证按 part_category/product_type/material_type 的匹配优先级。
 */
@DisplayName("模板解析逻辑(档案树/参数模板按品类匹配)")
class TemplateResolverTest {

    private static List<TplMeta> archiveMetas() {
        return List.of(
                meta("LUMINAIRE_FG", "灯具成品档案树", "PRODUCT", null, null, 100),
                meta("MOLD_PART", "模具件档案树", "COMPONENT", null, "PLASTIC,HARDWARE", 90),
                meta("GENERIC_COMPONENT", "通用部件档案树", "COMPONENT", null, null, 10),
                meta("ASSEMBLY", "半成品组件档案树", "ASSEMBLY", null, null, 100),
                meta("STANDARD", "标准件档案树", "STANDARD", null, null, 100),
                meta("GENERIC_DEFAULT", "默认档案树", null, null, null, 1)
        );
    }

    private static TplMeta meta(String code, String name, String cat, String prod, String mat, int prio) {
        TplMeta m = new TplMeta();
        m.tplCode = code;
        m.tplName = name;
        m.matchCategory = cat;
        m.matchProductType = prod;
        m.matchMaterialType = mat;
        m.priority = prio;
        return m;
    }

    @Test
    @DisplayName("成品(PRODUCT) → 灯具成品树")
    void productResolvesLuminaire() {
        String tpl = TemplateResolverServiceImpl.pickBest(archiveMetas(), "PRODUCT", "FL", null);
        assertEquals("LUMINAIRE_FG", tpl);
    }

    @Test
    @DisplayName("塑胶件部件(COMPONENT+PLASTIC) → 模具件树")
    void plasticComponentResolvesMoldPart() {
        assertEquals("MOLD_PART",
                TemplateResolverServiceImpl.pickBest(archiveMetas(), "COMPONENT", null, "PLASTIC"));
        assertEquals("MOLD_PART",
                TemplateResolverServiceImpl.pickBest(archiveMetas(), "COMPONENT", null, "HARDWARE"));
    }

    @Test
    @DisplayName("部件但无材质(无法判断模具) → 通用部件树,不走模具树")
    void componentWithoutMaterialResolvesGeneric() {
        assertEquals("GENERIC_COMPONENT",
                TemplateResolverServiceImpl.pickBest(archiveMetas(), "COMPONENT", null, null));
    }

    @Test
    @DisplayName("半成品/标准件 → 各自专属树")
    void assemblyAndStandard() {
        assertEquals("ASSEMBLY",
                TemplateResolverServiceImpl.pickBest(archiveMetas(), "ASSEMBLY", null, null));
        assertEquals("STANDARD",
                TemplateResolverServiceImpl.pickBest(archiveMetas(), "STANDARD", null, null));
    }

    @Test
    @DisplayName("未知品类 → 兜底默认树")
    void unknownFallsBackToDefault() {
        assertEquals("GENERIC_DEFAULT",
                TemplateResolverServiceImpl.pickBest(archiveMetas(), "PACKAGING", null, null));
    }

    @Test
    @DisplayName("材质列表匹配:逗号分隔大小写无关")
    void materialTypeListMatch() {
        TplMeta m = meta("X", "x", "COMPONENT", null, "PLASTIC,HARDWARE", 90);
        assertTrue(TemplateResolverServiceImpl.matches(m, "COMPONENT", null, "plastic"));
        assertTrue(TemplateResolverServiceImpl.matches(m, "component", null, "HARDWARE"));
        assertFalse(TemplateResolverServiceImpl.matches(m, "COMPONENT", null, "STANDARD"),
                "材质不在列表应不匹配");
        assertFalse(TemplateResolverServiceImpl.matches(m, "COMPONENT", null, null),
                "要求材质但未给材质应不匹配");
    }

    @Test
    @DisplayName("空匹配条件(NULL) 表示不限,任意值均可匹配")
    void nullConditionsMatchAnything() {
        TplMeta m = meta("GENERIC_DEFAULT", "默认", null, null, null, 1);
        assertTrue(TemplateResolverServiceImpl.matches(m, "PRODUCT", "FL", "PLASTIC"));
        assertTrue(TemplateResolverServiceImpl.matches(m, null, null, null));
    }
}
