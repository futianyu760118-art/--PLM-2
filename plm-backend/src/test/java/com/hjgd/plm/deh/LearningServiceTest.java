package com.hjgd.plm.deh;

import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.deh.entity.Learning;
import com.hjgd.plm.deh.mapper.LearningMapper;
import com.hjgd.plm.deh.service.LearningService;
import com.hjgd.plm.system.entity.SysUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * F16 学习发布门槛：独立验证 + 无收益/退化/越权 + 可版本化回退，三者缺一不得发布。
 */
@DisplayName("F16 DEH 学习发布门槛")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LearningServiceTest {

    @Mock private LearningMapper learningMapper;
    @InjectMocks private LearningService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String realName) {
        SysUser u = new SysUser();
        u.setUsername(realName.toLowerCase());
        u.setRealName(realName);
        LoginUser lu = new LoginUser(u, List.of("ENGINEER"), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(lu, null, lu.getAuthorities()));
    }

    private Learning learning(String id, String status, String createdBy, String rollbackRef) {
        Learning l = new Learning();
        l.setLearningId(id);
        l.setStatus(status);
        l.setCreatedBy(createdBy);
        l.setRollbackRef(rollbackRef);
        l.setContent("CF 口径改法");
        return l;
    }

    @Test
    @DisplayName("三门槛齐备 → 发布成功")
    void publishWithAllGatesPassed() {
        loginAs("品保");
        when(learningMapper.selectById("LRN-1")).thenReturn(learning("LRN-1", "draft", "架构师", "learn-v0"));

        Learning l = service.publish("LRN-1",
                new LearningService.PublishCommand("品保", "improved", null));

        assertEquals("published", l.getStatus());
        assertEquals("品保", l.getVerifiedBy());
        assertNotNull(l.getPublishedAt());
    }

    @Test
    @DisplayName("产出人自评不得发布 —— 独立验证是硬门槛")
    void publishRejectedWhenSelfVerified() {
        loginAs("架构师");
        when(learningMapper.selectById("LRN-1")).thenReturn(learning("LRN-1", "draft", "架构师", "learn-v0"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.publish("LRN-1", new LearningService.PublishCommand("架构师", "improved", null)));
        assertTrue(ex.getMessage().contains("LEARNING_NOT_PUBLISHABLE"));
        assertTrue(ex.getMessage().contains("独立验证"));
        verify(learningMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("自评者冒名他人也不得发布 —— 验证人取服务端身份，不听客户端自称")
    void publishRejectedWhenSelfVerifierSpoofsIdentity() {
        loginAs("架构师");
        when(learningMapper.selectById("LRN-1")).thenReturn(learning("LRN-1", "draft", "架构师", "learn-v0"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.publish("LRN-1", new LearningService.PublishCommand("品保", "improved", null)));
        assertTrue(ex.getMessage().contains("实际登录 架构师"), ex.getMessage());
        assertTrue(ex.getMessage().contains("独立验证"));
        verify(learningMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("回显的验证人与登录身份不符 → 拒绝，不静默记错验证人")
    void publishRejectedWhenClaimedVerifierMismatches() {
        loginAs("品保");
        when(learningMapper.selectById("LRN-1")).thenReturn(learning("LRN-1", "draft", "架构师", "learn-v0"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.publish("LRN-1", new LearningService.PublishCommand("李四", "improved", null)));
        assertTrue(ex.getMessage().contains("李四"));
        assertTrue(ex.getMessage().contains("品保"));
    }

    @Test
    @DisplayName("不填验证人也行：验证人由登录身份确定，缺省不再是否决项")
    void publishWithoutClaimedVerifierUsesLoginIdentity() {
        loginAs("品保");
        when(learningMapper.selectById("LRN-1")).thenReturn(learning("LRN-1", "draft", "架构师", "learn-v0"));

        Learning l = service.publish("LRN-1", new LearningService.PublishCommand(null, "improved", null));

        assertEquals("published", l.getStatus());
        assertEquals("品保", l.getVerifiedBy());
    }

    @Test
    @DisplayName("判定为退化或越权 → 不得发布")
    void publishRejectedForDegradedOrOverreach() {
        loginAs("品保");
        when(learningMapper.selectById("LRN-1")).thenReturn(learning("LRN-1", "draft", "架构师", "learn-v0"));

        assertTrue(assertThrows(BusinessException.class, () ->
                service.publish("LRN-1", new LearningService.PublishCommand("品保", "degraded", null)))
                .getMessage().contains("degraded"));
        assertTrue(assertThrows(BusinessException.class, () ->
                service.publish("LRN-1", new LearningService.PublishCommand("品保", "overreach", null)))
                .getMessage().contains("overreach"));
    }

    @Test
    @DisplayName("no_benefit → 不得发布（无收益与退化/越权同列阻断项，PAND-154 R-1）")
    void noBenefitIsBlocking() {
        loginAs("品保");
        when(learningMapper.selectById("LRN-1")).thenReturn(learning("LRN-1", "draft", "架构师", "learn-v0"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.publish("LRN-1", new LearningService.PublishCommand("品保", "no_benefit", null)));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("LEARNING_NOT_PUBLISHABLE"), ex.getMessage());
        assertTrue(ex.getMessage().contains("no_benefit"), ex.getMessage());
        verify(learningMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("判定为 neutral → 照常发布（中性不等于无收益）")
    void neutralVerdictPublishes() {
        loginAs("品保");
        when(learningMapper.selectById("LRN-1")).thenReturn(learning("LRN-1", "draft", "架构师", "learn-v0"));

        Learning l = service.publish("LRN-1",
                new LearningService.PublishCommand("品保", "neutral", null));

        assertEquals("published", l.getStatus());
    }

    @Test
    @DisplayName("无回退引用 → 不可回退的经验不得发布")
    void publishRejectedWithoutRollbackRef() {
        loginAs("品保");
        when(learningMapper.selectById("LRN-1")).thenReturn(learning("LRN-1", "draft", "架构师", null));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.publish("LRN-1", new LearningService.PublishCommand("品保", "improved", null)));
        assertTrue(ex.getMessage().contains("rollback_ref"));
    }

    @Test
    @DisplayName("发布入参可临时补回退引用")
    void publishAcceptsRollbackRefFromCommand() {
        loginAs("品保");
        when(learningMapper.selectById("LRN-1")).thenReturn(learning("LRN-1", "draft", "架构师", null));

        Learning l = service.publish("LRN-1",
                new LearningService.PublishCommand("品保", "improved", "learn-v1"));

        assertEquals("published", l.getStatus());
        assertEquals("learn-v1", l.getRollbackRef());
    }

    @Test
    @DisplayName("非法收益判定取值 → 拒绝")
    void publishRejectedForUnknownVerdict() {
        loginAs("品保");
        when(learningMapper.selectById("LRN-1")).thenReturn(learning("LRN-1", "draft", "架构师", "learn-v0"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.publish("LRN-1", new LearningService.PublishCommand("品保", "amazing", null)));
        assertTrue(ex.getMessage().contains("非法取值"));
    }

    @Test
    @DisplayName("回退保留记录：published→rolled_back，不删除")
    void rollbackKeepsRecord() {
        loginAs("品保");
        when(learningMapper.selectById("LRN-1")).thenReturn(learning("LRN-1", "published", "架构师", "learn-v0"));

        Learning l = service.rollback("LRN-1", "线上收益未复现");

        assertEquals("rolled_back", l.getStatus());
        assertTrue(l.getContent().contains("线上收益未复现"));
        verify(learningMapper, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("未发布的学习不可回退")
    void rollbackRejectedForDraft() {
        loginAs("品保");
        when(learningMapper.selectById("LRN-1")).thenReturn(learning("LRN-1", "draft", "架构师", "learn-v0"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.rollback("LRN-1", "x"));
        assertTrue(ex.getMessage().contains("不可回退"));
    }
}
