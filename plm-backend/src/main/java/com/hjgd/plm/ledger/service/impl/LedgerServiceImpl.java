package com.hjgd.plm.ledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.file.entity.PlmFile;
import com.hjgd.plm.file.service.FileService;
import com.hjgd.plm.ledger.config.LedgerProperties;
import com.hjgd.plm.ledger.dto.BatchResultVO;
import com.hjgd.plm.ledger.dto.LedgerDetailVO;
import com.hjgd.plm.ledger.dto.LedgerLinkVO;
import com.hjgd.plm.ledger.dto.LedgerQuery;
import com.hjgd.plm.ledger.dto.LedgerRefVO;
import com.hjgd.plm.ledger.dto.LedgerRequest;
import com.hjgd.plm.ledger.dto.LinkRequest;
import com.hjgd.plm.ledger.entity.PlmLedger;
import com.hjgd.plm.ledger.entity.PlmLedgerAttachment;
import com.hjgd.plm.ledger.entity.PlmLedgerLink;
import com.hjgd.plm.ledger.mapper.PlmLedgerAttachmentMapper;
import com.hjgd.plm.ledger.mapper.PlmLedgerLinkMapper;
import com.hjgd.plm.ledger.mapper.PlmLedgerMapper;
import com.hjgd.plm.ledger.service.LedgerAuditService;
import com.hjgd.plm.ledger.service.LedgerNotifyService;
import com.hjgd.plm.ledger.service.LedgerObjectResolver;
import com.hjgd.plm.ledger.service.LedgerService;
import com.hjgd.plm.ledger.spi.LedgerChangeEvent;
import com.hjgd.plm.ledger.spi.LedgerChangeListener;
import com.hjgd.plm.system.service.SequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 证据台账服务实现（能力线 A）。
 *
 * <p>写路径统一走 {@link #applyWrite} 的「校验 → 落库 → 留痕 → 通知」四段式，
 * 保证 AC-A1-7 无脏半记录（校验全部前置，异常在事务内抛出即整笔回滚）、
 * AC-A6-4 每笔增删改都有操作人/时间/对象/前后值/IP，以及 AC-A4-7/8 的通知内容齐全。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerServiceImpl implements LedgerService {

    private static final String SEQ_LEDGER_NO = "LEDGER_NO";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String STATUS_VOID = "VOID";
    private static final String LINK_ACTIVE = "ACTIVE";
    private static final String LINK_INVALID = "INVALID";
    private static final String REASON_DELETED = "DELETED";
    private static final String REASON_OBSOLETE = "OBSOLETE";

    private static final Set<String> ALLOWED_STATUS = Set.of(STATUS_DRAFT, STATUS_SUBMITTED,
            STATUS_ARCHIVED, STATUS_VOID);
    /** AC-A5-5：分页档位固定 20/50/100 */
    private static final Set<Integer> PAGE_SIZES = Set.of(20, 50, 100);

    private final PlmLedgerMapper ledgerMapper;
    private final PlmLedgerAttachmentMapper attachmentMapper;
    private final PlmLedgerLinkMapper linkMapper;
    private final LedgerObjectResolver objectResolver;
    private final LedgerAuditService auditService;
    private final LedgerNotifyService notifyService;
    private final LedgerProperties properties;
    private final SequenceService sequenceService;
    private final FileService fileService;
    private final JdbcTemplate jdbcTemplate;
    /**
     * 台账变更的扩展监听方（能力线 B 的单条订阅推送）。
     * 集合注入：无实现方时 Spring 注入空列表，台账功能不因此不可用。
     */
    private final List<LedgerChangeListener> changeListeners;

    // ===================== 查询 =====================

    @Override
    public PageResult<PlmLedger> page(LedgerQuery query) {
        if (StringUtils.hasText(query.getOccurStart()) && StringUtils.hasText(query.getOccurEnd())
                && query.getOccurStart().compareTo(query.getOccurEnd()) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "发生时间起始不得晚于结束");
        }
        if (StringUtils.hasText(query.getUploadStart()) && StringUtils.hasText(query.getUploadEnd())
                && query.getUploadStart().compareTo(query.getUploadEnd()) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上传时间起始不得晚于结束");
        }
        int pageSize = PAGE_SIZES.contains(query.getPageSize()) ? query.getPageSize() : 20;
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        applyScope(query);
        IPage<PlmLedger> page = ledgerMapper.pageLedger(new Page<>(pageNum, pageSize), query);
        return PageResult.of(page);
    }

    /**
     * 行级范围（AC-A6 角色表）：台账管理员/ADMIN 不过滤，其余限「本部门 + 本人上传」。
     * 由服务端根据当前登录用户注入，不接受外部入参。
     */
    private void applyScope(LedgerQuery query) {
        query.setScopeDeptId(null);
        query.setScopeUserId(null);
        if (isAdmin()) {
            return;
        }
        LoginUser user = SecurityUtils.getCurrentUser();
        query.setScopeUserId(user.getUserId());
        query.setScopeDeptId(user.getUser().getDeptId());
    }

    private boolean isAdmin() {
        return SecurityUtils.hasRole("ADMIN")
                || SecurityUtils.hasRole("LEDGER_ADMIN")
                || SecurityUtils.hasPermission("ledger:admin");
    }

    @Override
    public LedgerDetailVO detail(Long id) {
        PlmLedger ledger = require(id);
        requireReadable(ledger);
        return toDetail(ledger, true);
    }

    @Override
    public List<LedgerRefVO> byObject(String objectType, String objectId) {
        if (!StringUtils.hasText(objectType) || !StringUtils.hasText(objectId)) {
            return List.of();
        }
        // 实时读同一行数据：台账侧改动立即对模块侧可见，无需同步任务（AC-A3-1/A3-3）
        List<PlmLedger> rows = ledgerMapper.selectByObject(
                objectType.trim().toUpperCase(Locale.ROOT), objectId.trim());
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (PlmLedger row : rows) {
            ids.add(row.getId());
        }
        Map<Long, String> ownerNames = ownerNames(rows);
        Map<Long, Long> attachments = attachmentCounts(ids);
        Map<String, String> typeLabels = dictLabels("ledger_type");

        List<LedgerRefVO> out = new ArrayList<>();
        for (PlmLedger row : rows) {
            LedgerRefVO vo = new LedgerRefVO();
            vo.setId(row.getId());
            vo.setLedgerNo(row.getLedgerNo());
            vo.setName(row.getName());
            vo.setType(row.getType());
            vo.setTypeLabel(typeLabels.getOrDefault(row.getType(), row.getType()));
            vo.setStatus(row.getStatus());
            vo.setOwnerId(row.getOwnerId());
            vo.setOwnerName(ownerNames.get(row.getOwnerId()));
            vo.setOccurDate(row.getOccurDate());
            vo.setUploadedAt(row.getUploadedAt());
            vo.setAttachmentCount(attachments.getOrDefault(row.getId(), 0L));
            vo.setLink("/ledger/list?keyword=" + row.getLedgerNo());
            out.add(vo);
        }
        return out;
    }

    /** 责任人姓名批量装配：与台账主键同批查询，避免模块页逐行联查 */
    private Map<Long, String> ownerNames(List<PlmLedger> rows) {
        List<Long> ownerIds = new ArrayList<>();
        for (PlmLedger row : rows) {
            if (row.getOwnerId() != null && !ownerIds.contains(row.getOwnerId())) {
                ownerIds.add(row.getOwnerId());
            }
        }
        Map<Long, String> out = new LinkedHashMap<>();
        if (ownerIds.isEmpty()) {
            return out;
        }
        String sql = "SELECT id, real_name FROM sys_user WHERE id IN (" + placeholders(ownerIds.size()) + ")";
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, ownerIds.toArray())) {
            Object id = row.get("id");
            if (id != null) {
                out.put(Long.valueOf(String.valueOf(id)),
                        row.get("real_name") == null ? null : String.valueOf(row.get("real_name")));
            }
        }
        return out;
    }

    private Map<Long, Long> attachmentCounts(List<Long> ledgerIds) {
        Map<Long, Long> out = new LinkedHashMap<>();
        String sql = "SELECT ledger_id, COUNT(1) AS cnt FROM plm_ledger_attachment "
                + "WHERE deleted = 0 AND ledger_id IN (" + placeholders(ledgerIds.size()) + ") GROUP BY ledger_id";
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql, ledgerIds.toArray())) {
            Object id = row.get("ledger_id");
            Object cnt = row.get("cnt");
            if (id != null) {
                out.put(Long.valueOf(String.valueOf(id)),
                        cnt == null ? 0L : Long.valueOf(String.valueOf(cnt)));
            }
        }
        return out;
    }

    private Map<String, String> dictLabels(String dictType) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, Object> row : dict(dictType)) {
            out.put(String.valueOf(row.get("value")), String.valueOf(row.get("label")));
        }
        return out;
    }

    private String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    @Override
    public Map<String, Object> formOptions() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("types", dict("ledger_type"));
        out.put("statuses", dict("ledger_status"));
        out.put("objectTypes", dict("ledger_object_type"));
        out.put("supportedObjectTypes", new ArrayList<>(objectResolver.supportedTypes()));
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("maxAttachments", properties.getMaxAttachments());
        limits.put("maxFileSizeMb", properties.getMaxFileSizeMb());
        limits.put("allowExts", properties.getAllowExts());
        limits.put("maxLinks", properties.getMaxLinks());
        limits.put("batchLinkMax", properties.getBatchLinkMax());
        limits.put("nameMax", properties.getNameMax());
        limits.put("remarkMax", properties.getRemarkMax());
        limits.put("versionMax", properties.getVersionMax());
        limits.put("pageSizes", new ArrayList<>(PAGE_SIZES));
        out.put("limits", limits);
        return out;
    }

    private List<Map<String, Object>> dict(String dictType) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT dict_value, dict_label FROM sys_dict WHERE dict_type = ? ORDER BY sort_order, id", dictType);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", row.get("dict_value"));
            m.put("label", row.get("dict_label"));
            out.add(m);
        }
        return out;
    }

    // ===================== 录入 =====================

    @Override
    @Transactional
    public LedgerDetailVO create(LedgerRequest request) {
        boolean draft = isDraft(request.getStatus());
        validateRequestShape(request);

        PlmLedger ledger = new PlmLedger();
        ledger.setLedgerNo(sequenceService.nextNo(SEQ_LEDGER_NO));
        ledger.setUploadedBy(uid());
        ledger.setUploadedAt(LocalDateTime.now());
        ledger.setLastModifiedBy(uname());
        ledger.setLastModifiedAt(LocalDateTime.now());
        ledger.setRevision(0);
        applyFields(ledger, request);
        ledger.setStatus(draft ? STATUS_DRAFT : STATUS_SUBMITTED);
        validateEntityFields(ledger, draft);
        validateFileIds(request.getFileIds(), draft, true);
        validateLinks(request.getLinks(), draft, true);
        ledgerMapper.insert(ledger);

        replaceAttachments(ledger, request.getFileIds(), draft);
        replaceLinks(ledger, request.getLinks(), draft);

        auditService.log("CREATE", ledger.getLedgerNo(), null, snapshot(ledger));
        notifyService.notify(ledger.getOwnerId(), LedgerNotifyService.TYPE_LEDGER_CREATED,
                "新建证据 " + ledger.getLedgerNo(),
                null, null, List.of(), Map.of(
                        "ledgerNo", ledger.getLedgerNo(),
                        "ledgerName", nz(ledger.getName()),
                        "link", "/ledger/list?keyword=" + ledger.getLedgerNo()));
        emit(LedgerChangeEvent.Kind.CREATED, ledger, List.of(), "新建证据 " + ledger.getLedgerNo());
        return toDetail(ledger, true);
    }

    @Override
    @Transactional
    public LedgerDetailVO update(Long id, LedgerRequest request) {
        PlmLedger ledger = require(id);
        requireWritable(ledger);
        if (STATUS_ARCHIVED.equals(ledger.getStatus()) || STATUS_VOID.equals(ledger.getStatus())) {
            throw new BusinessException(ResultCode.FAILED, "已归档/已作废的证据不可编辑");
        }
        boolean draft = isDraft(request.getStatus() == null ? ledger.getStatus() : request.getStatus());
        validateRequestShape(request);

        Map<String, Object> before = snapshot(ledger);
        applyFields(ledger, request);
        if (StringUtils.hasText(request.getStatus())) {
            if (!ALLOWED_STATUS.contains(request.getStatus())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "非法状态: " + request.getStatus());
            }
            ledger.setStatus(request.getStatus());
        }
        validateEntityFields(ledger, draft);
        validateFileIds(request.getFileIds(), draft, request.getFileIds() != null);
        validateLinks(request.getLinks(), draft, request.getLinks() != null);
        ledger.setLastModifiedBy(uname());
        ledger.setLastModifiedAt(LocalDateTime.now());
        ledger.setRevision((ledger.getRevision() == null ? 0 : ledger.getRevision()) + 1);
        ledgerMapper.updateById(ledger);

        List<Map<String, Object>> changes = new ArrayList<>();
        if (request.getFileIds() != null) {
            replaceAttachments(ledger, request.getFileIds(), draft);
        }
        if (request.getLinks() != null) {
            replaceLinks(ledger, request.getLinks(), draft);
        }

        Map<String, Object> after = snapshot(ledger);
        diffInto(changes, before, after);
        auditService.log("UPDATE", ledger.getLedgerNo(), before, after);
        notifyChange(ledger, changes);
        emit(LedgerChangeEvent.Kind.UPDATED, ledger, changes, "证据 " + ledger.getLedgerNo() + " 发生变更");
        return toDetail(ledger, true);
    }

    @Override
    @Transactional
    public BatchResultVO batchUpload(LedgerRequest request, MultipartFile[] files) {
        BatchResultVO result = new BatchResultVO();
        if (files == null || files.length == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要上传的文件");
        }
        if (files.length > properties.getMaxAttachments()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "单次批量上传最多 " + properties.getMaxAttachments() + " 个文件，当前 " + files.length + " 个");
        }
        // 先整批校验再落盘：任一不合法直接拒绝，避免留下半截文件与脏记录（AC-A1-7）
        for (MultipartFile file : files) {
            String ext = extOf(file.getOriginalFilename());
            if (!properties.getAllowExts().contains(ext)) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "不支持的附件格式: " + (ext.isEmpty() ? file.getOriginalFilename() : ext));
            }
            long maxBytes = properties.getMaxFileSizeMb() * 1024 * 1024;
            if (file.getSize() > maxBytes) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "附件超过 " + properties.getMaxFileSizeMb() + "MB: " + file.getOriginalFilename());
            }
        }
        List<Long> fileIds = new ArrayList<>();
        for (MultipartFile file : files) {
            PlmFile saved = fileService.upload(file, null, "LEDGER", "INTRANET");
            fileIds.add(saved.getId());
        }
        request.setFileIds(fileIds);
        LedgerDetailVO vo = create(request);
        result.getLedgerIds().add(vo.getLedger().getId());
        result.setSuccessCount(fileIds.size());
        return result;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        PlmLedger ledger = require(id);
        requireWritable(ledger);
        Map<String, Object> before = snapshot(ledger);
        for (PlmLedgerLink link : allLinks(ledger.getId())) {
            linkMapper.deleteById(link.getId());
        }
        for (PlmLedgerAttachment attachment : attachments(ledger.getId())) {
            attachmentMapper.deleteById(attachment.getId());
        }
        ledgerMapper.deleteById(ledger.getId());
        auditService.log("DELETE", ledger.getLedgerNo(), before, null);
        notifyService.notify(ledger.getOwnerId(), LedgerNotifyService.TYPE_LEDGER_DELETED,
                "证据已删除 " + ledger.getLedgerNo(), null, null, List.of(),
                Map.of("ledgerNo", ledger.getLedgerNo(), "link", "/ledger/list"));
        emit(LedgerChangeEvent.Kind.DELETED, ledger,
                List.of(changeRow("status", ledger.getStatus(), "DELETED")),
                "证据 " + ledger.getLedgerNo() + " 已删除");
    }

    // ===================== 附件 =====================

    @Override
    @Transactional
    public LedgerDetailVO addAttachments(Long id, List<Long> fileIds) {
        PlmLedger ledger = require(id);
        requireWritable(ledger);
        if (fileIds == null || fileIds.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要添加的附件");
        }
        List<PlmLedgerAttachment> current = attachments(ledger.getId());
        if (current.size() + fileIds.size() > properties.getMaxAttachments()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "附件数超过上限 " + properties.getMaxAttachments());
        }
        validateFileIds(fileIds, false, true);
        int order = current.size();
        for (Long fileId : fileIds) {
            attachmentMapper.insert(buildAttachment(ledger.getId(), fileId, order++));
        }
        Map<String, Object> before = Map.of("attachmentCount", current.size());
        Map<String, Object> after = Map.of("attachmentCount", current.size() + fileIds.size());
        auditService.log("ATTACH_ADD", ledger.getLedgerNo(), before, after);
        emit(LedgerChangeEvent.Kind.ATTACH_ADDED, ledger,
                List.of(changeRow("attachmentCount", current.size(), current.size() + fileIds.size())),
                "证据 " + ledger.getLedgerNo() + " 新增 " + fileIds.size() + " 个附件");
        return toDetail(ledger, false);
    }

    @Override
    @Transactional
    public void removeAttachment(Long id, Long attachmentId) {
        PlmLedger ledger = require(id);
        requireWritable(ledger);
        PlmLedgerAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !ledger.getId().equals(attachment.getLedgerId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "附件不存在");
        }
        if (!STATUS_DRAFT.equals(ledger.getStatus()) && attachments(ledger.getId()).size() <= 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "至少保留 1 个附件");
        }
        attachmentMapper.deleteById(attachmentId);
        auditService.log("ATTACH_REMOVE", ledger.getLedgerNo(),
                Map.of("fileName", nz(attachment.getFileName())), Map.of("removed", true));
        emit(LedgerChangeEvent.Kind.ATTACH_REMOVED, ledger,
                List.of(changeRow("attachment", nz(attachment.getFileName()), null)),
                "证据 " + ledger.getLedgerNo() + " 移除附件 " + nz(attachment.getFileName()));
    }

    @Override
    public void requireReadable(Long id) {
        requireReadable(require(id));
    }

    @Override
    public void requireWritable(Long id) {
        requireWritable(require(id));
    }

    // ===================== 关联 =====================

    @Override
    @Transactional
    public LedgerDetailVO addLink(Long id, LinkRequest request) {
        PlmLedger ledger = require(id);
        requireWritable(ledger);
        List<PlmLedgerLink> current = activeLinks(ledger.getId());
        if (current.size() >= properties.getMaxLinks()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "关联对象数已达上限 " + properties.getMaxLinks());
        }
        PlmLedgerLink link = buildLink(ledger.getId(), request);
        linkMapper.insert(link);
        auditService.log("LINK", ledger.getLedgerNo(), null,
                Map.of("objectType", link.getObjectType(), "objectNo", nz(link.getObjectNo())));
        notifyLink(ledger, link);
        return toDetail(ledger, false);
    }

    @Override
    @Transactional
    public void removeLink(Long id, Long linkId) {
        PlmLedger ledger = require(id);
        requireWritable(ledger);
        PlmLedgerLink link = linkMapper.selectById(linkId);
        if (link == null || !ledger.getId().equals(link.getLedgerId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "关联不存在");
        }
        if (activeLinks(ledger.getId()).size() <= 1 && !STATUS_DRAFT.equals(ledger.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "至少保留 1 个关联对象");
        }
        // 解除关联仅删除引用，台账本体保留（AC-A4-6）
        linkMapper.deleteById(linkId);
        auditService.log("UNLINK", ledger.getLedgerNo(),
                Map.of("objectType", link.getObjectType(), "objectId", nz(link.getObjectId())),
                Map.of("removed", true));
    }

    @Override
    @Transactional
    public BatchResultVO batchLink(List<Long> ledgerIds, LinkRequest request) {
        BatchResultVO result = new BatchResultVO();
        if (ledgerIds == null || ledgerIds.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要关联的证据");
        }
        if (ledgerIds.size() > properties.getBatchLinkMax()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "单次批量关联最多 " + properties.getBatchLinkMax() + " 条证据，当前 " + ledgerIds.size() + " 条");
        }
        for (Long ledgerId : new LinkedHashSet<>(ledgerIds)) {
            try {
                PlmLedger ledger = require(ledgerId);
                requireWritable(ledger);
                List<PlmLedgerLink> current = activeLinks(ledger.getId());
                if (current.size() >= properties.getMaxLinks()) {
                    result.addFailure(ledgerId, "关联对象数已达上限");
                    continue;
                }
                boolean exists = current.stream().anyMatch(l -> l.getObjectType().equalsIgnoreCase(
                        nz(request.getObjectType())) && Objects.equals(l.getObjectId(), request.getObjectId()));
                if (exists) {
                    result.addFailure(ledgerId, "已关联该对象");
                    continue;
                }
                PlmLedgerLink link = buildLink(ledger.getId(), request);
                linkMapper.insert(link);
                auditService.log("LINK", ledger.getLedgerNo(), null,
                        Map.of("objectType", link.getObjectType(), "objectNo", nz(link.getObjectNo())));
                notifyLink(ledger, link);
                result.getLedgerIds().add(ledger.getId());
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (BusinessException e) {
                result.addFailure(ledgerId, e.getMessage());
            }
        }
        return result;
    }

    @Override
    public List<LedgerObjectResolver.ObjectRef> searchObjects(String objectType, String keyword, int limit) {
        return objectResolver.search(objectType, keyword, limit);
    }

    @Override
    public List<Map<String, Object>> searchOwners(String keyword, int limit) {
        int cap = Math.max(1, Math.min(limit, 50));
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        // 只回传选择责任人所必需的字段；与 ADMIN 专属的用户管理接口分离（AC-A2-2）
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, real_name, username, dept_id FROM sys_user "
                        + "WHERE status = 1 AND (real_name LIKE ? OR username LIKE ?) "
                        + "ORDER BY id LIMIT " + cap, like, like);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row.get("id"));
            m.put("realName", row.get("real_name"));
            m.put("username", row.get("username"));
            m.put("deptId", row.get("dept_id"));
            out.add(m);
        }
        return out;
    }

    @Override
    public Map<String, Object> trail(String ledgerNo) {
        if (!StringUtils.hasText(ledgerNo)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "证据编号不能为空");
        }
        return auditService.trail(ledgerNo.trim());
    }

    // ===================== 内部 =====================

    private PlmLedger require(Long id) {
        PlmLedger ledger = id == null ? null : ledgerMapper.selectById(id);
        if (ledger == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "证据不存在");
        }
        return ledger;
    }

    private void requireReadable(PlmLedger ledger) {
        if (isAdmin()) {
            return;
        }
        LoginUser user = SecurityUtils.getCurrentUser();
        if (Objects.equals(ledger.getUploadedBy(), user.getUserId())
                || Objects.equals(ledger.getOwnerId(), user.getUserId())
                || (user.getUser().getDeptId() != null && user.getUser().getDeptId().equals(ledger.getDeptId()))) {
            return;
        }
        auditService.logDenied("DENIED_READ", ledger.getLedgerNo(), "越权读取证据");
        throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该证据");
    }

    private void requireWritable(PlmLedger ledger) {
        if (isAdmin()) {
            return;
        }
        LoginUser user = SecurityUtils.getCurrentUser();
        if (Objects.equals(ledger.getUploadedBy(), user.getUserId())
                || Objects.equals(ledger.getOwnerId(), user.getUserId())
                || (user.getUser().getDeptId() != null && user.getUser().getDeptId().equals(ledger.getDeptId()))) {
            return;
        }
        auditService.logDenied("DENIED_WRITE", ledger.getLedgerNo(), "越权修改证据");
        throw new BusinessException(ResultCode.FORBIDDEN, "无权修改该证据");
    }

    private LedgerDetailVO toDetail(PlmLedger ledger, boolean refreshLinkStatus) {
        LedgerDetailVO vo = new LedgerDetailVO();
        vo.setLedger(ledger);
        vo.setAttachments(attachments(ledger.getId()));
        List<PlmLedgerLink> links = allLinks(ledger.getId());
        List<LedgerLinkVO> linkVos = new ArrayList<>();
        for (PlmLedgerLink link : links) {
            linkVos.add(toLinkVO(link, refreshLinkStatus));
        }
        vo.setLinks(linkVos);
        vo.setCanEdit(canWrite(ledger));
        vo.setCanDelete(isAdmin() || canWrite(ledger));
        return vo;
    }

    private boolean canWrite(PlmLedger ledger) {
        if (isAdmin()) {
            return true;
        }
        LoginUser user = SecurityUtils.getCurrentUser();
        return Objects.equals(ledger.getUploadedBy(), user.getUserId())
                || Objects.equals(ledger.getOwnerId(), user.getUserId())
                || (user.getUser().getDeptId() != null && user.getUser().getDeptId().equals(ledger.getDeptId()));
    }

    private List<PlmLedgerAttachment> attachments(Long ledgerId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<PlmLedgerAttachment>()
                .eq(PlmLedgerAttachment::getLedgerId, ledgerId)
                .orderByAsc(PlmLedgerAttachment::getSortOrder)
                .orderByAsc(PlmLedgerAttachment::getId));
    }

    private List<PlmLedgerLink> activeLinks(Long ledgerId) {
        return linkMapper.selectList(new LambdaQueryWrapper<PlmLedgerLink>()
                .eq(PlmLedgerLink::getLedgerId, ledgerId)
                .eq(PlmLedgerLink::getLinkStatus, LINK_ACTIVE));
    }

    private List<PlmLedgerLink> allLinks(Long ledgerId) {
        return linkMapper.selectList(new LambdaQueryWrapper<PlmLedgerLink>()
                .eq(PlmLedgerLink::getLedgerId, ledgerId)
                .orderByAsc(PlmLedgerLink::getId));
    }

    private LedgerLinkVO toLinkVO(PlmLedgerLink link, boolean refresh) {
        LedgerObjectResolver.ObjectRef ref = objectResolver.resolve(link.getObjectType(), link.getObjectId());
        LedgerLinkVO vo = new LedgerLinkVO();
        vo.setId(link.getId());
        vo.setObjectType(link.getObjectType());
        vo.setObjectId(link.getObjectId());
        vo.setObjectNo(StringUtils.hasText(link.getObjectNo()) ? link.getObjectNo() : ref.objectNo());
        vo.setObjectName(StringUtils.hasText(link.getObjectName()) ? link.getObjectName() : ref.objectName());
        vo.setObjectValid(ref.valid());
        vo.setJumpPath(jumpPath(link.getObjectType(), vo.getObjectNo()));
        if (ref.valid()) {
            vo.setLinkStatus(LINK_ACTIVE);
            vo.setInvalidReason(null);
        } else {
            vo.setLinkStatus(LINK_INVALID);
            vo.setInvalidReason(ref.invalidReason() == null ? REASON_DELETED : ref.invalidReason());
        }
        // 实时把失效状态回写引用表：模块侧与台账侧的下一次读取都能立刻看到（AC-A4-5 ≤1 分钟）
        if (refresh && !Objects.equals(vo.getLinkStatus(), link.getLinkStatus())) {
            try {
                PlmLedgerLink patch = new PlmLedgerLink();
                patch.setId(link.getId());
                patch.setLinkStatus(vo.getLinkStatus());
                patch.setInvalidReason(vo.getInvalidReason());
                if (StringUtils.hasText(ref.objectNo())) {
                    patch.setObjectNo(ref.objectNo());
                }
                if (StringUtils.hasText(ref.objectName())) {
                    patch.setObjectName(ref.objectName());
                }
                linkMapper.updateById(patch);
            } catch (Exception e) {
                log.warn("关联状态回写失败 linkId={}: {}", link.getId(), e.getMessage());
            }
        }
        return vo;
    }

    private String jumpPath(String objectType, String objectNo) {
        String kw = objectNo == null ? "" : objectNo;
        return switch (nz(objectType).toUpperCase(Locale.ROOT)) {
            case "PROJECT" -> "/project/list?keyword=" + kw;
            case "REQUIREMENT" -> "/improve/list?keyword=" + kw;
            case "MATERIAL" -> "/material/list?keyword=" + kw;
            case "BOM" -> "/bom/list?keyword=" + kw;
            case "ECN" -> "/ecn/list?keyword=" + kw;
            case "TASK" -> "/project/tracking?keyword=" + kw;
            case "REVIEW" -> "/project/review?keyword=" + kw;
            default -> "/ledger/list";
        };
    }

    // ---------- 校验 ----------

    private boolean isDraft(String status) {
        return StringUtils.hasText(status) && STATUS_DRAFT.equalsIgnoreCase(status.trim());
    }

    /** 入参自身的格式校验：长度与日期范围（草稿同样适用） */
    private void validateRequestShape(LedgerRequest request) {
        String name = trim(request.getName());
        if (name != null && name.length() > properties.getNameMax()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "证据名称不得超过 " + properties.getNameMax() + " 字");
        }
        String remark = trim(request.getRemark());
        if (remark != null && remark.length() > properties.getRemarkMax()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "备注不得超过 " + properties.getRemarkMax() + " 字");
        }
        String version = trim(request.getVersion());
        if (version != null && version.length() > properties.getVersionMax()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "版本不得超过 " + properties.getVersionMax() + " 字");
        }
        if (StringUtils.hasText(request.getOccurDate()) && parseDate(request.getOccurDate()).isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "发生时间不得晚于当前日期");
        }
    }

    /**
     * 合并后的实体必填校验。以实体（而非入参）为准，部分字段更新不会因为入参缺字段被误判。
     * {@code draft=true} 时跳过必填（AC-A2-7），仅保留类型合法性与外键存在性检查。
     */
    private void validateEntityFields(PlmLedger ledger, boolean draft) {
        if (StringUtils.hasText(ledger.getType())) {
            Set<String> allowedTypes = dictValues("ledger_type");
            if (!allowedTypes.isEmpty() && !allowedTypes.contains(ledger.getType())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "非法证据类型: " + ledger.getType());
            }
        }
        if (ledger.getOwnerId() != null && !userExists(ledger.getOwnerId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "责任人不存在");
        }
        if (ledger.getDeptId() != null && !deptExists(ledger.getDeptId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "来源部门不存在");
        }
        if (draft) {
            return;
        }
        if (!StringUtils.hasText(ledger.getName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "证据名称不能为空");
        }
        if (!StringUtils.hasText(ledger.getType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "证据类型不能为空");
        }
        if (ledger.getOwnerId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "责任人不能为空");
        }
        if (ledger.getOccurDate() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "发生时间不能为空");
        }
    }

    private void validateFileIds(List<Long> fileIds, boolean draft, boolean enforce) {
        if (!enforce) {
            return;
        }
        int count = fileIds == null ? 0 : fileIds.size();
        if (count == 0) {
            if (!draft) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "至少上传 1 个附件");
            }
            return;
        }
        if (count > properties.getMaxAttachments()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "附件数不得超过 " + properties.getMaxAttachments() + " 个");
        }
        long maxBytes = properties.getMaxFileSizeMb() * 1024 * 1024;
        for (Long fileId : fileIds) {
            PlmFile file = fileService.getById(fileId);
            String ext = file.getFileExt() == null ? "" : file.getFileExt().toLowerCase(Locale.ROOT);
            if (!properties.getAllowExts().contains(ext)) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "不支持的附件格式: " + (ext.isEmpty() ? file.getFileName() : ext));
            }
            if (file.getFileSize() != null && file.getFileSize() > maxBytes) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "附件超过 " + properties.getMaxFileSizeMb() + "MB: " + file.getFileName());
            }
        }
    }

    private void validateLinks(List<LinkRequest> links, boolean draft, boolean enforce) {
        if (!enforce) {
            return;
        }
        int count = links == null ? 0 : links.size();
        if (count == 0) {
            if (!draft) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "至少保留 1 个关联对象");
            }
            return;
        }
        if (count > properties.getMaxLinks()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "关联对象不得超过 " + properties.getMaxLinks() + " 个");
        }
        Set<String> seen = new HashSet<>();
        for (LinkRequest link : links) {
            if (!StringUtils.hasText(link.getObjectType()) || !StringUtils.hasText(link.getObjectId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "关联对象类型与编号均不能为空");
            }
            String type = link.getObjectType().trim().toUpperCase(Locale.ROOT);
            if (!objectResolver.supportedTypes().contains(type)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的关联对象类型: " + link.getObjectType());
            }
            String key = type + "#" + link.getObjectId().trim();
            if (!seen.add(key)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "存在重复的关联对象: " + key);
            }
            LedgerObjectResolver.ObjectRef ref = objectResolver.resolve(type, link.getObjectId());
            if (!ref.valid()) {
                String reason = REASON_OBSOLETE.equals(ref.invalidReason()) ? "已作废" : "已删除";
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "关联对象" + reason + ": " + link.getObjectId());
            }
        }
    }

    private boolean userExists(Long id) {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sys_user WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    private boolean deptExists(Long id) {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sys_department WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    private Set<String> dictValues(String dictType) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT dict_value FROM sys_dict WHERE dict_type = ?", String.class, dictType);
        return new HashSet<>(values);
    }

    // ---------- 写入辅助 ----------

    private void applyFields(PlmLedger ledger, LedgerRequest request) {
        if (request.getName() != null) {
            ledger.setName(trim(request.getName()));
        }
        if (request.getType() != null) {
            ledger.setType(trim(request.getType()));
        }
        if (request.getOwnerId() != null) {
            ledger.setOwnerId(request.getOwnerId());
        }
        if (request.getOccurDate() != null) {
            ledger.setOccurDate(StringUtils.hasText(request.getOccurDate()) ? parseDate(request.getOccurDate()) : null);
        }
        if (request.getVersion() != null) {
            ledger.setVersion(trim(request.getVersion()));
        }
        if (request.getDeptId() != null) {
            ledger.setDeptId(request.getDeptId());
        }
        if (request.getRemark() != null) {
            ledger.setRemark(trim(request.getRemark()));
        }
        if (ledger.getDeptId() == null) {
            // 未指定来源部门时取当前用户部门，保证行级范围可判定
            try {
                ledger.setDeptId(SecurityUtils.getCurrentUser().getUser().getDeptId());
            } catch (Exception ignored) {
                // 无登录上下文（内部调用）不补默认部门
            }
        }
    }

    /** 附件集合按 fileIds 全量对齐：新增缺失项、移除多余项（AC-A3-3 两侧一致） */
    private void replaceAttachments(PlmLedger ledger, List<Long> fileIds, boolean draft) {
        List<PlmLedgerAttachment> current = attachments(ledger.getId());
        Set<Long> target = new LinkedHashSet<>(fileIds == null ? List.of() : fileIds);
        if (target.isEmpty() && draft) {
            for (PlmLedgerAttachment a : current) {
                attachmentMapper.deleteById(a.getId());
            }
            return;
        }
        Set<Long> existing = new LinkedHashSet<>();
        for (PlmLedgerAttachment a : current) {
            existing.add(a.getFileId());
            if (!target.contains(a.getFileId())) {
                attachmentMapper.deleteById(a.getId());
            }
        }
        int order = 0;
        for (Long fileId : target) {
            if (!existing.contains(fileId)) {
                attachmentMapper.insert(buildAttachment(ledger.getId(), fileId, order));
            }
            order++;
        }
    }

    private PlmLedgerAttachment buildAttachment(Long ledgerId, Long fileId, int sortOrder) {
        PlmFile file = fileService.getById(fileId);
        PlmLedgerAttachment attachment = new PlmLedgerAttachment();
        attachment.setLedgerId(ledgerId);
        attachment.setFileId(fileId);
        attachment.setFileName(file.getFileName());
        attachment.setFileExt(file.getFileExt());
        attachment.setFileSize(file.getFileSize());
        attachment.setSortOrder(sortOrder);
        attachment.setUploadedBy(uid());
        attachment.setUploadedAt(LocalDateTime.now());
        return attachment;
    }

    /** 关联集合按 links 全量对齐 */
    private void replaceLinks(PlmLedger ledger, List<LinkRequest> links, boolean draft) {
        List<PlmLedgerLink> current = allLinks(ledger.getId());
        Set<String> target = new LinkedHashSet<>();
        if (links != null) {
            for (LinkRequest link : links) {
                target.add(link.getObjectType().trim().toUpperCase(Locale.ROOT) + "#" + link.getObjectId().trim());
            }
        }
        if (target.isEmpty() && draft) {
            for (PlmLedgerLink link : current) {
                linkMapper.deleteById(link.getId());
            }
            return;
        }
        if (links == null) {
            return;
        }
        Set<String> existing = new LinkedHashSet<>();
        for (PlmLedgerLink link : current) {
            String key = link.getObjectType().toUpperCase(Locale.ROOT) + "#" + link.getObjectId();
            existing.add(key);
            if (!target.contains(key)) {
                linkMapper.deleteById(link.getId());
            }
        }
        for (LinkRequest link : links) {
            String type = link.getObjectType().trim().toUpperCase(Locale.ROOT);
            String id = link.getObjectId().trim();
            if (!existing.contains(type + "#" + id)) {
                linkMapper.insert(buildLink(ledger.getId(), link));
            }
        }
    }

    private PlmLedgerLink buildLink(Long ledgerId, LinkRequest request) {
        String type = request.getObjectType().trim().toUpperCase(Locale.ROOT);
        String objectId = request.getObjectId().trim();
        LedgerObjectResolver.ObjectRef ref = objectResolver.resolve(type, objectId);
        PlmLedgerLink link = new PlmLedgerLink();
        link.setLedgerId(ledgerId);
        link.setObjectType(type);
        link.setObjectId(objectId);
        link.setObjectNo(ref.objectNo());
        link.setObjectName(ref.objectName());
        link.setLinkStatus(ref.valid() ? LINK_ACTIVE : LINK_INVALID);
        link.setInvalidReason(ref.valid() ? null : ref.invalidReason());
        return link;
    }

    private void notifyLink(PlmLedger ledger, PlmLedgerLink link) {
        notifyService.notifyAll(List.of(ledger.getOwnerId(), ledger.getUploadedBy()),
                LedgerNotifyService.TYPE_LEDGER_LINK,
                "证据 " + ledger.getLedgerNo() + " 关联了 " + link.getObjectType() + " " + nz(link.getObjectNo()),
                link.getObjectType(), link.getObjectId(),
                List.of(changeRow("link", null, link.getObjectNo())),
                linkExtra(ledger, link));
    }

    /** 台账字段变更通知：含对象/字段/前后值/操作人/时间/链接（AC-A4-7/A4-8） */
    private void notifyChange(PlmLedger ledger, List<Map<String, Object>> changes) {
        if (changes.isEmpty()) {
            return;
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("ledgerNo", ledger.getLedgerNo());
        extra.put("ledgerName", nz(ledger.getName()));
        extra.put("link", "/ledger/list?keyword=" + ledger.getLedgerNo());
        notifyService.notifyAll(List.of(ledger.getOwnerId(), ledger.getUploadedBy()),
                LedgerNotifyService.TYPE_LEDGER_CHANGED,
                "证据 " + ledger.getLedgerNo() + " 发生变更", null, null, changes, extra);
    }

    private Map<String, Object> linkExtra(PlmLedger ledger, PlmLedgerLink link) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("ledgerNo", ledger.getLedgerNo());
        extra.put("objectNo", nz(link.getObjectNo()));
        extra.put("objectName", nz(link.getObjectName()));
        extra.put("link", "/ledger/list?keyword=" + ledger.getLedgerNo());
        return extra;
    }

    /**
     * 广播台账变更（能力线 B 扩展点）。
     *
     * <p>监听方异常必须就地吞掉：订阅推送属于附带能力，不能因为推送侧的问题
     * 把已经校验通过的台账写入整体回滚（AC-A1-7 要求的是写入完整性，不是推送完整性）。
     */
    private void emit(LedgerChangeEvent.Kind kind, PlmLedger ledger,
                      List<Map<String, Object>> changes, String summary) {
        if (changeListeners == null || changeListeners.isEmpty()) {
            return;
        }
        LedgerChangeEvent event = LedgerChangeEvent.builder()
                .kind(kind)
                .ledgerId(ledger.getId())
                .ledgerNo(ledger.getLedgerNo())
                .ledgerName(ledger.getName())
                .ledgerType(ledger.getType())
                .status(ledger.getStatus())
                .ownerId(ledger.getOwnerId())
                .deptId(ledger.getDeptId())
                .uploadedBy(ledger.getUploadedBy())
                .operatorId(uid())
                .operatorName(uname())
                .occurredAt(LocalDateTime.now())
                .changes(changes == null ? new ArrayList<>() : changes)
                .summary(summary)
                .build();
        for (LedgerChangeListener listener : changeListeners) {
            try {
                listener.onLedgerChange(event);
            } catch (Exception e) {
                log.warn("台账变更监听失败 kind={} ledgerNo={}: {}",
                        kind, ledger.getLedgerNo(), e.getMessage());
            }
        }
    }

    private Map<String, Object> changeRow(String field, Object before, Object after) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("field", field);
        row.put("before", before);
        row.put("after", after);
        row.put("operator", uname());
        row.put("time", LocalDateTime.now().toString());
        return row;
    }

    private void diffInto(List<Map<String, Object>> out, Map<String, Object> before, Map<String, Object> after) {
        for (Map.Entry<String, Object> e : after.entrySet()) {
            Object b = before.get(e.getKey());
            if (!Objects.equals(normalize(b), normalize(e.getValue()))) {
                out.add(changeRow(e.getKey(), b, e.getValue()));
            }
        }
    }

    private Map<String, Object> snapshot(PlmLedger ledger) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ledgerNo", ledger.getLedgerNo());
        m.put("name", ledger.getName());
        m.put("type", ledger.getType());
        m.put("ownerId", ledger.getOwnerId());
        m.put("occurDate", ledger.getOccurDate() == null ? null : ledger.getOccurDate().toString());
        m.put("version", ledger.getVersion());
        m.put("deptId", ledger.getDeptId());
        m.put("status", ledger.getStatus());
        m.put("remark", ledger.getRemark());
        return m;
    }

    private Object normalize(Object o) {
        return o == null ? "" : o;
    }

    private LocalDate parseDate(String value) {
        String v = value.trim();
        try {
            return LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "日期格式应为 yyyy-MM-dd: " + value);
        }
    }

    private String extOf(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".")).toLowerCase(Locale.ROOT);
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private Long uid() {
        return SecurityUtils.getCurrentUserId();
    }

    private String uname() {
        try {
            return SecurityUtils.getCurrentRealName();
        } catch (Exception e) {
            return "SYSTEM";
        }
    }
}
