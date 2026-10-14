package com.hjgd.plm.ledger;

import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.file.entity.PlmFile;
import com.hjgd.plm.file.service.FileService;
import com.hjgd.plm.ledger.dto.BatchResultVO;
import com.hjgd.plm.ledger.dto.LedgerDetailVO;
import com.hjgd.plm.ledger.dto.LedgerQuery;
import com.hjgd.plm.ledger.dto.LedgerRequest;
import com.hjgd.plm.ledger.dto.LedgerLinkVO;
import com.hjgd.plm.ledger.entity.PlmLedger;
import com.hjgd.plm.ledger.entity.PlmLedgerAttachment;
import com.hjgd.plm.ledger.entity.PlmLedgerLink;
import com.hjgd.plm.ledger.mapper.PlmLedgerAttachmentMapper;
import com.hjgd.plm.ledger.mapper.PlmLedgerLinkMapper;
import com.hjgd.plm.ledger.mapper.PlmLedgerMapper;
import com.hjgd.plm.ledger.service.LedgerAuditService;
import com.hjgd.plm.ledger.service.LedgerNotifyService;
import com.hjgd.plm.ledger.service.LedgerObjectResolver;
import com.hjgd.plm.ledger.service.impl.LedgerServiceImpl;
import com.hjgd.plm.system.service.SequenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 能力线 A 台账服务：AC-A1（单界面/附件上限与白名单）、AC-A2（必填与草稿）、
 * AC-A4（关联与解除）、AC-A5（批量与筛选边界）、AC-A6（越权拦截）的边界验证。
 */
@DisplayName("证据台账服务（能力线 A）")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LedgerServiceTest {

    @Mock private PlmLedgerMapper ledgerMapper;
    @Mock private PlmLedgerAttachmentMapper attachmentMapper;
    @Mock private PlmLedgerLinkMapper linkMapper;
    @Mock private LedgerObjectResolver resolver;
    @Mock private LedgerAuditService auditService;
    @Mock private LedgerNotifyService notifyService;
    @Mock private SequenceService sequenceService;
    @Mock private FileService fileService;
    @Mock private JdbcTemplate jdbcTemplate;

    private LedgerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LedgerServiceImpl(ledgerMapper, attachmentMapper, linkMapper, resolver,
                auditService, notifyService, LedgerTestSupport.props(), sequenceService,
                fileService, jdbcTemplate);
        LedgerTestSupport.login("ledger:view", "ledger:add", "ledger:edit", "ledger:delete");
        // 字典与存在性查询
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), any())).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(1);
        when(sequenceService.nextNo("LEDGER_NO")).thenReturn("EV-20261014-0001");
        // 附件默认合法，个别用例再覆盖为越界/非白名单
        when(fileService.getById(anyLong())).thenReturn(file(0L, ".pdf", 1024L));
        // 关联对象默认有效
        when(resolver.supportedTypes()).thenReturn(Set.of("PROJECT", "MATERIAL"));
        when(resolver.resolve(anyString(), anyString()))
                .thenReturn(new LedgerObjectResolver.ObjectRef("PJ-001", "PJ-001", "投光灯项目", true, null));
    }

    @AfterEach
    void tearDown() {
        LedgerTestSupport.logout();
    }

    private PlmFile file(Long id, String ext, long size) {
        PlmFile f = new PlmFile();
        f.setId(id);
        f.setFileName("report" + ext);
        f.setFileExt(ext);
        f.setFileSize(size);
        return f;
    }

    // ---------- AC-A1/A2 录入 ----------

    @Test
    @DisplayName("AC-A1-3 / AC-A2-5 新建成功：编号取序列（全局唯一来源）、状态「已提交」、附件与关联各写一次")
    void createWritesLedgerWithGeneratedNo() {
        when(fileService.getById(101L)).thenReturn(file(101L, ".pdf", 1024L));
        when(attachmentMapper.selectList(any())).thenReturn(List.of());
        when(linkMapper.selectList(any())).thenReturn(List.of());

        LedgerDetailVO vo = service.create(LedgerTestSupport.submitRequest(101L));

        assertEquals("EV-20261014-0001", vo.getLedger().getLedgerNo());
        assertEquals("SUBMITTED", vo.getLedger().getStatus());
        assertEquals(LedgerTestSupport.USER_ID, vo.getLedger().getUploadedBy());
        verify(ledgerMapper).insert(any(PlmLedger.class));
        verify(attachmentMapper).insert(any(PlmLedgerAttachment.class));
        verify(linkMapper).insert(any(PlmLedgerLink.class));
        verify(auditService).log(eq("CREATE"), eq("EV-20261014-0001"), isNull(), any());
    }

    @Test
    @DisplayName("AC-A2-7 草稿跳过必填校验：无名称/类型/责任人/发生时间也能落库")
    void draftSkipsRequiredFields() {
        LedgerRequest req = new LedgerRequest();
        req.setStatus("DRAFT");
        req.setRemark("先占位");

        LedgerDetailVO vo = service.create(req);

        assertEquals("DRAFT", vo.getLedger().getStatus());
        assertNull(vo.getLedger().getName());
        verify(ledgerMapper).insert(any(PlmLedger.class));
        verify(linkMapper, never()).insert(any(PlmLedgerLink.class));
    }

    @Test
    @DisplayName("AC-A2-2 提交态缺少证据名称 → 拒绝且不落库")
    void submitWithoutNameRejected() {
        LedgerRequest req = LedgerTestSupport.submitRequest(101L);
        req.setName("  ");

        BusinessException e = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(e.getMessage().contains("证据名称"));
        verify(ledgerMapper, never()).insert(any(PlmLedger.class));
    }

    @Test
    @DisplayName("AC-A2-4 发生时间晚于今日 → 拒绝")
    void futureOccurDateRejected() {
        LedgerRequest req = LedgerTestSupport.submitRequest(101L);
        req.setOccurDate(java.time.LocalDate.now().plusDays(1).toString());

        assertThrows(BusinessException.class, () -> service.create(req));
        verify(ledgerMapper, never()).insert(any(PlmLedger.class));
    }

    @Test
    @DisplayName("AC-A2-3 名称超长 → 拒绝")
    void overlongNameRejected() {
        LedgerRequest req = LedgerTestSupport.submitRequest(101L);
        req.setName("证".repeat(101));

        assertThrows(BusinessException.class, () -> service.create(req));
    }

    @Test
    @DisplayName("AC-A1-7 提交态无附件 → 拒绝")
    void submitWithoutAttachmentRejected() {
        LedgerRequest req = LedgerTestSupport.submitRequest();
        req.setFileIds(List.of());

        BusinessException e = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(e.getMessage().contains("附件"));
    }

    @Test
    @DisplayName("AC-A1-4 附件数超过上限 → 拒绝")
    void tooManyAttachmentsRejected() {
        LedgerRequest req = LedgerTestSupport.submitRequest(1L, 2L, 3L);
        req.setFileIds(List.of(1L, 2L, 3L)); // 上限 2

        BusinessException e = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(e.getMessage().contains("附件数"));
    }

    @Test
    @DisplayName("AC-A1-6 白名单外格式 → 拒绝")
    void extNotWhitelistedRejected() {
        when(fileService.getById(101L)).thenReturn(file(101L, ".exe", 10L));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.create(LedgerTestSupport.submitRequest(101L)));
        assertTrue(e.getMessage().contains("格式"));
    }

    @Test
    @DisplayName("AC-A1-5 单文件超过 200MB 上限 → 拒绝；恰好等于上限 → 通过")
    void fileSizeBoundary() {
        long max = LedgerTestSupport.props().getMaxFileSizeMb() * 1024 * 1024;
        when(fileService.getById(101L)).thenReturn(file(101L, ".pdf", max + 1));
        assertThrows(BusinessException.class, () -> service.create(LedgerTestSupport.submitRequest(101L)));

        when(fileService.getById(101L)).thenReturn(file(101L, ".pdf", max));
        when(attachmentMapper.selectList(any())).thenReturn(List.of());
        when(linkMapper.selectList(any())).thenReturn(List.of());
        assertDoesNotThrow(() -> service.create(LedgerTestSupport.submitRequest(101L)));
    }

    @Test
    @DisplayName("AC-A2-2 提交态无关联对象 → 拒绝")
    void submitWithoutLinkRejected() {
        LedgerRequest req = LedgerTestSupport.submitRequest(101L);
        req.setLinks(List.of());

        BusinessException e = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(e.getMessage().contains("关联"));
    }

    @Test
    @DisplayName("AC-A4-1 关联数超过上限 → 拒绝")
    void tooManyLinksRejected() {
        LedgerRequest req = LedgerTestSupport.submitRequest(101L);
        req.setLinks(List.of(LedgerTestSupport.link("PROJECT", "PJ-001"),
                LedgerTestSupport.link("PROJECT", "PJ-002"),
                LedgerTestSupport.link("PROJECT", "PJ-003"))); // 上限 2

        BusinessException e = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(e.getMessage().contains("关联对象"));
    }

    @Test
    @DisplayName("AC-A4-5 关联已删除对象 → 建关联时拒绝并给出原因")
    void linkToDeletedObjectRejected() {
        when(resolver.resolve("PROJECT", "PJ-404"))
                .thenReturn(new LedgerObjectResolver.ObjectRef("PJ-404", null, null, false, "DELETED"));
        LedgerRequest req = LedgerTestSupport.submitRequest(101L);
        req.setLinks(List.of(LedgerTestSupport.link("PROJECT", "PJ-404")));

        BusinessException e = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(e.getMessage().contains("已删除"));
    }

    // ---------- AC-A5 筛选 ----------

    @Test
    @DisplayName("AC-A5-3 发生时间起 > 止 → 拒绝")
    void occurRangeReversedRejected() {
        LedgerQuery q = new LedgerQuery();
        q.setOccurStart("2026-10-10");
        q.setOccurEnd("2026-10-01");

        assertThrows(BusinessException.class, () -> service.page(q));
        verify(ledgerMapper, never()).pageLedger(any(), any());
    }

    @Test
    @DisplayName("AC-A5-5 非档位分页尺寸回落到 20")
    void pageSizeFallsBackToWhitelist() {
        LedgerQuery q = new LedgerQuery();
        q.setPageSize(37);
        com.baomidou.mybatisplus.core.metadata.IPage<PlmLedger> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        when(ledgerMapper.pageLedger(any(), any(LedgerQuery.class))).thenReturn(page);

        PageResult<PlmLedger> result = service.page(q);

        assertNotNull(result);
        ArgumentCaptor<com.baomidou.mybatisplus.extension.plugins.pagination.Page<PlmLedger>> cap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class);
        verify(ledgerMapper).pageLedger(cap.capture(), any(LedgerQuery.class));
        assertEquals(20, cap.getValue().getSize());
    }

    @Test
    @DisplayName("AC-A6-1 非台账管理员：查询自动注入本部门 + 本人范围")
    void scopeInjectedForNonAdmin() {
        LedgerQuery q = new LedgerQuery();
        com.baomidou.mybatisplus.core.metadata.IPage<PlmLedger> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        when(ledgerMapper.pageLedger(any(), any(LedgerQuery.class))).thenReturn(page);

        service.page(q);

        assertEquals(LedgerTestSupport.DEPT_ID, q.getScopeDeptId());
        assertEquals(LedgerTestSupport.USER_ID, q.getScopeUserId());
    }

    @Test
    @DisplayName("AC-A2-2 责任人候选只回传选择所需字段（非管理员录入人可用）")
    void ownerCandidatesExposeOnlyPickerFields() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 7L);
        row.put("real_name", "张三");
        row.put("username", "zhangsan");
        row.put("dept_id", LedgerTestSupport.DEPT_ID);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

        List<Map<String, Object>> owners = service.searchOwners("张", 20);

        assertEquals(1, owners.size());
        assertEquals(7L, owners.get(0).get("id"));
        assertEquals("张三", owners.get(0).get("realName"));
        assertFalse(owners.get(0).containsKey("password"), "不得回传账号安全字段");
    }

    // ---------- AC-A3 模块侧证据区 ----------

    @Test
    @DisplayName("AC-A3-1 模块侧引用条目含编号/名称/类型/责任人/时间/附件入口")
    void byObjectCarriesSyncContent() {
        PlmLedger ledger = LedgerTestSupport.ledger(1L, "SUBMITTED");
        when(ledgerMapper.selectByObject("PROJECT", "PJ-001")).thenReturn(List.of(ledger));

        Map<String, Object> ownerRow = new LinkedHashMap<>();
        ownerRow.put("id", LedgerTestSupport.USER_ID);
        ownerRow.put("real_name", LedgerTestSupport.REAL_NAME);
        Map<String, Object> countRow = new LinkedHashMap<>();
        countRow.put("ledger_id", 1L);
        countRow.put("cnt", 2L);
        Map<String, Object> dictRow = new LinkedHashMap<>();
        dictRow.put("dict_value", "INSPECT_REPORT");
        dictRow.put("dict_label", "检验报告");
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql.contains("sys_user")) {
                return List.of(ownerRow);
            }
            if (sql.contains("plm_ledger_attachment")) {
                return List.of(countRow);
            }
            if (sql.contains("sys_dict")) {
                return List.of(dictRow);
            }
            return List.of();
        });

        List<com.hjgd.plm.ledger.dto.LedgerRefVO> refs = service.byObject("project", "PJ-001");

        assertEquals(1, refs.size());
        com.hjgd.plm.ledger.dto.LedgerRefVO ref = refs.get(0);
        assertEquals("EV-20261014-0001", ref.getLedgerNo());
        assertEquals(LedgerTestSupport.REAL_NAME, ref.getOwnerName());
        assertEquals("检验报告", ref.getTypeLabel());
        assertEquals(2L, ref.getAttachmentCount());
        assertEquals("/ledger/list?keyword=EV-20261014-0001", ref.getLink());
    }

    // ---------- AC-A4 解除关联 / 批量 ----------
    @Test
    @DisplayName("AC-A4-3 解除关联只删引用，不删台账")
    void unlinkKeepsLedger() {
        PlmLedger ledger = LedgerTestSupport.ledger(1L, "SUBMITTED");
        when(ledgerMapper.selectById(1L)).thenReturn(ledger);
        PlmLedgerLink link = new PlmLedgerLink();
        link.setId(50L);
        link.setLedgerId(1L);
        link.setObjectType("PROJECT");
        link.setObjectId("PJ-001");
        when(linkMapper.selectById(50L)).thenReturn(link);
        when(linkMapper.selectList(any())).thenReturn(List.of(link, new PlmLedgerLink()));

        service.removeLink(1L, 50L);

        verify(linkMapper).deleteById(50L);
        verify(ledgerMapper, never()).deleteById(anyLong());
        verify(auditService).log(eq("UNLINK"), eq("EV-20261014-0001"), any(), any());
    }

    @Test
    @DisplayName("AC-A4-4 提交态仅剩 1 个关联时不可再解除")
    void lastLinkCannotBeRemoved() {
        PlmLedger ledger = LedgerTestSupport.ledger(1L, "SUBMITTED");
        when(ledgerMapper.selectById(1L)).thenReturn(ledger);
        PlmLedgerLink link = new PlmLedgerLink();
        link.setId(50L);
        link.setLedgerId(1L);
        when(linkMapper.selectById(50L)).thenReturn(link);
        when(linkMapper.selectList(any())).thenReturn(List.of(link));

        BusinessException e = assertThrows(BusinessException.class, () -> service.removeLink(1L, 50L));
        assertTrue(e.getMessage().contains("至少保留"));
        verify(linkMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("AC-A5-7 批量关联：成功与失败分别计数并给出原因")
    void batchLinkCountsSuccessAndFailure() {
        PlmLedger ok = LedgerTestSupport.ledger(1L, "SUBMITTED");
        when(ledgerMapper.selectById(1L)).thenReturn(ok);
        when(ledgerMapper.selectById(2L)).thenReturn(null); // 不存在 → 失败
        when(linkMapper.selectList(any())).thenReturn(new ArrayList<>());

        BatchResultVO result = service.batchLink(List.of(1L, 2L),
                LedgerTestSupport.link("PROJECT", "PJ-001"));

        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailCount());
        assertEquals(1, result.getFailures().size());
        assertEquals(List.of(1L), result.getLedgerIds());
    }

    @Test
    @DisplayName("AC-A5-7 批量关联超过 100 条上限 → 整批拒绝")
    void batchLinkOverLimitRejected() {
        List<Long> ids = new ArrayList<>();
        for (long i = 1; i <= 101; i++) {
            ids.add(i);
        }
        assertThrows(BusinessException.class,
                () -> service.batchLink(ids, LedgerTestSupport.link("PROJECT", "PJ-001")));
    }

    // ---------- AC-A4-5 失效展示 ----------

    @Test
    @DisplayName("AC-A4-5 目标对象已作废 → 详情返回失效状态与原因并回写引用表")
    void obsoleteLinkMarkedInvalid() {
        PlmLedger ledger = LedgerTestSupport.ledger(1L, "SUBMITTED");
        when(ledgerMapper.selectById(1L)).thenReturn(ledger);
        when(attachmentMapper.selectList(any())).thenReturn(List.of());
        PlmLedgerLink link = new PlmLedgerLink();
        link.setId(60L);
        link.setLedgerId(1L);
        link.setObjectType("PROJECT");
        link.setObjectId("PJ-001");
        link.setObjectNo("PJ-001");
        link.setObjectName("投光灯项目");
        link.setLinkStatus("ACTIVE");
        when(linkMapper.selectList(any())).thenReturn(List.of(link));
        when(resolver.resolve("PROJECT", "PJ-001"))
                .thenReturn(new LedgerObjectResolver.ObjectRef("PJ-001", "PJ-001", "投光灯项目", false, "OBSOLETE"));

        LedgerDetailVO vo = service.detail(1L);

        LedgerLinkVO vo1 = vo.getLinks().get(0);
        assertEquals("INVALID", vo1.getLinkStatus());
        assertEquals("OBSOLETE", vo1.getInvalidReason());
        assertFalse(vo1.getObjectValid());
        assertEquals("投光灯项目", vo1.getObjectName());
        verify(linkMapper).updateById(argThat((PlmLedgerLink l) ->
                "INVALID".equals(l.getLinkStatus()) && "OBSOLETE".equals(l.getInvalidReason())));
    }

    // ---------- AC-A6-2 越权 ----------

    @Test
    @DisplayName("AC-A6-2 非本部门且非本人上传 → 编辑被拦截并记越权日志")
    void crossDeptEditDenied() {
        PlmLedger ledger = LedgerTestSupport.ledger(1L, "SUBMITTED");
        ledger.setUploadedBy(999L);
        ledger.setOwnerId(999L);
        ledger.setDeptId(888L);
        when(ledgerMapper.selectById(1L)).thenReturn(ledger);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.update(1L, LedgerTestSupport.submitRequest(101L)));
        assertEquals(403, e.getCode());
        verify(auditService).logDenied(eq("DENIED_WRITE"), eq("EV-20261014-0001"), anyString());
        verify(ledgerMapper, never()).updateById(any(PlmLedger.class));
    }

    @Test
    @DisplayName("已归档证据不可编辑")
    void archivedLedgerReadOnly() {
        when(ledgerMapper.selectById(1L)).thenReturn(LedgerTestSupport.ledger(1L, "ARCHIVED"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.update(1L, LedgerTestSupport.submitRequest(101L)));
        assertTrue(e.getMessage().contains("不可编辑"));
    }
}
