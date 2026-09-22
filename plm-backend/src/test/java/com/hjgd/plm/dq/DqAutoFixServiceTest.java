package com.hjgd.plm.dq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.dq.dto.DqRunResult;
import com.hjgd.plm.dq.service.DqAutoFixService;
import com.hjgd.plm.dq.service.DataQualityService;
import com.hjgd.plm.improve.entity.Issue;
import com.hjgd.plm.improve.service.ImproveService;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.enums.MaterialStatus;
import com.hjgd.plm.material.service.MaterialService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.atLeastOnce;

/**
 * V1.1 DQ 自动智能修复测试。
 * 使用 LENIENT 严格度,允许部分 stub 在某些分支不被使用。
 */
@DisplayName("DQ 自动智能修复 V1.1")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DqAutoFixServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private MaterialService materialService;
    @Mock private DataQualityService dataQualityService;
    @Mock private ImproveService improveService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private DqAutoFixService service;

    @org.junit.jupiter.api.BeforeEach
    void init() {
        service = new DqAutoFixService(jdbcTemplate, objectMapper, materialService, dataQualityService, improveService);
    }

    @Test
    @DisplayName("FILL_DEFAULT 策略: unit 填 PCS → 重跑DQ → 关闭债务")
    void fillDefaultResolvesAndClosesDebt() {
        // 债务 OPEN
        when(jdbcTemplate.queryForMap(contains("FROM plm_dq_debt WHERE id="), eq(1L)))
                .thenReturn(Map.of(
                        "id", 1, "object_type", "PART", "object_id", "HJ001",
                        "rule_code", "PART_UNIT_WARN", "severity", "WARN", "status", "OPEN"));
        // 规则启用 + 策略
        when(jdbcTemplate.queryForMap(contains("FROM plm_dq_rule"), eq("PART_UNIT_WARN")))
                .thenReturn(Map.of(
                        "rule_code", "PART_UNIT_WARN",
                        "auto_fix_strategy", "{\"strategy\":\"FILL_DEFAULT\",\"field\":\"unit\",\"defaultValue\":\"PCS\"}"));
        // 当前分数
        when(jdbcTemplate.queryForObject(contains("score_0_100"), eq(BigDecimal.class), any(), any()))
                .thenReturn(new BigDecimal("50"));
        // 料号
        Material m = new Material();
        m.setId(10L);
        m.setPartNo("HJ001");
        m.setMaterialName("成品");
        when(materialService.getByPartNo("HJ001")).thenReturn(m);
        // 重跑DQ 后分数提升且该规则PASS
        DqRunResult rerun = DqRunResult.builder().objectType("PART").objectId("HJ001")
                .score(85).blockCount(0).warnCount(0).infoCount(0).items(List.of()).build();
        when(dataQualityService.runForPart(any(Material.class), eq("AUTO_FIX"))).thenReturn(rerun);
        // 记录 attempt 拿到 id
        when(jdbcTemplate.queryForObject(eq("SELECT currval('plm_dq_fix_attempt_id_seq')"), eq(Long.class)))
                .thenReturn(42L);

        DqAutoFixService.FixAttemptResult res = service.tryFix(1L, "USER");

        assertTrue(res.resolved, "FILL_DEFAULT 应当被标记 resolved");
        assertEquals(42L, res.attemptId);
        assertEquals("FILL_DEFAULT", res.strategy);
        // 校验 updateQuietly 被调用(直接对 m 写入 unit='PCS')
        ArgumentCaptor<Material> matCap = ArgumentCaptor.forClass(Material.class);
        verify(materialService).updateQuietly(matCap.capture());
        assertEquals("PCS", matCap.getValue().getUnit());
        // 债务关闭 + waive_reason
        verify(jdbcTemplate).update(contains("UPDATE plm_dq_debt SET status='CLOSED'"), any(), eq(1L));
    }

    @Test
    @DisplayName("NO_OP 策略: 不修改数据,仅记录 attempt 且不关闭债务")
    void noOpDoesNotMutate() {
        when(jdbcTemplate.queryForMap(contains("FROM plm_dq_debt"), eq(2L)))
                .thenReturn(Map.of(
                        "id", 2, "object_type", "PART", "object_id", "HJ002",
                        "rule_code", "PART_NO_FORMAT", "severity", "INFO", "status", "OPEN"));
        when(jdbcTemplate.queryForMap(contains("FROM plm_dq_rule"), eq("PART_NO_FORMAT")))
                .thenReturn(Map.of(
                        "rule_code", "PART_NO_FORMAT",
                        "auto_fix_strategy", "{\"strategy\":\"NO_OP\",\"reason\":\"料号格式需业务人工确认\"}"));
        // 必须找到 part
        Material m = new Material();
        m.setId(20L); m.setPartNo("HJ002");
        when(materialService.getByPartNo("HJ002")).thenReturn(m);
        // 回检仍跑: NO_OP 未修改数据,该规则仍 FAIL,总分低
        DqRunResult rerun = DqRunResult.builder().objectType("PART").objectId("HJ002")
                .score(60).blockCount(0).warnCount(0).infoCount(1)
                .items(List.of(DqRunResult.Item.builder()
                        .ruleCode("PART_NO_FORMAT").severity("INFO").result("FAIL").message("格式不合规").build()))
                .build();
        when(dataQualityService.runForPart(any(Material.class), eq("AUTO_FIX"))).thenReturn(rerun);
        when(jdbcTemplate.queryForObject(eq("SELECT currval('plm_dq_fix_attempt_id_seq')"), eq(Long.class)))
                .thenReturn(10L);

        DqAutoFixService.FixAttemptResult res = service.tryFix(2L, "USER");

        assertFalse(res.resolved);
        assertEquals("NO_OP", res.skipReason);
        // INFO 不升级 issue,所以不动 improveService
        verify(improveService, never()).createIssue(any());
        verify(materialService, never()).updateQuietly(any(Material.class));
        verify(jdbcTemplate, never()).update(contains("status='CLOSED'"), any(), anyLong());
        // 但 attempt 仍记录(全路径审计)
        verify(jdbcTemplate, atLeastOnce()).update(contains("INSERT INTO plm_dq_fix_attempt"), any(Object[].class));
    }

    @Test
    @DisplayName("LINK_TO_ISSUE 策略: 创建正式 issue 关联回债务")
    void linkToIssueCreatesIssue() {
        when(jdbcTemplate.queryForMap(contains("FROM plm_dq_debt"), eq(3L)))
                .thenReturn(Map.of(
                        "id", 3, "object_type", "PART", "object_id", "HJ003",
                        "rule_code", "BOM_NO_CYCLE", "severity", "BLOCK", "status", "OPEN"));
        when(jdbcTemplate.queryForMap(contains("FROM plm_dq_rule"), eq("BOM_NO_CYCLE")))
                .thenReturn(Map.of(
                        "rule_code", "BOM_NO_CYCLE",
                        "auto_fix_strategy", "{\"strategy\":\"LINK_TO_ISSUE\",\"severity\":\"MEDIUM\",\"title\":\"BOM循环引用\"}"));
        Material m = new Material();
        m.setId(11L); m.setPartNo("HJ003"); m.setMaterialName("测试");
        when(materialService.getByPartNo("HJ003")).thenReturn(m);
        // 重跑 DQ
        DqRunResult rerun = DqRunResult.builder().objectType("PART").objectId("HJ003")
                .score(75).blockCount(0).warnCount(1).infoCount(0).items(List.of()).build();
        when(dataQualityService.runForPart(any(Material.class), eq("AUTO_FIX"))).thenReturn(rerun);
        when(jdbcTemplate.queryForObject(eq("SELECT currval('plm_dq_fix_attempt_id_seq')"), eq(Long.class)))
                .thenReturn(50L);

        DqAutoFixService.FixAttemptResult res = service.tryFix(3L, "USER");

        assertEquals("LINK_TO_ISSUE", res.strategy);
        // 创建 issue
        ArgumentCaptor<Issue> issCap = ArgumentCaptor.forClass(Issue.class);
        verify(improveService).createIssue(issCap.capture());
        Issue issue = issCap.getValue();
        assertEquals("DQ_AUTO", issue.getSourceType());
        assertEquals("BOM循环引用", issue.getTitle());
        assertEquals("PART", issue.getObjectType());
    }

    @Test
    @DisplayName("debt.status != OPEN → 直接跳过")
    void closedDebtSkipped() {
        when(jdbcTemplate.queryForMap(contains("FROM plm_dq_debt"), eq(4L)))
                .thenReturn(Map.of(
                        "id", 4, "object_type", "PART", "object_id", "HJ004",
                        "rule_code", "X", "severity", "WARN", "status", "CLOSED"));

        DqAutoFixService.FixAttemptResult res = service.tryFix(4L, "USER");

        assertFalse(res.resolved);
        assertEquals("DEBT_NOT_OPEN", res.skipReason);
    }

    @Test
    @DisplayName("object_type 非 PART → 跳过(暂不支持 BOM/ECN)")
    void unsupportedObjectType() {
        when(jdbcTemplate.queryForMap(contains("FROM plm_dq_debt"), eq(5L)))
                .thenReturn(Map.of(
                        "id", 5, "object_type", "BOM", "object_id", "BOM001",
                        "rule_code", "X", "severity", "WARN", "status", "OPEN"));

        DqAutoFixService.FixAttemptResult res = service.tryFix(5L, "USER");

        assertEquals("UNSUPPORTED_OBJECT_TYPE", res.skipReason);
    }

    @Test
    @DisplayName("规则未启用或策略为空 → 跳过")
    void ruleDisabledOrNoStrategy() {
        when(jdbcTemplate.queryForMap(contains("FROM plm_dq_debt"), eq(6L)))
                .thenReturn(Map.of(
                        "id", 6, "object_type", "PART", "object_id", "HJ006",
                        "rule_code", "Y", "severity", "INFO", "status", "OPEN"));
        when(jdbcTemplate.queryForMap(contains("FROM plm_dq_rule"), eq("Y")))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        DqAutoFixService.FixAttemptResult res = service.tryFix(6L, "USER");

        assertEquals("RULE_DISABLED", res.skipReason);
    }

    @Test
    @DisplayName("MARK_OBSOLETE 策略: 设置状态为 OBSOLETE + updateQuietly")
    void markObsolete() {
        when(jdbcTemplate.queryForMap(contains("FROM plm_dq_debt"), eq(7L)))
                .thenReturn(Map.of(
                        "id", 7, "object_type", "PART", "object_id", "HJ007",
                        "rule_code", "Z", "severity", "WARN", "status", "OPEN"));
        when(jdbcTemplate.queryForMap(contains("FROM plm_dq_rule"), eq("Z")))
                .thenReturn(Map.of(
                        "rule_code", "Z",
                        "auto_fix_strategy", "{\"strategy\":\"MARK_OBSOLETE\"}"));
        when(jdbcTemplate.queryForObject(contains("score_0_100"), eq(BigDecimal.class), any(), any()))
                .thenReturn(new BigDecimal("70"));
        Material m = new Material();
        m.setId(13L); m.setPartNo("HJ007"); m.setStatus(MaterialStatus.RELEASED);
        when(materialService.getByPartNo("HJ007")).thenReturn(m);
        DqRunResult rerun = DqRunResult.builder().objectType("PART").objectId("HJ007")
                .score(85).blockCount(0).warnCount(0).infoCount(0).items(List.of()).build();
        when(dataQualityService.runForPart(any(Material.class), eq("AUTO_FIX"))).thenReturn(rerun);
        when(jdbcTemplate.queryForObject(eq("SELECT currval('plm_dq_fix_attempt_id_seq')"), eq(Long.class)))
                .thenReturn(99L);

        DqAutoFixService.FixAttemptResult res = service.tryFix(7L, "USER");

        assertEquals("MARK_OBSOLETE", res.strategy);
        assertTrue(res.resolved);
        ArgumentCaptor<Material> matCap = ArgumentCaptor.forClass(Material.class);
        verify(materialService).updateQuietly(matCap.capture());
        assertEquals(MaterialStatus.OBSOLETE, matCap.getValue().getStatus());
    }

    @Test
    @DisplayName("修复后规则仍 FAIL → 不关闭债务(回检验证)")
    void noScoreImprovementDoesNotClose() {
        when(jdbcTemplate.queryForMap(contains("FROM plm_dq_debt"), eq(8L)))
                .thenReturn(Map.of(
                        "id", 8, "object_type", "PART", "object_id", "HJ008",
                        "rule_code", "PART_VERSION_DEFAULT", "severity", "WARN", "status", "OPEN"));
        when(jdbcTemplate.queryForMap(contains("FROM plm_dq_rule"), eq("PART_VERSION_DEFAULT")))
                .thenReturn(Map.of(
                        "rule_code", "PART_VERSION_DEFAULT",
                        "auto_fix_strategy", "{\"strategy\":\"FILL_DEFAULT\",\"field\":\"versionNo\",\"defaultValue\":\"V1.0\"}"));
        when(jdbcTemplate.queryForObject(contains("score_0_100"), eq(BigDecimal.class), any(), any()))
                .thenReturn(new BigDecimal("90"));
        Material m = new Material();
        m.setId(14L); m.setPartNo("HJ008");
        when(materialService.getByPartNo("HJ008")).thenReturn(m);
        DqRunResult rerun = DqRunResult.builder().objectType("PART").objectId("HJ008")
                .score(60).blockCount(0).warnCount(1).infoCount(0)
                .items(List.of(DqRunResult.Item.builder()
                        .ruleCode("PART_VERSION_DEFAULT").severity("WARN").result("FAIL").message("x").build()))
                .build();
        when(dataQualityService.runForPart(any(Material.class), eq("AUTO_FIX"))).thenReturn(rerun);
        when(jdbcTemplate.queryForObject(eq("SELECT currval('plm_dq_fix_attempt_id_seq')"), eq(Long.class)))
                .thenReturn(100L);

        DqAutoFixService.FixAttemptResult res = service.tryFix(8L, "USER");

        assertFalse(res.resolved);
        verify(jdbcTemplate, never()).update(contains("status='CLOSED'"), any(), anyLong());
    }

    @Test
    @DisplayName("批量: fixTopDebts 走 tryFix × N 并返回已解决数")
    void batchCalls() {
        when(jdbcTemplate.queryForList(contains("FROM plm_dq_debt WHERE status='OPEN'"), eq(3)))
                .thenReturn(List.of(
                        Map.of("id", 1),
                        Map.of("id", 2),
                        Map.of("id", 3)));
        // 全部走 NOT_OPEN 跳过
        when(jdbcTemplate.queryForMap(contains("FROM plm_dq_debt"), eq(1L)))
                .thenReturn(Map.of("id", 1, "object_type", "PART", "object_id", "X", "rule_code", "X", "severity", "WARN", "status", "CLOSED"));
        when(jdbcTemplate.queryForMap(contains("FROM plm_dq_debt"), eq(2L)))
                .thenReturn(Map.of("id", 2, "object_type", "BOM", "object_id", "B", "rule_code", "X", "severity", "WARN", "status", "OPEN"));
        when(jdbcTemplate.queryForMap(contains("FROM plm_dq_debt"), eq(3L)))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        int resolved = service.fixTopDebts(3, "AUTO");

        // 三次调用,全部未解决
        assertEquals(0, resolved);
    }
}