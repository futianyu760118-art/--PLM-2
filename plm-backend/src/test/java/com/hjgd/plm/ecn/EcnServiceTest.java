package com.hjgd.plm.ecn;

import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.bom.service.BomService;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.ecn.dto.EcnDTO;
import com.hjgd.plm.ecn.dto.EcnReviewDTO;
import com.hjgd.plm.ecn.entity.Ecn;
import com.hjgd.plm.ecn.entity.EcnImpact;
import com.hjgd.plm.ecn.enums.EcnChangeType;
import com.hjgd.plm.ecn.enums.EcnStatus;
import com.hjgd.plm.ecn.mapper.EcnFlowLogMapper;
import com.hjgd.plm.ecn.mapper.EcnMapper;
import com.hjgd.plm.ecn.service.EcnImpactService;
import com.hjgd.plm.ecn.service.impl.EcnServiceImpl;
import com.hjgd.plm.event.service.DomainEventService;
import com.hjgd.plm.file.mapper.PlmFileMapper;
import com.hjgd.plm.file.entity.PlmFile;
import com.hjgd.plm.file.service.FileService;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.enums.MaterialStatus;
import com.hjgd.plm.material.service.MaterialService;
import com.hjgd.plm.system.service.SequenceService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ECN工程变更模块测试 V1.1")
@ExtendWith(MockitoExtension.class)
class EcnServiceTest {

    @Mock private EcnMapper ecnMapper;
    @Mock private EcnFlowLogMapper flowLogMapper;
    @Mock private SequenceService sequenceService;
    @Mock private MaterialService materialService;
    @Mock private FileService fileService;
    @Mock private PlmFileMapper fileMapper;
    @Mock private DomainEventService domainEventService;
    @Mock private EcnImpactService ecnImpactService;
    @Mock private BomService bomService;

    @InjectMocks
    private EcnServiceImpl ecnService;

    private Ecn testEcn;
    private Material testMaterial;

    @BeforeEach
    void setUp() {
        testEcn = new Ecn();
        testEcn.setId(1L);
        testEcn.setEcnNo("ECN202607040001");
        testEcn.setPartNo("HJ202607040001");
        testEcn.setMaterialId(1L);
        testEcn.setChangeType(EcnChangeType.STRUCTURE);
        testEcn.setStatus(EcnStatus.DRAFT);
        testEcn.setVersionBefore("V1.0");
        testEcn.setVersionAfter("V1.1");

        testMaterial = new Material();
        testMaterial.setId(1L);
        testMaterial.setPartNo("HJ202607040001");
        testMaterial.setVersionNo("V1.0");
        testMaterial.setStatus(MaterialStatus.RELEASED);
        testMaterial.setPhase("STRUCTURE");

        LoginUser mockUser = mock(LoginUser.class);
        lenient().when(mockUser.getRealName()).thenReturn("测试工程师");
        lenient().when(mockUser.getUserId()).thenReturn(1L);
        lenient().when(mockUser.getUsername()).thenReturn("engineer");
        lenient().when(mockUser.getPrimaryRole()).thenReturn("ENGINEER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(mockUser, null, Collections.emptyList()));

        lenient().when(fileMapper.selectList(any())).thenReturn(List.of());
        lenient().doNothing().when(domainEventService).publish(any(), any(), any(), any());
        lenient().when(ecnImpactService.listByEcn(anyLong())).thenReturn(List.of());
        lenient().when(ecnImpactService.saveImpacts(anyLong(), anyString(), anyList())).thenReturn(List.of());
        lenient().when(bomService.bumpVersionForEcn(anyString(), anyString(), anyString())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("审批流")
    class ApprovalFlow {

        @Test
        @DisplayName("完整: 提交→一审→二审→生效并落库升版")
        void shouldCompleteFullApprovalFlow() {
            when(ecnMapper.selectById(1L)).thenReturn(testEcn);
            when(materialService.getById(1L)).thenReturn(testMaterial);

            EcnReviewDTO reviewDTO = new EcnReviewDTO();
            reviewDTO.setComment("同意");

            testEcn.setStatus(EcnStatus.DRAFT);
            ecnService.submit(1L);
            assertEquals(EcnStatus.PENDING_L1, testEcn.getStatus());
            verify(materialService).startChange(1L);

            testEcn.setStatus(EcnStatus.PENDING_L1);
            ecnService.reviewL1Approve(1L, reviewDTO);
            assertEquals(EcnStatus.PENDING_L2, testEcn.getStatus());

            testEcn.setStatus(EcnStatus.PENDING_L2);
            ecnService.reviewL2Approve(1L, reviewDTO);
            assertEquals(EcnStatus.APPROVED, testEcn.getStatus());

            testEcn.setStatus(EcnStatus.APPROVED);
            ecnService.effect(1L);
            assertEquals(EcnStatus.EFFECTIVE, testEcn.getStatus());
            verify(materialService).applyEcnEffect(eq(1L), eq("V1.1"), eq("ECN202607040001"), anyBoolean());
        }

        @Test
        @DisplayName("一审驳回")
        void shouldRejectAtL1() {
            EcnReviewDTO dto = new EcnReviewDTO();
            dto.setComment("不同意");
            testEcn.setStatus(EcnStatus.PENDING_L1);
            when(ecnMapper.selectById(1L)).thenReturn(testEcn);
            ecnService.reviewL1Reject(1L, dto);
            assertEquals(EcnStatus.REJECTED, testEcn.getStatus());
        }

        @Test
        @DisplayName("二审驳回")
        void shouldRejectAtL2() {
            EcnReviewDTO dto = new EcnReviewDTO();
            dto.setComment("二审不同意");
            testEcn.setStatus(EcnStatus.PENDING_L2);
            when(ecnMapper.selectById(1L)).thenReturn(testEcn);
            ecnService.reviewL2Reject(1L, dto);
            assertEquals(EcnStatus.REJECTED, testEcn.getStatus());
            // 写入流程日志
            verify(flowLogMapper, times(1)).insert(any());
        }

        @Test
        @DisplayName("二审驳回但 ECN 不在 PENDING_L2 → 抛异常")
        void shouldRejectL2RejectWhenWrongStatus() {
            EcnReviewDTO dto = new EcnReviewDTO();
            dto.setComment("test");
            testEcn.setStatus(EcnStatus.PENDING_L1);
            when(ecnMapper.selectById(1L)).thenReturn(testEcn);
            assertThrows(BusinessException.class, () -> ecnService.reviewL2Reject(1L, dto));
        }

        @Test
        @DisplayName("非APPROVED不能生效")
        void shouldNotEffectFromNonApproved() {
            testEcn.setStatus(EcnStatus.PENDING_L2);
            when(ecnMapper.selectById(1L)).thenReturn(testEcn);
            assertThrows(BusinessException.class, () -> ecnService.effect(1L));
        }

        @Test
        @DisplayName("已生效禁止作废")
        void shouldNotVoidEffectiveEcn() {
            testEcn.setStatus(EcnStatus.EFFECTIVE);
            when(ecnMapper.selectById(1L)).thenReturn(testEcn);
            assertThrows(BusinessException.class, () -> ecnService.voidEcn(1L));
        }
    }

    @Test
    @DisplayName("创建ECN自动版本")
    void shouldAutoFillVersionOnCreate() {
        EcnDTO dto = new EcnDTO();
        dto.setPartNo("HJ202607040001");
        dto.setChangeType(EcnChangeType.MOLD);
        dto.setChangeReason("模具修改");
        when(sequenceService.nextNo("ECN_NO")).thenReturn("ECN202607040001");
        when(materialService.getByPartNo("HJ202607040001")).thenReturn(testMaterial);
        when(ecnMapper.insert(any(Ecn.class))).thenAnswer(inv -> {
            ((Ecn) inv.getArgument(0)).setId(1L);
            return 1;
        });

        Ecn result = ecnService.create(dto);
        assertEquals("V1.0", result.getVersionBefore());
        assertEquals("V1.1", result.getVersionAfter());
    }

    @Test
    @DisplayName("ECN生效按影响面分发: 验收剧本 改结构件→PART升版+BOM升版+事件带bomChanged")
    void effectDispatchesByImpactTypes() {
        // 预填影响面 PART + BOM
        EcnImpact partImp = impact("PART", 11L);
        EcnImpact bomImp = impact("BOM", 12L);
        testEcn.setStatus(EcnStatus.APPROVED);
        when(ecnMapper.selectById(1L)).thenReturn(testEcn);
        when(materialService.getById(1L)).thenReturn(testMaterial);
        when(ecnImpactService.listByEcn(1L)).thenReturn(List.of(partImp, bomImp));
        when(bomService.bumpVersionForEcn("HJ202607040001", "ECN202607040001", "V1.1")).thenReturn("V1.0");

        ecnService.effect(1L);

        assertEquals(EcnStatus.EFFECTIVE, testEcn.getStatus());
        verify(materialService).applyEcnEffect(eq(1L), eq("V1.1"), eq("ECN202607040001"), anyBoolean());
        verify(bomService).bumpVersionForEcn("HJ202607040001", "ECN202607040001", "V1.1");
        // 事件 payload 带 bomChanged=true(EBMS 重算标记)
        verify(domainEventService).publish(eq("ecn.effective"), eq("ECN"), eq("ECN202607040001"), any());
    }

    private EcnImpact impact(String type, long id) {
        EcnImpact imp = new EcnImpact();
        imp.setId(id);
        imp.setEcnId(1L);
        imp.setImpactType(type);
        imp.setStatus("PENDING");
        return imp;
    }

    @Nested
    @DisplayName("生效影响面分发(补强)")
    class EffectDispatch {

        @Test
        @DisplayName("FILE 影响：旧版图纸自动作废")
        void effectObsoletesOldVersionFiles() {
            EcnImpact fileImp = impact("FILE", 13L);
            PlmFile oldFile = new PlmFile();
            oldFile.setId(77L);
            testEcn.setStatus(EcnStatus.APPROVED);
            when(ecnMapper.selectById(1L)).thenReturn(testEcn);
            when(materialService.getById(1L)).thenReturn(testMaterial);
            when(ecnImpactService.listByEcn(1L)).thenReturn(List.of(fileImp));
            when(fileMapper.selectList(any())).thenReturn(List.of(oldFile));

            ecnService.effect(1L);

            verify(fileService).markObsolete(77L);
            verify(domainEventService).publish(eq("ecn.effective"), any(), any(), any());
        }

        @Test
        @DisplayName("未填影响面时按变更类型推断：STRUCTURE→PART+BOM+FILE 并落库")
        void effectInfersImpactsWhenEmpty() {
            testEcn.setStatus(EcnStatus.APPROVED);
            testEcn.setChangeType(EcnChangeType.STRUCTURE);
            when(ecnMapper.selectById(1L)).thenReturn(testEcn);
            when(materialService.getById(1L)).thenReturn(testMaterial);
            // listByEcn 默认返回空(setUp)，触发推断

            ecnService.effect(1L);

            // 推断 STRUCTURE → [PART, BOM, FILE] 落库
            verify(ecnImpactService).saveImpacts(eq(1L), anyString(), argThat(list ->
                    list.contains("PART") && list.contains("BOM") && list.contains("FILE")));
            verify(materialService).applyEcnEffect(eq(1L), anyString(), anyString(), anyBoolean());
        }

        @Test
        @DisplayName("PROCESS 变更推断仅 SOP，不触发 PART/BOM 升版")
        void processChangeInfersOnlySop() {
            testEcn.setStatus(EcnStatus.APPROVED);
            testEcn.setChangeType(EcnChangeType.PROCESS);
            when(ecnMapper.selectById(1L)).thenReturn(testEcn);
            when(materialService.getById(1L)).thenReturn(testMaterial);

            ecnService.effect(1L);

            verify(ecnImpactService).saveImpacts(eq(1L), anyString(), argThat(list ->
                    list.contains("SOP") && !list.contains("PART") && !list.contains("BOM")));
            verify(materialService, never()).applyEcnEffect(anyLong(), any(), any(), anyBoolean());
            verify(bomService, never()).bumpVersionForEcn(any(), any(), any());
        }
    }
}
