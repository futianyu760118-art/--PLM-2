package com.hjgd.plm.subscription.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hjgd.plm.ledger.entity.PlmLedger;
import com.hjgd.plm.ledger.entity.PlmLedgerAttachment;
import com.hjgd.plm.ledger.mapper.PlmLedgerAttachmentMapper;
import com.hjgd.plm.subscription.dto.RepoFileVO;
import com.hjgd.plm.subscription.dto.RepositoryItemVO;
import com.hjgd.plm.subscription.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 把台账行拼成「资料汇总处条目」（AC-B6-2 列表字段 / AC-B5 报告条目）。
 *
 * <p>汇总处列表与报告条目共用本方法：两处的字段口径必须一致，否则
 * 「按报告里的条目去汇总处找那条」会因为字段对不上而找不到（AC-B5-4 一次点击直达）。
 *
 * <p>批量取数（标签/附件/责任人/部门/类型字典各一次），不在循环里查库。
 */
@Component
@RequiredArgsConstructor
public class RepositoryItemAssembler {

    /** AC-B6-3：仅 pdf 与图片可在页面内预览，其余走下载 */
    private static final Set<String> PREVIEWABLE = Set.of(".pdf", ".jpg", ".jpeg", ".png");

    private final PlmLedgerAttachmentMapper attachmentMapper;
    private final CategoryService categoryService;
    private final JdbcTemplate jdbcTemplate;

    public List<RepositoryItemVO> assemble(List<PlmLedger> ledgers, boolean withFiles) {
        List<RepositoryItemVO> out = new ArrayList<>();
        if (ledgers == null || ledgers.isEmpty()) {
            return out;
        }
        List<Long> ids = new ArrayList<>();
        Set<Long> ownerIds = new HashSet<>();
        Set<Long> deptIds = new HashSet<>();
        for (PlmLedger ledger : ledgers) {
            ids.add(ledger.getId());
            if (ledger.getOwnerId() != null) {
                ownerIds.add(ledger.getOwnerId());
            }
            if (ledger.getDeptId() != null) {
                deptIds.add(ledger.getDeptId());
            }
        }
        Map<Long, List<String>> labels = categoryService.labelsByLedgerIds(ids);
        Map<Long, List<PlmLedgerAttachment>> files = withFiles ? attachmentsOf(ids) : Map.of();
        Map<Long, String> owners = userNames(ownerIds);
        Map<Long, String> depts = deptNames(deptIds);
        Map<String, String> typeLabels = dictLabels("ledger_type");

        for (PlmLedger ledger : ledgers) {
            RepositoryItemVO vo = new RepositoryItemVO();
            vo.setLedgerId(ledger.getId());
            vo.setLedgerNo(ledger.getLedgerNo());
            vo.setName(ledger.getName());
            vo.setType(ledger.getType());
            vo.setTypeName(typeLabels.getOrDefault(ledger.getType(), ledger.getType()));
            vo.setStatus(ledger.getStatus());
            vo.setCategories(labels.getOrDefault(ledger.getId(), List.of()));
            vo.setOwnerId(ledger.getOwnerId());
            vo.setOwnerName(owners.getOrDefault(ledger.getOwnerId(), ""));
            vo.setDeptId(ledger.getDeptId());
            vo.setDeptName(depts.getOrDefault(ledger.getDeptId(), ""));
            vo.setUploadedAt(ledger.getUploadedAt());
            vo.setLastModifiedAt(ledger.getLastModifiedAt());
            vo.setLastModifiedBy(ledger.getLastModifiedBy());
            vo.setRemark(ledger.getRemark());
            vo.setOccurDate(ledger.getOccurDate());
            vo.setVersion(ledger.getVersion());
            // 一次点击直达：链接直接指向汇总处的这条资料，不要求再手工检索（AC-B5-2/B5-4、AC-B6-6）
            vo.setLink("/ledger/repository?ledgerId=" + ledger.getId());

            List<PlmLedgerAttachment> list = files.getOrDefault(ledger.getId(), List.of());
            vo.setAttachmentCount(list.size());
            List<RepoFileVO> fileVOs = new ArrayList<>();
            for (PlmLedgerAttachment a : list) {
                fileVOs.add(toFileVO(a));
            }
            vo.setFiles(fileVOs);
            out.add(vo);
        }
        return out;
    }

    public RepoFileVO toFileVO(PlmLedgerAttachment attachment) {
        RepoFileVO vo = new RepoFileVO();
        vo.setAttachmentId(attachment.getId());
        vo.setFileId(attachment.getFileId());
        vo.setFileName(attachment.getFileName());
        String ext = attachment.getFileExt() == null ? "" : attachment.getFileExt().toLowerCase(Locale.ROOT);
        vo.setFileExt(ext);
        vo.setFileSize(attachment.getFileSize());
        vo.setUploadedAt(attachment.getUploadedAt());
        vo.setPreviewable(PREVIEWABLE.contains(ext));
        vo.setDownloadUrl("/ledger/attachments/" + attachment.getId() + "/download");
        vo.setPreviewUrl(PREVIEWABLE.contains(ext) ? "/ledger/attachments/" + attachment.getId() + "/preview" : null);
        return vo;
    }

    private Map<Long, List<PlmLedgerAttachment>> attachmentsOf(List<Long> ledgerIds) {
        Map<Long, List<PlmLedgerAttachment>> map = new LinkedHashMap<>();
        List<PlmLedgerAttachment> all = attachmentMapper.selectList(
                Wrappers.<PlmLedgerAttachment>lambdaQuery()
                        .in(PlmLedgerAttachment::getLedgerId, ledgerIds)
                        .orderByAsc(PlmLedgerAttachment::getSortOrder)
                        .orderByAsc(PlmLedgerAttachment::getId));
        for (PlmLedgerAttachment a : all) {
            map.computeIfAbsent(a.getLedgerId(), k -> new ArrayList<>()).add(a);
        }
        return map;
    }

    private Map<Long, String> userNames(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (Map<String, Object> row : queryIn("SELECT id, real_name FROM sys_user WHERE id IN ", ids)) {
            map.put(longOf(row.get("id")), str(row.get("real_name")));
        }
        return map;
    }

    private Map<Long, String> deptNames(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (Map<String, Object> row : queryIn("SELECT id, dept_name FROM sys_department WHERE id IN ", ids)) {
            map.put(longOf(row.get("id")), str(row.get("dept_name")));
        }
        return map;
    }

    private List<Map<String, Object>> queryIn(String prefix, Set<Long> ids) {
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.queryForList(prefix + "(" + placeholders + ")", ids.toArray());
    }

    private Map<String, String> dictLabels(String dictType) {
        Map<String, String> map = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT dict_value, dict_label FROM sys_dict WHERE dict_type = ?", dictType)) {
            map.put(str(row.get("dict_value")), str(row.get("dict_label")));
        }
        return map;
    }

    private Long longOf(Object o) {
        if (o == null) {
            return null;
        }
        return o instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(o).trim());
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
