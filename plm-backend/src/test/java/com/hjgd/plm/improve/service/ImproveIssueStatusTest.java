package com.hjgd.plm.improve.service;

import com.hjgd.plm.common.ApiErrorCodes;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.improve.entity.Issue;
import com.hjgd.plm.improve.mapper.ImproveActionMapper;
import com.hjgd.plm.improve.mapper.ImproveResultMapper;
import com.hjgd.plm.improve.mapper.InsightMapper;
import com.hjgd.plm.improve.mapper.IssueMapper;
import com.hjgd.plm.improve.mapper.StandardWorkMapper;
import com.hjgd.plm.system.service.SequenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 0 止血 4/4: 改进问题状态流转守卫。
 * 白名单外的跳转一律 409, 且必须拒绝在前 —— 不能先改状态再报错。
 */
@DisplayName("改进问题状态流转守卫")
@ExtendWith(MockitoExtension.class)
class ImproveIssueStatusTest {

    @Mock private IssueMapper issueMapper;
    @Mock private ImproveActionMapper actionMapper;
    @Mock private InsightMapper insightMapper;
    @Mock private ImproveResultMapper resultMapper;
    @Mock private StandardWorkMapper standardWorkMapper;
    @Mock private SequenceService sequenceService;

    @InjectMocks private ImproveService improveService;

    private Issue issueWith(String status) {
        Issue issue = new Issue();
        issue.setId(1L);
        issue.setIssueNo("ISSUE202607040001");
        issue.setStatus(status);
        return issue;
    }

    @Nested
    @DisplayName("合法路径")
    class Allowed {

        @Test
        @DisplayName("OPEN → ANALYZING 通过")
        void openToAnalyzing() {
            Issue issue = issueWith("OPEN");
            when(issueMapper.selectById(1L)).thenReturn(issue);

            improveService.updateIssueStatus(1L, "ANALYZING");

            assertEquals("ANALYZING", issue.getStatus());
            verify(issueMapper).updateById(issue);
        }

        @Test
        @DisplayName("VERIFY → ACTION 允许回退")
        void verifyBackToAction() {
            Issue issue = issueWith("VERIFY");
            when(issueMapper.selectById(1L)).thenReturn(issue);

            improveService.updateIssueStatus(1L, "ACTION");

            assertEquals("ACTION", issue.getStatus());
            assertNull(issue.getClosedAt(), "回退不应写关闭时间");
        }

        @Test
        @DisplayName("VERIFY → CLOSED 落关闭时间")
        void verifyToClosedStampsClosedAt() {
            Issue issue = issueWith("VERIFY");
            when(issueMapper.selectById(1L)).thenReturn(issue);

            improveService.updateIssueStatus(1L, "CLOSED");

            assertEquals("CLOSED", issue.getStatus());
            assertNotNull(issue.getClosedAt());
        }
    }

    @Nested
    @DisplayName("非法跳转")
    class Denied {

        @Test
        @DisplayName("OPEN → CLOSED 越级关闭 → 409 LIFECYCLE_DENIED 且不写库")
        void openToClosedDenied() {
            Issue issue = issueWith("OPEN");
            when(issueMapper.selectById(1L)).thenReturn(issue);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> improveService.updateIssueStatus(1L, "CLOSED"));

            assertEquals(409, ex.getCode());
            assertTrue(ex.getMessage().contains(ApiErrorCodes.LIFECYCLE_DENIED));
            assertEquals("OPEN", issue.getStatus());
            verify(issueMapper, never()).updateById(any(Issue.class));
        }

        @Test
        @DisplayName("终态 CLOSED 不可再流转")
        void closedIsTerminal() {
            Issue issue = issueWith("CLOSED");
            when(issueMapper.selectById(1L)).thenReturn(issue);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> improveService.updateIssueStatus(1L, "ANALYZING"));

            assertEquals(409, ex.getCode());
            verify(issueMapper, never()).updateById(any(Issue.class));
        }

        @Test
        @DisplayName("未知目标状态 → 409")
        void unknownTargetDenied() {
            Issue issue = issueWith("OPEN");
            when(issueMapper.selectById(1L)).thenReturn(issue);

            assertThrows(BusinessException.class, () -> improveService.updateIssueStatus(1L, "DONE"));
            verify(issueMapper, never()).updateById(any(Issue.class));
        }

        @Test
        @DisplayName("问题不存在 → 404")
        void issueNotFound() {
            when(issueMapper.selectById(99L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> improveService.updateIssueStatus(99L, "ANALYZING"));

            assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        }
    }
}
