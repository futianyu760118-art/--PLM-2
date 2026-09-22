package com.hjgd.plm.material;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.archive.service.ArchiveTreeService;
import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.codegen.service.CodeGenService;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.dq.dto.DqRunResult;
import com.hjgd.plm.dq.service.DataQualityService;
import com.hjgd.plm.event.service.DomainEventService;
import com.hjgd.plm.lifecycle.service.LifecycleService;
import com.hjgd.plm.material.dto.MaterialDTO;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.entity.MaterialVersion;
import com.hjgd.plm.material.enums.MaterialStatus;
import com.hjgd.plm.material.enums.MaterialType;
import com.hjgd.plm.material.mapper.MaterialMapper;
import com.hjgd.plm.material.mapper.MaterialVersionMapper;
import com.hjgd.plm.material.service.EntityHistoryService;
import com.hjgd.plm.material.service.MaterialSupplierService;
import com.hjgd.plm.material.service.impl.MaterialServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("物料主数据模块测试 V1.1")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaterialServiceTest {

    @Mock private MaterialMapper materialMapper;
    @Mock private MaterialVersionMapper materialVersionMapper;
    @Mock private CodeGenService codeGenService;
    @Mock private ArchiveTreeService archiveTreeService;
    @Mock private LifecycleService lifecycleService;
    @Mock private DataQualityService dataQualityService;
    @Mock private DomainEventService domainEventService;
    @Mock private ObjectMapper objectMapper;
    @Mock private MaterialSupplierService materialSupplierService;
    @Mock private EntityHistoryService entityHistoryService;

    @InjectMocks
    private MaterialServiceImpl materialService;

    private Material testMaterial;

    @BeforeEach
    void setUp() {
        testMaterial = new Material();
        testMaterial.setId(1L);
        testMaterial.setPartNo("HJ202607040001");
        testMaterial.setMaterialName("测试物料");
        testMaterial.setMaterialType(MaterialType.FINISHED);
        testMaterial.setStatus(MaterialStatus.DRAFT);
        testMaterial.setVersionNo("V1.0");

        LoginUser mockUser = mock(LoginUser.class);
        lenient().when(mockUser.getRealName()).thenReturn("测试用户");
        lenient().when(mockUser.getUserId()).thenReturn(1L);
        lenient().when(mockUser.getUsername()).thenReturn("testuser");
        lenient().when(mockUser.getPrimaryRole()).thenReturn("ADMIN");
        lenient().when(mockUser.getRoles()).thenReturn(List.of("ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(mockUser, null, Collections.emptyList()));

        lenient().when(dataQualityService.runForPart(any(), anyString()))
                .thenReturn(DqRunResult.builder().blockCount(0).warnCount(0).score(100).build());
        lenient().doNothing().when(dataQualityService).assertNoBlock(any());
        lenient().doNothing().when(domainEventService).publish(any(), any(), any(), any());
        lenient().when(lifecycleService.resolveToState(eq("PART"), eq(MaterialStatus.DRAFT), eq("submit_review")))
                .thenReturn(MaterialStatus.IN_REVIEW);
        lenient().when(lifecycleService.resolveToState(eq("PART"), eq(MaterialStatus.DRAFT), eq("release")))
                .thenReturn(MaterialStatus.RELEASED);
        lenient().when(lifecycleService.resolveToState(eq("PART"), eq(MaterialStatus.IN_REVIEW), eq("release")))
                .thenReturn(MaterialStatus.RELEASED);
        lenient().when(lifecycleService.resolveToState(eq("PART"), eq(MaterialStatus.RELEASED), eq("submit_review")))
                .thenThrow(new BusinessException("lifecycle denied"));
        lenient().when(lifecycleService.resolveToState(eq("PART"), eq(MaterialStatus.CHANGING), eq("finish_change")))
                .thenReturn(MaterialStatus.RELEASED);
        // DQ 门禁按矩阵 require_dq 列判定; release/submit_review 需为 true 才会走 runForPart
        lenient().when(lifecycleService.requiresDq(eq("PART"), anyString(), anyString())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("创建物料")
    class CreateMaterial {

        @Test
        @DisplayName("自动编号创建")
        void shouldCreateMaterialWithAutoPartNo() {
            MaterialDTO dto = new MaterialDTO();
            dto.setMaterialName("新产品A");
            dto.setMaterialType(MaterialType.PLASTIC);
            when(codeGenService.allocate(eq("PART"), any(), isNull(), anyString())).thenReturn("HJ202607040001");
            when(materialMapper.insert(any(Material.class))).thenAnswer(inv -> {
                ((Material) inv.getArgument(0)).setId(1L);
                return 1;
            });

            Material result = materialService.create(dto);

            assertEquals("HJ202607040001", result.getPartNo());
            assertEquals(MaterialStatus.DRAFT, result.getStatus());
            assertEquals("V1.0", result.getVersionNo());
            verify(codeGenService).allocate(eq("PART"), any(), isNull(), anyString());
            verify(archiveTreeService).generateForPart(eq("HJ202607040001"), eq("COMPONENT"), isNull(), eq("PLASTIC"));
        }

        @Test
        @DisplayName("手工号重复拦截")
        void shouldRejectDuplicatePartNo() {
            MaterialDTO dto = new MaterialDTO();
            dto.setPartNo("EXISTING001");
            dto.setMaterialName("重复物料");
            dto.setMaterialType(MaterialType.HARDWARE);
            when(materialMapper.selectCount(any())).thenReturn(1L);
            assertThrows(BusinessException.class, () -> materialService.create(dto));
        }

        @Test
        @DisplayName("管理员可手工指定唯一料号")
        void shouldCreateWithManualPartNo() {
            MaterialDTO dto = new MaterialDTO();
            dto.setPartNo("CUSTOM001");
            dto.setMaterialName("定制物料");
            dto.setMaterialType(MaterialType.STANDARD);
            when(materialMapper.selectCount(any())).thenReturn(0L);
            when(materialMapper.insert(any(Material.class))).thenAnswer(inv -> {
                ((Material) inv.getArgument(0)).setId(2L);
                return 1;
            });

            Material result = materialService.create(dto);
            assertEquals("CUSTOM001", result.getPartNo());
            verify(codeGenService, never()).allocate(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("修改与状态")
    class UpdateAndStatus {

        @Test
        @DisplayName("草稿可改")
        void shouldAllowUpdateInDraft() {
            MaterialDTO dto = new MaterialDTO();
            dto.setId(1L);
            dto.setMaterialName("修改后名称");
            dto.setMaterialType(MaterialType.FINISHED);
            when(materialMapper.selectById(1L)).thenReturn(testMaterial);
            Material result = materialService.update(dto);
            assertEquals("修改后名称", result.getMaterialName());
        }

        @Test
        @DisplayName("已发布禁止直接改")
        void shouldRejectUpdateWhenReleased() {
            testMaterial.setStatus(MaterialStatus.RELEASED);
            MaterialDTO dto = new MaterialDTO();
            dto.setId(1L);
            dto.setMaterialName("x");
            when(materialMapper.selectById(1L)).thenReturn(testMaterial);
            assertThrows(BusinessException.class, () -> materialService.update(dto));
        }

        @Test
        @DisplayName("草稿->评审")
        void shouldTransitionToReviewing() {
            when(materialMapper.selectById(1L)).thenReturn(testMaterial);
            materialService.submitReview(1L);
            assertEquals(MaterialStatus.IN_REVIEW, testMaterial.getStatus());
        }

        @Test
        @DisplayName("发布写快照")
        void shouldReleaseAndSnapshot() throws Exception {
            when(materialMapper.selectById(1L)).thenReturn(testMaterial);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            materialService.release(1L);
            assertEquals(MaterialStatus.RELEASED, testMaterial.getStatus());
            verify(materialVersionMapper).insert(any());
        }

        @Test
        @DisplayName("非草稿禁止提审")
        void shouldRejectReviewFromNonDraft() {
            testMaterial.setStatus(MaterialStatus.RELEASED);
            when(materialMapper.selectById(1L)).thenReturn(testMaterial);
            assertThrows(BusinessException.class, () -> materialService.submitReview(1L));
        }
    }

    @Test
    @DisplayName("删除已发布拒绝")
    void shouldRejectDeleteReleased() {
        testMaterial.setStatus(MaterialStatus.RELEASED);
        when(materialMapper.selectById(1L)).thenReturn(testMaterial);
        assertThrows(BusinessException.class, () -> materialService.delete(1L));
    }

    @Nested
    @DisplayName("ECN 生效升版 (Phase 0 止血 1/4)")
    class ApplyEcnEffect {

        @Test
        @DisplayName("版号落库 + 快照写入 + 流转留痕, 一次调用内完成")
        void bumpsVersionAndWritesSnapshot() throws Exception {
            testMaterial.setStatus(MaterialStatus.CHANGING);
            testMaterial.setVersionNo("V1.0");
            when(materialMapper.selectById(1L)).thenReturn(testMaterial);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            materialService.applyEcnEffect(1L, "V1.1", "ECN202607040001", false);

            // 1) version_no 真正落到实体并 update
            ArgumentCaptor<Material> mc = ArgumentCaptor.forClass(Material.class);
            verify(materialMapper).updateById(mc.capture());
            assertEquals("V1.1", mc.getValue().getVersionNo());
            assertEquals(MaterialStatus.RELEASED, mc.getValue().getStatus());
            // 2) 快照落库 (文档称 plm_part_version, 本仓库实体为 plm_material_version)
            ArgumentCaptor<MaterialVersion> vc = ArgumentCaptor.forClass(MaterialVersion.class);
            verify(materialVersionMapper).insert(vc.capture());
            assertEquals("V1.1", vc.getValue().getVersionNo());
            assertEquals("ECN202607040001", vc.getValue().getEcnNo());
            assertEquals("{}", vc.getValue().getSnapshot());
            // 3) 流转留痕
            verify(lifecycleService).recordHistory(eq("PART"), eq("HJ202607040001"), eq("CHANGING"),
                    eq("RELEASED"), eq("ecn_effect"), any(), any());
        }

        @Test
        @DisplayName("缺少生效版本号 → 拒绝升版, 不写快照")
        void rejectsMissingVersion() {
            testMaterial.setStatus(MaterialStatus.CHANGING);
            testMaterial.setVersionNo(null);
            when(materialMapper.selectById(1L)).thenReturn(testMaterial);

            assertThrows(BusinessException.class,
                    () -> materialService.applyEcnEffect(1L, "", "ECN202607040001", false));

            verify(materialMapper, never()).updateById(any(Material.class));
            verify(materialVersionMapper, never()).insert(any(MaterialVersion.class));
        }

        @Test
        @DisplayName("快照写入失败 → 抛出, 由调用方事务整体回滚")
        void snapshotFailurePropagates() throws Exception {
            testMaterial.setStatus(MaterialStatus.CHANGING);
            testMaterial.setVersionNo("V1.0");
            when(materialMapper.selectById(1L)).thenReturn(testMaterial);
            when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("io"));

            assertThrows(BusinessException.class,
                    () -> materialService.applyEcnEffect(1L, "V1.1", "ECN202607040001", false));
        }
    }
}
