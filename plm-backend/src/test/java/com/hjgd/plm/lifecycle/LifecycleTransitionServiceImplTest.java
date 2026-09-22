package com.hjgd.plm.lifecycle;

import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.bom.entity.Bom;
import com.hjgd.plm.bom.service.BomService;
import com.hjgd.plm.common.ApiErrorCodes;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.event.service.DomainEventService;
import com.hjgd.plm.lifecycle.service.LifecycleService;
import com.hjgd.plm.lifecycle.service.LifecycleTransitionService;
import com.hjgd.plm.lifecycle.service.impl.LifecycleTransitionServiceImpl;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.enums.MaterialStatus;
import com.hjgd.plm.material.service.MaterialService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 0 止血 4/4: 通用流转 = 守卫 + 动作 + 事件。
 * 覆盖非法跳转、越权、force 语义、矩阵与实现漂移、未实现动作。
 */
@DisplayName("通用生命周期流转 (守卫+动作+事件)")
@ExtendWith(MockitoExtension.class)
class LifecycleTransitionServiceImplTest {

    @Mock private LifecycleService lifecycleService;
    @Mock private MaterialService materialService;
    @Mock private BomService bomService;
    @Mock private DomainEventService domainEventService;

    @InjectMocks private LifecycleTransitionServiceImpl service;

    private Material draftPart;

    @BeforeEach
    void setUp() {
        draftPart = new Material();
        draftPart.setId(1L);
        draftPart.setPartNo("HJ001");
        draftPart.setStatus(MaterialStatus.DRAFT);

        authenticate(List.of("ENGINEER"));
    }

    private void authenticate(List<String> roles) {
        LoginUser user = mock(LoginUser.class);
        lenient().when(user.getRealName()).thenReturn("测试工程师");
        lenient().when(user.getUserId()).thenReturn(1L);
        lenient().when(user.getUsername()).thenReturn("engineer");
        lenient().when(user.getRoles()).thenReturn(roles);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    }

    private Material materialWith(MaterialStatus status) {
        Material m = new Material();
        m.setId(1L);
        m.setPartNo("HJ001");
        m.setStatus(status);
        return m;
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("守卫")
    class Guards {

        @Test
        @DisplayName("非法跳转: 矩阵拒绝 → 409 LIFECYCLE_DENIED, 状态/审计/事件均不产生")
        void illegalTransitionRejected() {
            when(materialService.getByPartNo("HJ001")).thenReturn(draftPart);
            when(lifecycleService.resolveToState("PART", "DRAFT", "seal"))
                    .thenThrow(new BusinessException(409, "[" + ApiErrorCodes.LIFECYCLE_DENIED + "] 不允许 DRAFT→SEALED"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.transition("PART", "HJ001", "seal", "越级封存", false));

            assertEquals(409, ex.getCode());
            assertTrue(ex.getMessage().contains(ApiErrorCodes.LIFECYCLE_DENIED));
            verify(materialService, never()).seal(anyLong());
            verify(lifecycleService, never()).recordHistory(any(), any(), any(), any(), any(), any(), any());
            verify(domainEventService, never()).publish(any(), any(), any(), any());
        }

        @Test
        @DisplayName("越权: 角色不满足 → 403 并列出所需角色, 动作不执行")
        void roleDeniedRejected() {
            when(materialService.getByPartNo("HJ001")).thenReturn(draftPart);
            when(lifecycleService.resolveToState("PART", "DRAFT", "release")).thenReturn("RELEASED");
            when(lifecycleService.isRoleAllowed("PART", "DRAFT", "release")).thenReturn(false);
            when(lifecycleService.allowedRoles("PART", "DRAFT", "release")).thenReturn(Set.of("RD_LEAD", "ADMIN"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.transition("PART", "HJ001", "release", "发布", false));

            assertEquals(403, ex.getCode());
            assertTrue(ex.getMessage().contains("RD_LEAD"));
            verify(materialService, never()).release(anyLong());
            verify(domainEventService, never()).publish(any(), any(), any(), any());
        }

        @Test
        @DisplayName("对象不存在 → 404, 不进守卫")
        void unknownPartNotFound() {
            when(materialService.getByPartNo("HJ999")).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.transition("PART", "HJ999", "release", null, false));

            assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
            verify(lifecycleService, never()).resolveToState(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("不支持的 objectType → 400")
        void unsupportedObjectType() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.transition("SOP", "SOP001", "release", null, false));

            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("objectType"));
        }

        @Test
        @DisplayName("force=true 非管理员 → 403")
        void forceRequiresAdmin() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.transition("PART", "HJ001", "obsolete", "强制作废", true));

            assertEquals(403, ex.getCode());
            verify(materialService, never()).obsolete(anyLong());
        }

        @Test
        @DisplayName("force=true 管理员 → 400 明确告知覆盖守卫未实现(不静默放行)")
        void forceExplicitlyUnsupportedForAdmin() {
            authenticate(List.of("ADMIN"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.transition("PART", "HJ001", "obsolete", "强制作废", true));

            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("尚未实现"));
            verify(materialService, never()).obsolete(anyLong());
        }
    }

    @Nested
    @DisplayName("动作 + 事件")
    class ActionAndEvent {

        @Test
        @DisplayName("PART DRAFT --release--> RELEASED: 动作执行 + 留痕 + 事件")
        void partReleaseSucceeds() {
            when(materialService.getByPartNo("HJ001")).thenReturn(draftPart);
            when(lifecycleService.resolveToState("PART", "DRAFT", "release")).thenReturn("RELEASED");
            when(lifecycleService.isRoleAllowed("PART", "DRAFT", "release")).thenReturn(true);
            when(materialService.getById(1L)).thenReturn(materialWith(MaterialStatus.RELEASED));

            LifecycleTransitionService.TransitionResult result =
                    service.transition("PART", "HJ001", "release", "正式发布", false);

            assertEquals("DRAFT", result.fromState());
            assertEquals("RELEASED", result.toState());
            verify(materialService).release(1L);
            verify(lifecycleService).recordHistory("PART", "HJ001", "DRAFT", "RELEASED", "release",
                    "测试工程师", "正式发布");
            verify(domainEventService).publish(eq("lifecycle.transitioned"), eq("PART"), eq("HJ001"), any());
        }

        @Test
        @DisplayName("BOM release 走同一守卫与动作")
        void bomReleaseSucceeds() {
            Bom bom = new Bom();
            bom.setId(5L);
            bom.setBomNo("BOM202607040001");
            bom.setStatus("DRAFT");
            Bom released = new Bom();
            released.setStatus("RELEASED");
            when(bomService.getByBomNo("BOM202607040001")).thenReturn(bom);
            when(lifecycleService.resolveToState("BOM", "DRAFT", "release")).thenReturn("RELEASED");
            when(lifecycleService.isRoleAllowed("BOM", "DRAFT", "release")).thenReturn(true);
            when(bomService.getById(5L)).thenReturn(released);

            LifecycleTransitionService.TransitionResult result =
                    service.transition("BOM", "BOM202607040001", "release", "发布BOM", false);

            assertEquals("RELEASED", result.toState());
            verify(bomService).release(5L);
            verify(lifecycleService).recordHistory("BOM", "BOM202607040001", "DRAFT", "RELEASED", "release",
                    "测试工程师", "发布BOM");
        }

        @Test
        @DisplayName("矩阵与实际落库状态漂移 → 500 暴露, 不改状态")
        void matrixDriftExposed() {
            when(materialService.getByPartNo("HJ001")).thenReturn(draftPart);
            // 矩阵说是 OBSOLETE, 但 submit_review 动作实际落 IN_REVIEW
            when(lifecycleService.resolveToState("PART", "DRAFT", "submit_review")).thenReturn("OBSOLETE");
            when(lifecycleService.isRoleAllowed("PART", "DRAFT", "submit_review")).thenReturn(true);
            when(materialService.getById(1L)).thenReturn(materialWith(MaterialStatus.IN_REVIEW));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.transition("PART", "HJ001", "submit_review", null, false));

            assertEquals(500, ex.getCode());
            assertTrue(ex.getMessage().contains("不一致"));
            verify(lifecycleService, never()).recordHistory(any(), any(), any(), any(), any(), any(), any());
            verify(domainEventService, never()).publish(any(), any(), any(), any());
        }

        @Test
        @DisplayName("矩阵中有规则但本版本无实现的动作 → 409, 不静默改状态")
        void unimplementedActionRejected() {
            when(materialService.getByPartNo("HJ001")).thenReturn(draftPart);
            when(lifecycleService.resolveToState("PART", "DRAFT", "finish_change")).thenReturn("RELEASED");
            when(lifecycleService.isRoleAllowed("PART", "DRAFT", "finish_change")).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.transition("PART", "HJ001", "finish_change", null, false));

            assertEquals(409, ex.getCode());
            assertTrue(ex.getMessage().contains("动作无实现"));
            verify(materialService, never()).release(anyLong());
        }
    }
}
