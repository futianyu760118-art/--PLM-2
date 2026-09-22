package com.hjgd.plm.bom;

import com.hjgd.plm.bom.entity.BomTemplate;
import com.hjgd.plm.bom.mapper.BomTemplateMapper;
import com.hjgd.plm.bom.service.impl.BomServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.bom.entity.Bom;
import com.hjgd.plm.bom.entity.BomItem;
import com.hjgd.plm.bom.entity.BomTemplateItem;
import com.hjgd.plm.bom.mapper.BomItemMapper;
import com.hjgd.plm.bom.mapper.BomMapper;
import com.hjgd.plm.bom.mapper.BomVersionMapper;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.service.MaterialService;
import com.hjgd.plm.system.service.SequenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * V1.1 BOM 模板套用 + 拖拽排序测试。
 */
@DisplayName("BOM 模板/拖拽排序 V1.1")
@ExtendWith(MockitoExtension.class)
class BomTemplateServiceTest {

    @Mock private BomMapper bomMapper;
    @Mock private BomItemMapper bomItemMapper;
    @Mock private BomVersionMapper bomVersionMapper;
    @Mock private BomTemplateMapper bomTemplateMapper;
    @Mock private SequenceService sequenceService;
    @Mock private MaterialService materialService;
    @Mock private ObjectMapper objectMapper;
    @Mock private JdbcTemplate jdbcTemplate;
    @InjectMocks private BomServiceImpl bomService;

    @BeforeEach
    void setUp() {
        LoginUser u = mock(LoginUser.class);
        lenient().when(u.getRealName()).thenReturn("测试");
        lenient().when(u.getUserId()).thenReturn(1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    @Test
    @DisplayName("套用模板: 4 个模板各取子件 → BOM 创建成功")
    void createFromTemplatesMergesAll() {
        // 顶级料号
        Material root = new Material();
        root.setId(1L); root.setPartNo("HJ-FL-100W"); root.setMaterialName("投光灯100W");
        root.setUnit("PCS");
        when(materialService.getByPartNo("HJ-FL-100W")).thenReturn(root);

        // 子件(每个模板 1 个)
        Material heatsink = new Material();
        heatsink.setId(10L); heatsink.setPartNo("STRUCT-HEATSINK"); heatsink.setMaterialName("铝合金散热器");
        heatsink.setMakeType(0);
        Material fastener = new Material();
        fastener.setId(11L); fastener.setPartNo("FAST-M3X8"); fastener.setMaterialName("M3×8 螺丝");
        fastener.setMakeType(1);
        Material pkgbox = new Material();
        pkgbox.setId(12L); pkgbox.setPartNo("PKG-COLORBOX"); pkgbox.setMaterialName("彩盒");
        pkgbox.setMakeType(1);
        Material driver = new Material();
        driver.setId(13L); driver.setPartNo("ELEC-DRIVER"); driver.setMaterialName("LED 驱动");
        driver.setMakeType(1);

        when(materialService.getByPartNo("STRUCT-HEATSINK")).thenReturn(heatsink);
        when(materialService.getByPartNo("FAST-M3X8")).thenReturn(fastener);
        when(materialService.getByPartNo("PKG-COLORBOX")).thenReturn(pkgbox);
        when(materialService.getByPartNo("ELEC-DRIVER")).thenReturn(driver);

        when(sequenceService.nextNo("BOM_NO")).thenReturn("BOM-TPL-001");
        when(bomMapper.insert(any(Bom.class))).thenAnswer(inv -> {
            ((Bom) inv.getArgument(0)).setId(99L);
            return 1;
        });
        when(bomItemMapper.insert(any(BomItem.class))).thenAnswer(inv -> {
            ((BomItem) inv.getArgument(0)).setId((long) (Math.random() * 1000));
            return 1;
        });

        // 模板列表
        BomTemplate t1 = mockTemplate(1L, "STRUCTURE_DEFAULT", "structure");
        BomTemplate t2 = mockTemplate(2L, "FASTENER_DEFAULT", "fastener");
        BomTemplate t3 = mockTemplate(3L, "PACKAGING_DEFAULT", "packaging");
        BomTemplate t4 = mockTemplate(4L, "ELECTRONICS_DEFAULT", "electronics");

        when(bomTemplateMapper.findByCode("STRUCTURE_DEFAULT")).thenReturn(t1);
        when(bomTemplateMapper.findByCode("FASTENER_DEFAULT")).thenReturn(t2);
        when(bomTemplateMapper.findByCode("PACKAGING_DEFAULT")).thenReturn(t3);
        when(bomTemplateMapper.findByCode("ELECTRONICS_DEFAULT")).thenReturn(t4);

        when(bomTemplateMapper.listItems(1L)).thenReturn(List.of(
                new BomTemplateItem() {{ setChildPartNo("STRUCT-HEATSINK"); setQuantity(BigDecimal.ONE); setIsOptional(false); setSortOrder(1); }}
        ));
        when(bomTemplateMapper.listItems(2L)).thenReturn(List.of(
                new BomTemplateItem() {{ setChildPartNo("FAST-M3X8"); setQuantity(BigDecimal.valueOf(4)); setIsOptional(false); setSortOrder(1); }}
        ));
        when(bomTemplateMapper.listItems(3L)).thenReturn(List.of(
                new BomTemplateItem() {{ setChildPartNo("PKG-COLORBOX"); setQuantity(BigDecimal.ONE); setIsOptional(false); setSortOrder(1); }}
        ));
        when(bomTemplateMapper.listItems(4L)).thenReturn(List.of(
                new BomTemplateItem() {{ setChildPartNo("ELEC-DRIVER"); setQuantity(BigDecimal.ONE); setIsOptional(false); setSortOrder(1); }}
        ));

        Bom bom = bomService.createFromTemplates("HJ-FL-100W",
                List.of("STRUCTURE_DEFAULT", "FASTENER_DEFAULT", "PACKAGING_DEFAULT", "ELECTRONICS_DEFAULT"),
                false);

        assertEquals("BOM-TPL-001", bom.getBomNo());
        assertEquals(99L, bom.getId());
        // 1 根 + 4 子件 = 5 行
        verify(bomItemMapper, times(5)).insert(any(BomItem.class));
    }

    @Test
    @DisplayName("套用模板: 不存在的料号跳过,继续其他项")
    void createFromTemplatesSkipsMissing() {
        Material root = new Material();
        root.setId(1L); root.setPartNo("HJ-X"); root.setMaterialName("X");
        when(materialService.getByPartNo("HJ-X")).thenReturn(root);

        when(sequenceService.nextNo("BOM_NO")).thenReturn("BOM-X-1");
        when(bomMapper.insert(any(Bom.class))).thenAnswer(inv -> { ((Bom) inv.getArgument(0)).setId(7L); return 1; });
        when(bomItemMapper.insert(any(BomItem.class))).thenAnswer(inv -> { ((BomItem) inv.getArgument(0)).setId(System.nanoTime()); return 1; });

        BomTemplate t = mockTemplate(1L, "STRUCTURE_DEFAULT", "structure");
        when(bomTemplateMapper.findByCode("STRUCTURE_DEFAULT")).thenReturn(t);
        when(bomTemplateMapper.listItems(1L)).thenReturn(List.of(
                new BomTemplateItem() {{ setChildPartNo("EXISTS"); setQuantity(BigDecimal.ONE); setSortOrder(1); }},
                new BomTemplateItem() {{ setChildPartNo("MISSING"); setQuantity(BigDecimal.ONE); setSortOrder(2); }}
        ));
        Material exist = new Material();
        exist.setId(100L); exist.setPartNo("EXISTS"); exist.setMaterialName("已存在");
        when(materialService.getByPartNo("EXISTS")).thenReturn(exist);
        when(materialService.getByPartNo("MISSING")).thenReturn(null);

        bomService.createFromTemplates("HJ-X", List.of("STRUCTURE_DEFAULT"), false);

        // 1 根 + 1 子件 = 2 行 (MISSING 跳过)
        verify(bomItemMapper, times(2)).insert(any(BomItem.class));
    }

    @Test
    @DisplayName("套用模板: 无效料号 → 抛异常")
    void createFromTemplatesRejectsInvalidRoot() {
        when(materialService.getByPartNo("UNKNOWN")).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> bomService.createFromTemplates("UNKNOWN", List.of("X"), false));
    }

    @Test
    @DisplayName("套用模板: includeOptional=true 时包含可选项")
    void createFromTemplatesWithOptional() {
        Material root = new Material();
        root.setId(1L); root.setPartNo("HJ-Y"); root.setMaterialName("Y");
        when(materialService.getByPartNo("HJ-Y")).thenReturn(root);
        when(sequenceService.nextNo("BOM_NO")).thenReturn("BOM-Y");
        when(bomMapper.insert(any(Bom.class))).thenAnswer(inv -> { ((Bom) inv.getArgument(0)).setId(8L); return 1; });
        when(bomItemMapper.insert(any(BomItem.class))).thenAnswer(inv -> { ((BomItem) inv.getArgument(0)).setId(System.nanoTime()); return 1; });

        BomTemplate t = mockTemplate(1L, "STRUCTURE_DEFAULT", "structure");
        when(bomTemplateMapper.findByCode("STRUCTURE_DEFAULT")).thenReturn(t);
        when(bomTemplateMapper.listItems(1L)).thenReturn(List.of(
                new BomTemplateItem() {{ setChildPartNo("A"); setQuantity(BigDecimal.ONE); setIsOptional(false); setSortOrder(1); }},
                new BomTemplateItem() {{ setChildPartNo("B"); setQuantity(BigDecimal.ONE); setIsOptional(true); setSortOrder(2); }}
        ));
        Material a = new Material(); a.setId(1L); a.setPartNo("A");
        Material b = new Material(); b.setId(2L); b.setPartNo("B");
        when(materialService.getByPartNo("A")).thenReturn(a);
        when(materialService.getByPartNo("B")).thenReturn(b);

        bomService.createFromTemplates("HJ-Y", List.of("STRUCTURE_DEFAULT"), true);
        // 1 根 + 2 子件(含可选项)
        verify(bomItemMapper, times(3)).insert(any(BomItem.class));
    }

    @Test
    @DisplayName("拖拽排序: 按 itemIds 顺序写入 sort_order")
    void reorderItemsAssignsSequentialSortOrder() {
        BomItem i1 = new BomItem(); i1.setId(11L); i1.setBomId(1L); i1.setParentItemId(100L); i1.setPartNo("A");
        BomItem i2 = new BomItem(); i2.setId(22L); i2.setBomId(1L); i2.setParentItemId(100L); i2.setPartNo("B");
        BomItem i3 = new BomItem(); i3.setId(33L); i3.setBomId(1L); i3.setParentItemId(100L); i3.setPartNo("C");
        when(bomItemMapper.selectList(any())).thenReturn(List.of(i1, i2, i3));

        bomService.reorderItems(1L, List.of(33L, 11L, 22L)); // C, A, B

        // 三次 updateById, sort_order 应该是 1, 2, 3
        org.mockito.InOrder ord = inOrder(bomItemMapper);
        org.mockito.ArgumentCaptor<BomItem> cap = org.mockito.ArgumentCaptor.forClass(BomItem.class);
        ord.verify(bomItemMapper, times(3)).updateById(cap.capture());
        List<BomItem> updated = cap.getAllValues();
        assertEquals(33L, updated.get(0).getId()); assertEquals(1, updated.get(0).getSortOrder());
        assertEquals(11L, updated.get(1).getId()); assertEquals(2, updated.get(1).getSortOrder());
        assertEquals(22L, updated.get(2).getId()); assertEquals(3, updated.get(2).getSortOrder());
    }

    @Test
    @DisplayName("拖拽排序: 包含不属于该 BOM 的 itemId → 抛异常")
    void reorderItemsRejectsForeign() {
        BomItem i1 = new BomItem(); i1.setId(11L); i1.setBomId(1L); i1.setParentItemId(100L); i1.setPartNo("A");
        when(bomItemMapper.selectList(any())).thenReturn(List.of(i1));
        assertThrows(BusinessException.class,
                () -> bomService.reorderItems(1L, List.of(11L, 999L)));
    }

    private BomTemplate mockTemplate(Long id, String code, String cat) {
        BomTemplate t = new BomTemplate();
        t.setId(id); t.setTemplateCode(code); t.setCategory(cat); t.setTemplateName(code);
        return t;
    }
}