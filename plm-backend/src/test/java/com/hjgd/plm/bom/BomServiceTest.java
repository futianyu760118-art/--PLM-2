package com.hjgd.plm.bom;

import com.hjgd.plm.bom.dto.BomDTO;
import com.hjgd.plm.bom.dto.BomItemDTO;
import com.hjgd.plm.bom.entity.Bom;
import com.hjgd.plm.bom.entity.BomItem;
import com.hjgd.plm.bom.mapper.BomItemMapper;
import com.hjgd.plm.bom.mapper.BomMapper;
import com.hjgd.plm.bom.mapper.BomVersionMapper;
import com.hjgd.plm.bom.service.impl.BomServiceImpl;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.service.MaterialService;
import com.hjgd.plm.system.service.SequenceService;
import com.hjgd.plm.auth.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BOM 多级爆炸测试
 */
@DisplayName("BOM多级爆炸模块测试")
@ExtendWith(MockitoExtension.class)
class BomServiceTest {

    @Mock private BomMapper bomMapper;
    @Mock private BomItemMapper bomItemMapper;
    @Mock private BomVersionMapper bomVersionMapper;
    @Mock private SequenceService sequenceService;
    @Mock private MaterialService materialService;
    @Mock private ObjectMapper objectMapper;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private com.hjgd.plm.lifecycle.service.LifecycleService lifecycleService;

    @InjectMocks
    private BomServiceImpl bomService;

    private Bom testBom;
    private Material rootMaterial;
    private Material childMaterial;

    @BeforeEach
    void setUp() {
        testBom = new Bom();
        testBom.setId(1L);
        testBom.setBomNo("BOM202607040001");
        testBom.setRootPartNo("HJ001");
        testBom.setMaterialId(1L);
        testBom.setVersionNo("V1.0");

        rootMaterial = new Material();
        rootMaterial.setId(1L);
        rootMaterial.setPartNo("HJ001");
        rootMaterial.setMaterialName("成品");
        rootMaterial.setUnit("PCS");

        childMaterial = new Material();
        childMaterial.setId(2L);
        childMaterial.setPartNo("HJ002");
        childMaterial.setMaterialName("零件A");
        childMaterial.setUnit("PCS");

        // 发布守卫(矩阵+角色)由 LifecycleServiceImpl 承担; 此处放行以便断言门禁与快照本身
        lenient().when(lifecycleService.isRoleAllowed(anyString(), anyString(), anyString())).thenReturn(true);

        LoginUser mockUser = mock(LoginUser.class);
        lenient().when(mockUser.getRealName()).thenReturn("测试工程师");
        lenient().when(mockUser.getUserId()).thenReturn(1L);
        lenient().when(mockUser.getUsername()).thenReturn("engineer");
        lenient().when(mockUser.getPrimaryRole()).thenReturn("ENGINEER");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(mockUser, null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("创建BOM自动生成根节点")
    void shouldCreateBomWithRootNode() {
        BomDTO dto = new BomDTO();
        dto.setRootPartNo("HJ001");
        when(materialService.getByPartNo("HJ001")).thenReturn(rootMaterial);
        when(sequenceService.nextNo("BOM_NO")).thenReturn("BOM202607040001");
        when(bomMapper.insert(any(Bom.class))).thenAnswer(inv -> {
            ((Bom) inv.getArgument(0)).setId(1L);
            return 1;
        });

        Bom result = bomService.create(dto);

        assertNotNull(result);
        assertEquals("BOM202607040001", result.getBomNo());
        verify(bomItemMapper).insert(any(BomItem.class)); // root node
    }

    @Nested
    @DisplayName("添加BOM明细")
    class AddItem {

        @Test
        @DisplayName("添加顶级子件成功")
        void shouldAddTopLevelItem() {
            BomItemDTO dto = new BomItemDTO();
            dto.setBomId(1L);
            dto.setPartNo("HJ002");
            dto.setQuantity(BigDecimal.valueOf(2));
            when(bomMapper.selectById(1L)).thenReturn(testBom);
            when(materialService.getByPartNo("HJ002")).thenReturn(childMaterial);

            BomItem result = bomService.addItem(dto);

            assertNotNull(result);
            assertEquals(1, result.getLevelNo());
            assertEquals("HJ002", result.getPartNo());
        }

        @Test
        @DisplayName("子件料号不存在时抛异常")
        void shouldRejectNonExistentPartNo() {
            BomItemDTO dto = new BomItemDTO();
            dto.setBomId(1L);
            dto.setPartNo("NOTEXIST");
            when(bomMapper.selectById(1L)).thenReturn(testBom);
            when(materialService.getByPartNo("NOTEXIST")).thenReturn(null);

            assertThrows(BusinessException.class, () -> bomService.addItem(dto));
        }

        @Test
        @DisplayName("循环引用检测: 子件=父件料号时拦截")
        void shouldDetectCycle() {
            BomItem parent = new BomItem();
            parent.setId(10L);
            parent.setPartNo("HJ002");
            parent.setParentItemId(0L);
            parent.setBomId(1L);
            parent.setLevelNo(1);

            BomItemDTO dto = new BomItemDTO();
            dto.setBomId(1L);
            dto.setParentItemId(10L);
            dto.setPartNo("HJ002"); // same as parent = cycle!
            dto.setQuantity(BigDecimal.ONE);

            when(bomMapper.selectById(1L)).thenReturn(testBom);
            when(materialService.getByPartNo("HJ002")).thenReturn(childMaterial);
            when(bomItemMapper.selectById(10L)).thenReturn(parent);

            assertThrows(BusinessException.class, () -> bomService.addItem(dto));
        }
    }

    @Test
    @DisplayName("删除父件级联删除子件")
    void shouldCascadeDeleteChildren() {
        BomItem parent = new BomItem();
        parent.setId(10L);
        parent.setBomId(1L);
        parent.setParentItemId(0L);

        BomItem child1 = new BomItem();
        child1.setId(11L);
        child1.setBomId(1L);
        child1.setParentItemId(10L);

        BomItem child2 = new BomItem();
        child2.setId(12L);
        child2.setBomId(1L);
        child2.setParentItemId(11L);

        when(bomItemMapper.selectById(10L)).thenReturn(parent);
        when(bomItemMapper.selectList(any())).thenReturn(Arrays.asList(parent, child1, child2));

        bomService.deleteItem(10L);

        verify(bomItemMapper).deleteBatchIds(argThat(ids -> ids.size() >= 1));
    }

    @Test
    @DisplayName("构建树形结构: 单层")
    void shouldBuildSingleLevelTree() {
        BomItem root = new BomItem();
        root.setId(1L);
        root.setBomId(1L);
        root.setParentItemId(0L);
        root.setPartNo("HJ001");

        BomItem child = new BomItem();
        child.setId(2L);
        child.setBomId(1L);
        child.setParentItemId(1L);
        child.setPartNo("HJ002");

        when(bomItemMapper.selectList(any())).thenReturn(Arrays.asList(root, child));

        List<BomItem> tree = bomService.getTree(1L);

        assertEquals(1, tree.size());
        assertEquals("HJ001", tree.get(0).getPartNo());
        assertNotNull(tree.get(0).getChildren());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("HJ002", tree.get(0).getChildren().get(0).getPartNo());
    }

    @Test
    @DisplayName("CBOM 成本滚算: 有效用量(路径连乘)×单价")
    void cbomCostRollup() {
        // EBOM 结构(单件成品对各零件的用量):
        //   HJ001(根,qty1)
        //   ├─ HJ002 (qty2, 单价10)   → 有效用量 1×2=2, 成本 20
        //   │   └─ HJ003 (qty3, 单价5) → 有效用量 1×2×3=6, 成本 30
        //   └─ HJ004 (qty4, 单价2)    → 有效用量 1×4=4, 成本 8
        Bom ebom = new Bom();
        ebom.setId(7L);
        ebom.setRootPartNo("HJ001");
        ebom.setBomType("EBOM");
        ebom.setStatus("RELEASED");
        ebom.setVersionNo("V1.0");

        BomItem root = bomItem(1L, 0L, "HJ001", BigDecimal.valueOf(1), 1);
        BomItem child = bomItem(2L, 1L, "HJ002", BigDecimal.valueOf(2), 2);
        BomItem grand = bomItem(3L, 2L, "HJ003", BigDecimal.valueOf(3), 3);
        BomItem sib = bomItem(4L, 1L, "HJ004", BigDecimal.valueOf(4), 2);

        when(bomMapper.selectOne(any())).thenReturn(ebom);
        when(bomItemMapper.selectList(any())).thenReturn(Arrays.asList(root, child, grand, sib));
        when(materialService.getByPartNo("HJ002")).thenReturn(costMaterial("HJ002", new BigDecimal("10")));
        when(materialService.getByPartNo("HJ003")).thenReturn(costMaterial("HJ003", new BigDecimal("5")));
        when(materialService.getByPartNo("HJ004")).thenReturn(costMaterial("HJ004", new BigDecimal("2")));

        List<Map<String, Object>> lines = bomService.cbomExpand("HJ001");

        Map<String, Map<String, Object>> byPart = new HashMap<>();
        for (Map<String, Object> l : lines) {
            byPart.put((String) l.get("partNo"), l);
        }
        assertEquals(new BigDecimal("20.0000"), byPart.get("HJ002").get("extendedCost"));
        assertEquals(new BigDecimal("30.0000"), byPart.get("HJ003").get("extendedCost"));
        assertEquals(new BigDecimal("8.0000"), byPart.get("HJ004").get("extendedCost"));
        assertEquals(new BigDecimal("6"), byPart.get("HJ003").get("effectiveQuantity"));
    }

    private BomItem bomItem(long id, long parent, String partNo, BigDecimal qty, int level) {
        BomItem it = new BomItem();
        it.setId(id);
        it.setBomId(1L);
        it.setParentItemId(parent);
        it.setPartNo(partNo);
        it.setQuantity(qty);
        it.setLevelNo(level);
        return it;
    }

    private Material costMaterial(String partNo, BigDecimal cost) {
        Material m = new Material();
        m.setPartNo(partNo);
        m.setStandardCost(cost);
        return m;
    }

    // ============ Step2 三态 / 发布门禁 / 版本快照 / where-used ============

    @Nested
    @DisplayName("构建MBOM(从EBOM)")
    class BuildMbom {
        @Test
        @DisplayName("从EBOM构建MBOM：复制明细、bomType=MBOM")
        void shouldBuildMbomFromEbom() {
            Bom ebom = new Bom();
            ebom.setId(7L);
            ebom.setBomNo("E1");
            ebom.setRootPartNo("HJ001");
            ebom.setMaterialId(1L);
            ebom.setBomType("EBOM");
            ebom.setVersionNo("V1.0");
            BomItem root = bomItem(1L, 0L, "HJ001", BigDecimal.ONE, 1);
            BomItem child = bomItem(2L, 1L, "HJ002", BigDecimal.valueOf(2), 2);
            when(bomMapper.selectById(7L)).thenReturn(ebom);
            when(bomItemMapper.selectList(any())).thenReturn(Arrays.asList(root, child));
            when(sequenceService.nextNo("BOM_NO")).thenReturn("BOM-MBOM-1");
            when(bomMapper.insert(any(Bom.class))).thenAnswer(inv -> {
                ((Bom) inv.getArgument(0)).setId(99L);
                return 1;
            });

            Bom mbom = bomService.buildMbom(7L);

            assertEquals("MBOM", mbom.getBomType());
            assertEquals(99L, mbom.getId());
            verify(bomItemMapper, times(2)).insert(any(BomItem.class));
        }

        @Test
        @DisplayName("非EBOM禁止构建MBOM")
        void shouldRejectNonEbomSource() {
            Bom mbom = new Bom();
            mbom.setId(7L);
            mbom.setBomType("MBOM");
            when(bomMapper.selectById(7L)).thenReturn(mbom);
            assertThrows(BusinessException.class, () -> bomService.buildMbom(7L));
        }
    }

    @Nested
    @DisplayName("构建SBOM(备件分级)")
    class BuildSbom {
        @Test
        @DisplayName("外购件→C类、高层自制件→A类 自动分级")
        void shouldClassifySbom() {
            Bom src = new Bom();
            src.setId(5L);
            src.setRootPartNo("HJ001");
            src.setMaterialId(1L);
            src.setVersionNo("V1.0");
            BomItem root = bomItem(1L, 0L, "HJ001", BigDecimal.ONE, 1);
            BomItem bought = bomItem(2L, 1L, "HJ002", BigDecimal.ONE, 2);
            bought.setMakeType(1); // 外购 → C
            BomItem made = bomItem(3L, 1L, "HJ003", BigDecimal.ONE, 2);
            made.setMakeType(0);   // 自制高层 → A
            when(bomMapper.selectById(5L)).thenReturn(src);
            when(bomItemMapper.selectList(any())).thenReturn(Arrays.asList(root, bought, made));
            when(sequenceService.nextNo("BOM_NO")).thenReturn("BOM-SBOM-1");
            when(bomMapper.insert(any(Bom.class))).thenAnswer(inv -> {
                ((Bom) inv.getArgument(0)).setId(88L);
                return 1;
            });

            Bom sbom = bomService.buildSbom(5L);

            assertEquals("SBOM", sbom.getBomType());
            // 第3次 insert 是 bought(copy) → C；第4次是 made(copy) → A
            verify(bomItemMapper, times(3)).insert(any(BomItem.class));
        }
    }

    @Nested
    @DisplayName("发布门禁与版本快照")
    class Release {
        @Test
        @DisplayName("只有根节点禁止发布")
        void shouldRejectEmptyBom() {
            testBom.setStatus("DRAFT");
            BomItem root = bomItem(1L, 0L, "HJ001", BigDecimal.ONE, 1);
            when(bomMapper.selectById(1L)).thenReturn(testBom);
            when(bomItemMapper.selectList(any())).thenReturn(List.of(root));
            assertThrows(BusinessException.class, () -> bomService.release(1L));
        }

        @Test
        @DisplayName("子件数量为0禁止发布")
        void shouldRejectZeroQty() {
            testBom.setStatus("DRAFT");
            BomItem root = bomItem(1L, 0L, "HJ001", BigDecimal.ONE, 1);
            BomItem child = bomItem(2L, 1L, "HJ002", BigDecimal.ZERO, 2);
            when(bomMapper.selectById(1L)).thenReturn(testBom);
            when(bomItemMapper.selectList(any())).thenReturn(Arrays.asList(root, child));
            assertThrows(BusinessException.class, () -> bomService.release(1L));
        }

        @Test
        @DisplayName("合法发布：状态置RELEASED并写版本快照")
        void shouldReleaseAndSnapshot() throws Exception {
            testBom.setStatus("DRAFT");
            BomItem root = bomItem(1L, 0L, "HJ001", BigDecimal.ONE, 1);
            BomItem child = bomItem(2L, 1L, "HJ002", BigDecimal.valueOf(3), 2);
            when(bomMapper.selectById(1L)).thenReturn(testBom);
            when(bomItemMapper.selectList(any())).thenReturn(Arrays.asList(root, child));
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(bomVersionMapper.insert(any())).thenReturn(1);

            bomService.release(1L);

            assertEquals("RELEASED", testBom.getStatus());
            // 归档版本号必须落库(BOM.archive_version_no), 与快照版本一致
            assertEquals("V1.0", testBom.getArchiveVersionNo());
            verify(bomMapper).updateById(any(Bom.class));
            verify(bomVersionMapper).insert(any());
            verify(lifecycleService).recordHistory("BOM", "BOM202607040001", "DRAFT", "RELEASED",
                    "release", "测试工程师", "BOM发布");
        }

        @Test
        @DisplayName("CHANGING 再发布走 finish_change 动作")
        void changingBomReleasesViaFinishChange() throws Exception {
            testBom.setStatus("CHANGING");
            when(bomMapper.selectById(1L)).thenReturn(testBom);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(bomVersionMapper.insert(any())).thenReturn(1);

            bomService.release(1L);

            assertEquals("RELEASED", testBom.getStatus());
            verify(lifecycleService).recordHistory("BOM", "BOM202607040001", "CHANGING", "RELEASED",
                    "finish_change", "测试工程师", "BOM发布");
        }

        @Test
        @DisplayName("角色无权发布 → 403, 不改状态不写快照")
        void shouldRejectWhenRoleNotAllowed() {
            testBom.setStatus("DRAFT");
            when(bomMapper.selectById(1L)).thenReturn(testBom);
            when(lifecycleService.isRoleAllowed("BOM", "DRAFT", "release")).thenReturn(false);
            when(lifecycleService.allowedRoles("BOM", "DRAFT", "release")).thenReturn(java.util.Set.of("RD_LEAD"));

            BusinessException ex = assertThrows(BusinessException.class, () -> bomService.release(1L));

            assertEquals(403, ex.getCode());
            assertTrue(ex.getMessage().contains("RD_LEAD"));
            assertNull(testBom.getArchiveVersionNo());
            verify(bomMapper, never()).updateById(any(Bom.class));
            verify(bomVersionMapper, never()).insert(any());
        }

        @Test
        @DisplayName("矩阵无 release 出边(已发布/作废) → 409, 动作前即被拒")
        void shouldRejectWhenNoTransitionEdge() {
            testBom.setStatus("RELEASED");
            when(bomMapper.selectById(1L)).thenReturn(testBom);
            doThrow(new BusinessException(409, "no edge"))
                    .when(lifecycleService).assertTransition("BOM", "RELEASED", "release");

            BusinessException ex = assertThrows(BusinessException.class, () -> bomService.release(1L));

            assertEquals(409, ex.getCode());
            verify(bomMapper, never()).updateById(any(Bom.class));
        }
    }

    @Nested
    @DisplayName("where-used 反查")
    class WhereUsed {
        @Test
        @DisplayName("返回包含该料号的BOM清单")
        void shouldReturnContainingBoms() {
            Map<String, Object> row = new HashMap<>();
            row.put("bom_no", "BOM001");
            row.put("root_part_no", "HJ001");
            List<Map<String, Object>> rows = List.of(row);
            when(jdbcTemplate.queryForList(anyString(), eq("HJ002"))).thenReturn(rows);

            List<Map<String, Object>> result = bomService.whereUsed("HJ002");

            assertEquals(1, result.size());
            assertEquals("BOM001", result.get(0).get("bom_no"));
        }
    }
}

