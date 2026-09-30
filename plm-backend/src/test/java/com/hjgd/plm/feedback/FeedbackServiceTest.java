package com.hjgd.plm.feedback;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.feedback.dto.FeedbackQueryDTO;
import com.hjgd.plm.feedback.dto.FeedbackSubmitDTO;
import com.hjgd.plm.feedback.dto.FeedbackTransitionDTO;
import com.hjgd.plm.feedback.dto.FeedbackVO;
import com.hjgd.plm.feedback.entity.Feedback;
import com.hjgd.plm.feedback.mapper.FeedbackMapper;
import com.hjgd.plm.feedback.service.impl.FeedbackServiceImpl;
import com.hjgd.plm.system.entity.SysUser;
import com.hjgd.plm.system.mapper.SysUserMapper;
import com.hjgd.plm.system.service.SequenceService;
import org.junit.jupiter.api.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("全局反馈模块测试(AC F4-F14)")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedbackServiceTest {

    private static final String NO = "FB-20260930-0001";

    @Mock private FeedbackMapper feedbackMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private SequenceService sequenceService;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks private FeedbackServiceImpl feedbackService;

    /** 单元测试无 MyBatis 上下文,需手动注册实体元数据,否则 LambdaQueryWrapper 无法解析列名 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Feedback.class);
    }

    @BeforeEach
    void setUp() {
        loginAs(1L, List.of("ENGINEER"));

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(sequenceService.nextNo("FEEDBACK_NO")).thenReturn(NO);
        when(feedbackMapper.selectCount(any())).thenReturn(0L);
        when(feedbackMapper.insert(any(Feedback.class))).thenAnswer(inv -> {
            Feedback f = inv.getArgument(0);
            f.setId(100L);
            f.setCreatedAt(LocalDateTime.now());
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId, List<String> permissions) {
        SysUser u = new SysUser();
        u.setId(userId);
        u.setUsername("user" + userId);
        u.setRealName("用户" + userId);
        u.setStatus(1);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(u, List.of("ENGINEER"), permissions), null, List.of()));
    }

    private FeedbackSubmitDTO dto(String type, String content, String expected) {
        FeedbackSubmitDTO d = new FeedbackSubmitDTO();
        d.setType(type);
        d.setContent(content);
        d.setExpectedResult(expected);
        d.setPagePath("/material/list");
        return d;
    }

    private String content(int len) {
        return "内".repeat(len);
    }

    // ---------- F4/F5/F6/F8 校验 ----------

    @Nested
    @DisplayName("字段校验")
    class Validation {

        @Test
        @DisplayName("F9/F10 合法提交落库一条并返回回执编号")
        void shouldSubmitAndReturnReceipt() {
            var receipt = feedbackService.submit(dto("BUG", content(10), null));

            assertEquals(NO, receipt.getFeedbackNo());
            assertNotNull(receipt.getCreatedAt());
            verify(feedbackMapper, times(1)).insert(any(Feedback.class));
        }

        @Test
        @DisplayName("F5 内容 9 字符拒绝、10 字符放行")
        void contentMinBoundary() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> feedbackService.submit(dto("BUG", content(9), null)));
            assertTrue(ex.getMessage().contains("10-2000"));

            assertDoesNotThrow(() -> feedbackService.submit(dto("BUG", content(10), null)));
        }

        @Test
        @DisplayName("F5 内容 2000 字符放行、2001 字符拒绝")
        void contentMaxBoundary() {
            assertDoesNotThrow(() -> feedbackService.submit(dto("BUG", content(2000), null)));

            assertThrows(BusinessException.class,
                    () -> feedbackService.submit(dto("BUG", content(2001), null)));
        }

        @Test
        @DisplayName("F5 纯空白内容视为空")
        void blankContentRejected() {
            assertThrows(BusinessException.class,
                    () -> feedbackService.submit(dto("BUG", "          ", null)));
        }

        @Test
        @DisplayName("F4 类型缺省或非法被拒绝")
        void typeRequired() {
            assertThrows(BusinessException.class, () -> feedbackService.submit(dto(null, content(10), null)));
            assertThrows(BusinessException.class, () -> feedbackService.submit(dto("OTHER", content(10), null)));
        }

        @Test
        @DisplayName("F6 需求类型期望结果必填,缺陷类型可空")
        void expectedResultRequiredOnlyForRequirement() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> feedbackService.submit(dto("REQUIREMENT", content(10), null)));
            assertTrue(ex.getMessage().contains("期望结果"));

            assertDoesNotThrow(() -> feedbackService.submit(dto("BUG", content(10), null)));
        }

        @Test
        @DisplayName("F8 多字段同时非法时一次性列出全部错误且不落库")
        void listsAllErrorsAtOnce() {
            FeedbackSubmitDTO bad = dto("REQUIREMENT", content(3), "x".repeat(2001));
            bad.setContact("c".repeat(101));

            BusinessException ex = assertThrows(BusinessException.class, () -> feedbackService.submit(bad));

            assertTrue(ex.getMessage().contains("10-2000"), ex.getMessage());
            assertTrue(ex.getMessage().contains("期望结果长度"), ex.getMessage());
            assertTrue(ex.getMessage().contains("联系方式长度"), ex.getMessage());
            verify(feedbackMapper, never()).insert(any(Feedback.class));
        }
    }

    // ---------- F11 去重 ----------

    @Nested
    @DisplayName("重复提交")
    class Dedup {

        @Test
        @DisplayName("F11 5 分钟内同内容同页面命中数据库记录时拦截且不落库")
        void blocksDuplicateInWindow() {
            when(feedbackMapper.selectCount(any())).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> feedbackService.submit(dto("BUG", content(10), null)));

            assertEquals(13001, ex.getCode());
            verify(feedbackMapper, never()).insert(any(Feedback.class));
        }

        @Test
        @DisplayName("F11 Redis 去重锁命中时拦截且不落库")
        void blocksWhenRedisLockHeld() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);

            assertThrows(BusinessException.class, () -> feedbackService.submit(dto("BUG", content(10), null)));
            verify(feedbackMapper, never()).insert(any(Feedback.class));
        }

        @Test
        @DisplayName("Redis 不可用时降级为数据库判定,不影响提交")
        void degradesWhenRedisUnavailable() {
            when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

            var receipt = feedbackService.submit(dto("BUG", content(10), null));

            assertEquals(NO, receipt.getFeedbackNo());
        }

        @Test
        @DisplayName("落库失败时释放去重锁,允许原样重试")
        void releasesLockWhenInsertFails() {
            when(feedbackMapper.insert(any(Feedback.class))).thenThrow(new RuntimeException("db down"));

            assertThrows(RuntimeException.class, () -> feedbackService.submit(dto("BUG", content(10), null)));
            verify(stringRedisTemplate, times(1)).delete(anyString());
        }
    }

    // ---------- F13 状态机 ----------

    @Nested
    @DisplayName("状态流转")
    class StateMachine {

        private Feedback pending() {
            Feedback f = new Feedback();
            f.setId(1L);
            f.setStatus(FeedbackConstants.STATUS_PENDING);
            return f;
        }

        private FeedbackTransitionDTO target(String status) {
            FeedbackTransitionDTO d = new FeedbackTransitionDTO();
            d.setTargetStatus(status);
            d.setNote("对照 E1-E5 的评估说明");
            return d;
        }

        @Test
        @DisplayName("F13 待分析 → 评估中")
        void pendingToEvaluating() {
            when(feedbackMapper.selectById(1L)).thenReturn(pending());

            feedbackService.transition(1L, target(FeedbackConstants.STATUS_EVALUATING));

            verify(feedbackMapper).updateById(argThat(f ->
                    FeedbackConstants.STATUS_EVALUATING.equals(f.getStatus()) && f.getEvaluatedAt() != null));
        }

        @Test
        @DisplayName("F13 待分析不可直接立项")
        void pendingCannotJumpToInitiated() {
            when(feedbackMapper.selectById(1L)).thenReturn(pending());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> feedbackService.transition(1L, target(FeedbackConstants.STATUS_INITIATED)));

            assertEquals(13002, ex.getCode());
            verify(feedbackMapper, never()).updateById(any(Feedback.class));
        }

        @Test
        @DisplayName("F13 终态不可再流转")
        void terminalIsClosed() {
            Feedback done = pending();
            done.setStatus(FeedbackConstants.STATUS_RETURNED);
            when(feedbackMapper.selectById(1L)).thenReturn(done);

            assertThrows(BusinessException.class,
                    () -> feedbackService.transition(1L, target(FeedbackConstants.STATUS_EVALUATING)));
        }

        @Test
        @DisplayName("F13 流转必须填写原因/结论")
        void noteRequired() {
            when(feedbackMapper.selectById(1L)).thenReturn(pending());
            FeedbackTransitionDTO d = target(FeedbackConstants.STATUS_EVALUATING);
            d.setNote("   ");

            assertThrows(BusinessException.class, () -> feedbackService.transition(1L, d));
        }

        @Test
        @DisplayName("F13 立项必须回填关联 issue 编号")
        void relatedIssueRequiredOnInitiated() {
            Feedback evaluating = pending();
            evaluating.setStatus(FeedbackConstants.STATUS_EVALUATING);
            when(feedbackMapper.selectById(1L)).thenReturn(evaluating);

            assertThrows(BusinessException.class,
                    () -> feedbackService.transition(1L, target(FeedbackConstants.STATUS_INITIATED)));

            FeedbackTransitionDTO ok = target(FeedbackConstants.STATUS_INITIATED);
            ok.setRelatedIssueNo("PAND-999");
            assertDoesNotThrow(() -> feedbackService.transition(1L, ok));
        }
    }

    // ---------- F14 查询 ----------

    @Nested
    @DisplayName("凭编号查询")
    class Query {

        @Test
        @DisplayName("F14 非本人且非总控按不存在处理,不泄露联系方式")
        void othersFeedbackHidden() {
            Feedback fb = new Feedback();
            fb.setId(1L);
            fb.setFeedbackNo(NO);
            fb.setSubmitterId(999L);
            fb.setContact("13800000000");
            when(feedbackMapper.selectOne(any())).thenReturn(fb);

            assertThrows(BusinessException.class, () -> feedbackService.getByNo(NO));
        }

        @Test
        @DisplayName("F14 本人可查状态,联系方式对非总控隐藏")
        void ownerSeesStatusWithoutContact() {
            Feedback fb = new Feedback();
            fb.setId(1L);
            fb.setFeedbackNo(NO);
            fb.setSubmitterId(1L);
            fb.setContact("13800000000");
            fb.setStatus(FeedbackConstants.STATUS_EVALUATING);
            when(feedbackMapper.selectOne(any())).thenReturn(fb);

            var vo = feedbackService.getByNo(NO);

            assertEquals(FeedbackConstants.STATUS_EVALUATING, vo.getStatus());
            assertNull(vo.getContact());
        }

        @Test
        @DisplayName("F7 总控可见联系方式")
        void manageSeesContact() {
            loginAs(2L, List.of("feedback:manage"));
            Feedback fb = new Feedback();
            fb.setId(1L);
            fb.setFeedbackNo(NO);
            fb.setSubmitterId(1L);
            fb.setContact("13800000000");
            when(feedbackMapper.selectOne(any())).thenReturn(fb);

            assertEquals("13800000000", feedbackService.getByNo(NO).getContact());
        }
    }

    @Nested
    @DisplayName("总控分页与筛选")
    class Paging {

        @BeforeEach
        void stubPage() {
            when(feedbackMapper.selectPage(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("F16a 不带任何筛选条件可分页读取(回归:时间参数为空不得抛错)")
        void pagingWithoutFilters() {
            assertNotNull(feedbackService.page(new FeedbackQueryDTO()));
        }

        @Test
        @DisplayName("F16b 结束时间早于开始时间被拒绝并提示")
        void endBeforeStartRejected() {
            FeedbackQueryDTO q = new FeedbackQueryDTO();
            q.setDateFrom("2026-09-30");
            q.setDateTo("2026-09-01");

            assertThrows(BusinessException.class, () -> feedbackService.page(q));
        }

        @Test
        @DisplayName("F16b 起止同日时区间包含当日全部记录")
        void sameDayCoversWholeDay() {
            FeedbackQueryDTO q = new FeedbackQueryDTO();
            q.setDateFrom("2026-09-30");
            q.setDateTo("2026-09-30");

            feedbackService.page(q);

            ArgumentCaptor<LambdaQueryWrapper<Feedback>> captor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(feedbackMapper).selectPage(any(), captor.capture());
            // 条件值是在构建 SQL 段时才写入 paramNameValuePairs,需先触发一次
            captor.getValue().getTargetSql();
            Collection<Object> values = captor.getValue().getParamNameValuePairs().values();
            assertTrue(values.contains(LocalDateTime.of(2026, 9, 30, 0, 0, 0)));
            assertTrue(values.contains(LocalDateTime.of(2026, 9, 30, 23, 59, 59, 999_999_999)));
        }
    }

    @Nested
    @DisplayName("落库字段完整性")
    class Persistence {

        @Test
        @DisplayName("插入的实体必须带 deleted=0,否则后续 deleted=0 的查询查不到该行")
        void insertedEntityCarriesLogicDeleteFlag() {
            FeedbackSubmitDTO dto = new FeedbackSubmitDTO();
            dto.setType("BUG");
            dto.setContent("物料列表页翻页后筛选条件被重置,需要保留。");
            dto.setPagePath("/material/list");

            feedbackService.submit(dto);

            ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
            verify(feedbackMapper).insert(captor.capture());
            assertEquals(0, captor.getValue().getDeleted());
        }
    }
}
