package com.hjgd.plm.improve;

import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.improve.entity.ImproveResult;
import com.hjgd.plm.improve.entity.Issue;
import com.hjgd.plm.improve.entity.StandardWork;
import com.hjgd.plm.improve.mapper.ImproveActionMapper;
import com.hjgd.plm.improve.mapper.ImproveResultMapper;
import com.hjgd.plm.improve.mapper.InsightMapper;
import com.hjgd.plm.improve.mapper.IssueMapper;
import com.hjgd.plm.improve.mapper.StandardWorkMapper;
import com.hjgd.plm.improve.service.ImproveService;
import com.hjgd.plm.system.service.SequenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 改善成效回写 + 标准作业固化。
 */
@DisplayName("改善成效/标准作业 V1.1")
@ExtendWith(MockitoExtension.class)
class ImproveResultStandardWorkTest {

    @Mock private IssueMapper issueMapper;
    @Mock private ImproveActionMapper actionMapper;
    @Mock private InsightMapper insightMapper;
    @Mock private ImproveResultMapper resultMapper;
    @Mock private StandardWorkMapper standardWorkMapper;
    @Mock private SequenceService sequenceService;
    @InjectMocks private ImproveService improveService;

    @BeforeEach
    void setUp() {
        LoginUser mockUser = mock(LoginUser.class);
        lenient().when(mockUser.getUserId()).thenReturn(7L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(mockUser, null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("记录改善成效：默认窗口 + effective 置空待验证")
    void shouldRecordResultWithDefaultWindow() {
        ImproveResult r = new ImproveResult();
        r.setIssueId(10L);
        r.setKpiCode("M_DQ_SCORE");
        r.setBeforeValue(new BigDecimal("70"));
        r.setAfterValue(new BigDecimal("88"));

        improveService.recordResult(r);

        assertNotNull(r.getWindowFrom());
        assertNotNull(r.getWindowTo());
        assertNull(r.getEffective());
        verify(resultMapper).insert(r);
    }

    @Test
    @DisplayName("验证改善成效：写 verifiedBy/verifiedAt + effective")
    void shouldVerifyResult() {
        ImproveResult r = new ImproveResult();
        r.setId(1L);
        when(resultMapper.selectById(1L)).thenReturn(r);

        ImproveResult out = improveService.verifyResult(1L, true);

        assertEquals(Boolean.TRUE, out.getEffective());
        assertNotNull(out.getVerifiedAt());
        assertEquals(7L, out.getVerifiedBy());
        verify(resultMapper).updateById(r);
    }

    @Test
    @DisplayName("固化为标准作业：分配 swNo + DRAFT + V1")
    void shouldCreateStandardWorkFromIssue() {
        StandardWork sw = new StandardWork();
        sw.setTitle("投光灯 IP 填写规范");
        sw.setFromIssueId(10L);
        sw.setContent("成品发布前必须填 IP 等级...");
        when(sequenceService.nextNo("ISSUE_NO")).thenReturn("IQ202607220001");

        improveService.createStandardWork(sw);

        assertEquals("IQ202607220001", sw.getSwNo());
        assertEquals("DRAFT", sw.getStatus());
        assertEquals("V1", sw.getVersionNo());
        verify(standardWorkMapper).insert(sw);
    }

    @Test
    @DisplayName("发布标准作业：status=PUBLISHED + publishedAt")
    void shouldPublishStandardWork() {
        StandardWork sw = new StandardWork();
        sw.setId(3L);
        sw.setStatus("DRAFT");
        when(standardWorkMapper.selectById(3L)).thenReturn(sw);

        improveService.publishStandardWork(3L);

        assertEquals("PUBLISHED", sw.getStatus());
        assertNotNull(sw.getPublishedAt());
        verify(standardWorkMapper).updateById(sw);
    }
}
