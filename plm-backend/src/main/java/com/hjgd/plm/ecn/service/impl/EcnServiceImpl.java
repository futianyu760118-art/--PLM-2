package com.hjgd.plm.ecn.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.m04.approval.dto.ApprovalProgressVO;
import com.hjgd.plm.m04.approval.entity.ApprovalInstance;
import com.hjgd.plm.m04.approval.enums.ApprovalAction;
import com.hjgd.plm.m04.approval.service.ApprovalService;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.bom.service.BomService;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.ecn.dto.EcnDTO;
import com.hjgd.plm.ecn.dto.EcnQueryDTO;
import com.hjgd.plm.ecn.dto.EcnReviewDTO;
import com.hjgd.plm.ecn.entity.Ecn;
import com.hjgd.plm.ecn.entity.EcnFlowLog;
import com.hjgd.plm.ecn.entity.EcnImpact;
import com.hjgd.plm.ecn.enums.EcnChangeType;
import com.hjgd.plm.ecn.enums.EcnStatus;
import com.hjgd.plm.ecn.mapper.EcnFlowLogMapper;
import com.hjgd.plm.ecn.mapper.EcnMapper;
import com.hjgd.plm.ecn.service.EcnService;
import com.hjgd.plm.event.service.DomainEventService;
import com.hjgd.plm.file.entity.PlmFile;
import com.hjgd.plm.file.mapper.PlmFileMapper;
import com.hjgd.plm.file.service.FileService;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.enums.MaterialStatus;
import com.hjgd.plm.material.service.MaterialService;
import com.hjgd.plm.system.service.SequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class EcnServiceImpl implements EcnService {

    private final EcnMapper ecnMapper;
    private final EcnFlowLogMapper flowLogMapper;
    private final SequenceService sequenceService;
    private final MaterialService materialService;
    private final FileService fileService;
    private final PlmFileMapper fileMapper;
    private final DomainEventService domainEventService;
    private final com.hjgd.plm.ecn.service.EcnImpactService ecnImpactService;
    private final BomService bomService;
    private final ApprovalService approvalService;

    @Override
    public PageResult<Ecn> page(EcnQueryDTO query) {
        LambdaQueryWrapper<Ecn> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getEcnNo()), Ecn::getEcnNo, query.getEcnNo())
                .like(StringUtils.hasText(query.getPartNo()), Ecn::getPartNo, query.getPartNo())
                .eq(query.getChangeType() != null, Ecn::getChangeType, query.getChangeType())
                .eq(query.getStatus() != null, Ecn::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getApplicant()), Ecn::getApplicant, query.getApplicant())
                .orderByDesc(Ecn::getCreatedAt);
        Page<Ecn> page = ecnMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page);
    }

    @Override
    public Ecn getById(Long id) {
        Ecn ecn = ecnMapper.selectById(id);
        if (ecn == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return ecn;
    }

    @Override
    public Ecn getByEcnNo(String ecnNo) {
        return ecnMapper.selectOne(new LambdaQueryWrapper<Ecn>().eq(Ecn::getEcnNo, ecnNo));
    }

    @Override
    @Transactional
    public Ecn create(EcnDTO dto) {
        Material material = materialService.getByPartNo(dto.getPartNo());
        if (material == null) {
            throw new BusinessException("关联物料料号不存在");
        }
        Ecn ecn = new Ecn();
        BeanUtils.copyProperties(dto, ecn);
        ecn.setEcnNo(sequenceService.nextNo("ECN_NO"));
        ecn.setMaterialId(material.getId());
        ecn.setMaterialName(material.getMaterialName());
        ecn.setStatus(EcnStatus.DRAFT);
        if (!StringUtils.hasText(ecn.getVersionBefore())) {
            ecn.setVersionBefore(material.getVersionNo());
        }
        if (!StringUtils.hasText(ecn.getVersionAfter())) {
            ecn.setVersionAfter(incVersion(material.getVersionNo()));
        }
        ecn.setApplicant(SecurityUtils.getCurrentRealName());
        ecn.setApplyTime(LocalDateTime.now());
        ecn.setCreatedBy(SecurityUtils.getCurrentRealName());
        ecnMapper.insert(ecn);
        return ecn;
    }

    @Override
    @Transactional
    public Ecn update(EcnDTO dto) {
        Ecn exist = getById(dto.getId());
        if (exist.getStatus() != EcnStatus.DRAFT && exist.getStatus() != EcnStatus.REJECTED) {
            throw new BusinessException(ResultCode.ECN_STATUS_ERROR);
        }
        BeanUtils.copyProperties(dto, exist, "id", "ecnNo", "materialId", "materialName",
                "status", "applicant", "applyTime", "reviewL1By", "reviewL1Time",
                "reviewL2By", "reviewL2Time", "effectiveTime", "voidTime",
                "createdBy", "createdAt", "updatedAt", "deleted");
        ecnMapper.updateById(exist);
        return exist;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Ecn exist = getById(id);
        if (exist.getStatus() != EcnStatus.DRAFT) {
            throw new BusinessException("非草稿状态ECN禁止删除");
        }
        ecnMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void submit(Long id) {
        Ecn exist = getById(id);
        if (exist.getStatus() != EcnStatus.DRAFT && exist.getStatus() != EcnStatus.REJECTED) {
            throw new BusinessException(ResultCode.ECN_STATUS_ERROR);
        }
        EcnStatus from = exist.getStatus();
        // 审批级数由流程配置决定，不再硬编码 L1/L2；无启用流程时回落旧的两级逻辑
        ApprovalInstance inst = approvalService.start("ECN", exist.getEcnNo(), null,
                Map.of("change_type", exist.getChangeType() == null ? "" : exist.getChangeType().name()));
        if (inst == null) {
            exist.setStatus(EcnStatus.PENDING_L1);
        } else {
            syncStatus(exist, inst);
        }
        ecnMapper.updateById(exist);
        // 物料进入变更中
        try {
            Material m = materialService.getById(exist.getMaterialId());
            if (m.getStatus() == MaterialStatus.RELEASED || m.getStatus() == MaterialStatus.IN_PRODUCTION) {
                materialService.startChange(m.getId());
            }
        } catch (Exception e) {
            log.warn("startChange on submit: {}", e.getMessage());
        }
        recordFlow(exist.getId(), 0, "submit", from, exist.getStatus(), "提交ECN审批");
    }

    @Override
    @Transactional
    public void approve(Long id, ApprovalAction action, String comment) {
        Ecn exist = getById(id);
        ApprovalInstance inst = approvalService.instanceOf("ECN", exist.getEcnNo());
        if (inst == null) {
            // 在途老单：没有审批实例，按原两级逻辑处理
            legacyAct(exist, action, comment);
            return;
        }
        EcnStatus from = exist.getStatus();
        ApprovalInstance after = approvalService.act(inst.getId(), action, comment);
        syncStatus(exist, after);
        fillReviewFields(exist, after, action, comment);
        ecnMapper.updateById(exist);
        recordFlow(exist.getId(), after.getCurrentSeq(), action.name().toLowerCase(),
                from, exist.getStatus(), comment);
    }

    @Override
    public ApprovalProgressVO progress(Long id) {
        Ecn exist = getById(id);
        return approvalService.progress("ECN", exist.getEcnNo());
    }

    /** 把审批实例状态映射回 ECN 状态；节点 seq 1 记 PENDING_L1，seq>=2 记 PENDING_L2，兼容老枚举与前端 */
    private void syncStatus(Ecn ecn, ApprovalInstance inst) {
        if (inst == null) {
            return;
        }
        switch (inst.getStatus()) {
            case "RUNNING" -> ecn.setStatus(inst.getCurrentSeq() != null && inst.getCurrentSeq() <= 1
                    ? EcnStatus.PENDING_L1 : EcnStatus.PENDING_L2);
            case "APPROVED" -> ecn.setStatus(EcnStatus.APPROVED);
            case "REJECTED" -> ecn.setStatus(EcnStatus.REJECTED);
            case "CANCELLED" -> ecn.setStatus(EcnStatus.VOID);
            default -> log.warn("未知审批状态 {}，ECN {} 状态保持不变", inst.getStatus(), ecn.getEcnNo());
        }
    }

    /** 旧字段 review_l1/l2 继续写，保证老报表和历史列表不空 */
    private void fillReviewFields(Ecn ecn, ApprovalInstance inst, ApprovalAction action, String comment) {
        int seq = inst.getCurrentSeq() == null ? 1 : inst.getCurrentSeq();
        String who = SecurityUtils.getCurrentRealName();
        LocalDateTime now = LocalDateTime.now();
        if (seq <= 1) {
            ecn.setReviewL1By(who);
            ecn.setReviewL1Time(now);
            ecn.setReviewL1Comment(comment);
        } else {
            ecn.setReviewL2By(who);
            ecn.setReviewL2Time(now);
            ecn.setReviewL2Comment(comment);
        }
        if (action == ApprovalAction.APPROVE && "APPROVED".equals(inst.getStatus())) {
            ecn.setFinalComment(comment);
        }
    }

    /** 无审批实例的在途单：保留原 L1→L2 行为 */
    private void legacyAct(Ecn exist, ApprovalAction action, String comment) {
        EcnStatus from = exist.getStatus();
        String who = SecurityUtils.getCurrentRealName();
        LocalDateTime now = LocalDateTime.now();
        if (exist.getStatus() == EcnStatus.PENDING_L1) {
            exist.setReviewL1By(who);
            exist.setReviewL1Time(now);
            exist.setReviewL1Comment(comment);
            exist.setStatus(action == ApprovalAction.APPROVE ? EcnStatus.PENDING_L2 : EcnStatus.REJECTED);
            recordFlow(exist.getId(), 1, action.name().toLowerCase(), from, exist.getStatus(), comment);
        } else if (exist.getStatus() == EcnStatus.PENDING_L2) {
            exist.setReviewL2By(who);
            exist.setReviewL2Time(now);
            exist.setReviewL2Comment(comment);
            if (action == ApprovalAction.APPROVE) {
                exist.setStatus(EcnStatus.APPROVED);
                exist.setFinalComment(comment);
            } else {
                exist.setStatus(EcnStatus.REJECTED);
            }
            recordFlow(exist.getId(), 2, action.name().toLowerCase(), from, exist.getStatus(), comment);
        } else {
            throw new BusinessException(ResultCode.ECN_STATUS_ERROR);
        }
        ecnMapper.updateById(exist);
    }

    // ---------- 兼容旧接口：前端 /ecn/{id}/l1/approve 等仍可用 ----------

    @Override
    public void reviewL1Approve(Long id, EcnReviewDTO dto) {
        approve(id, ApprovalAction.APPROVE, dto.getComment());
    }

    @Override
    public void reviewL1Reject(Long id, EcnReviewDTO dto) {
        approve(id, ApprovalAction.REJECT, dto.getComment());
    }

    @Override
    public void reviewL2Approve(Long id, EcnReviewDTO dto) {
        approve(id, ApprovalAction.APPROVE, dto.getComment());
    }

    @Override
    public void reviewL2Reject(Long id, EcnReviewDTO dto) {
        approve(id, ApprovalAction.REJECT, dto.getComment());
    }

    /**
     * ECN 生效：落库升版 + 按 impact 分发 + 发 ecn.effective 事件。
     * 刻意不加 @Transactional：ECN 状态置 EFFECTIVE 必须独立提交，各 impact 分发
     * (applyEcnEffect/bumpVersionForEcn/obsoleteFiles 各自带 @Transactional)独立成事务，
     * 实现"单类失败不回滚已生效状态"。若加 @Transactional，applyEcnEffect 抛异常时
     * 其代理会把共享事务标记 rollback-only，try/catch 拦不住，整笔回滚违背隔离意图。
     */
    @Override
    public void effect(Long id) {
        Ecn exist = getById(id);
        assertStatus(exist, EcnStatus.APPROVED);
        exist.setStatus(EcnStatus.EFFECTIVE);
        exist.setEffectiveTime(LocalDateTime.now());
        ecnMapper.updateById(exist);

        Material material = materialService.getById(exist.getMaterialId());
        String oldVersion = material.getVersionNo();
        boolean stayMp = "MASS_PRODUCTION".equals(material.getPhase());

        // 影响面：优先读已填写；未填写则按变更类型推断并落库
        Set<String> impactTypes = loadOrInferImpacts(exist);

        // 按 impact 类型分发(每类独立 try，单类失败不回滚已生效状态)
        boolean partBumped = false;
        boolean bomBumped = false;
        int filesObsolete = 0;

        if (impactTypes.contains("PART")) {
            try {
                materialService.applyEcnEffect(exist.getMaterialId(), exist.getVersionAfter(),
                        exist.getEcnNo(), stayMp);
                partBumped = true;
            } catch (Exception e) {
                log.error("ECN[{}] PART 升版失败: {}", exist.getEcnNo(), e.getMessage());
            }
        }
        if (impactTypes.contains("BOM")) {
            try {
                String old = bomService.bumpVersionForEcn(material.getPartNo(),
                        exist.getEcnNo(), exist.getVersionAfter());
                bomBumped = (old != null);
            } catch (Exception e) {
                log.error("ECN[{}] BOM 升版失败: {}", exist.getEcnNo(), e.getMessage());
            }
        }
        if (impactTypes.contains("FILE")) {
            try {
                filesObsolete = obsoleteOldVersionFiles(material.getPartNo(), oldVersion);
            } catch (Exception e) {
                log.error("ECN[{}] 旧图作废失败: {}", exist.getEcnNo(), e.getMessage());
            }
        }
        // SOP/TRADE/MOLD: 仅记录影响，具体动作由后续模块承接

        // 标记影响清单为已处理
        try {
            String result = "{\"partBumped\":" + partBumped
                    + ",\"bomBumped\":" + bomBumped
                    + ",\"filesObsolete\":" + filesObsolete + "}";
            for (EcnImpact imp : ecnImpactService.listByEcn(exist.getId())) {
                if ("PENDING".equals(imp.getStatus())) {
                    ecnImpactService.markHandled(imp.getId(), result);
                }
            }
        } catch (Exception e) {
            log.warn("ECN[{}] impact mark-handled skipped: {}", exist.getEcnNo(), e.getMessage());
        }

        recordFlow(exist.getId(), 3, "effect", EcnStatus.APPROVED, EcnStatus.EFFECTIVE, "ECN生效,版本已落库");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ecnNo", exist.getEcnNo());
        payload.put("partNo", exist.getPartNo());
        payload.put("versionBefore", exist.getVersionBefore());
        payload.put("versionAfter", exist.getVersionAfter());
        payload.put("impacts", impactTypes);
        payload.put("bomChanged", bomBumped);
        domainEventService.publish("ecn.effective", "ECN", exist.getEcnNo(), payload);
    }

    /** 读取已填写影响面；为空则按变更类型推断并落库 */
    private Set<String> loadOrInferImpacts(Ecn exist) {
        Set<String> types = new LinkedHashSet<>();
        try {
            List<EcnImpact> saved = ecnImpactService.listByEcn(exist.getId());
            for (EcnImpact imp : saved) {
                if (StringUtils.hasText(imp.getImpactType())) {
                    types.add(imp.getImpactType());
                }
            }
        } catch (Exception e) {
            log.warn("load impacts failed, will infer: {}", e.getMessage());
        }
        if (types.isEmpty()) {
            types.addAll(inferImpactTypes(exist.getChangeType()));
            try {
                ecnImpactService.saveImpacts(exist.getId(), exist.getEcnNo(), new ArrayList<>(types));
            } catch (Exception e) {
                log.warn("infer+save impacts skipped: {}", e.getMessage());
            }
        }
        return types;
    }

    private Set<String> inferImpactTypes(EcnChangeType ct) {
        if (ct == null) {
            return Set.of("PART", "FILE");
        }
        return switch (ct) {
            case STRUCTURE -> Set.of("PART", "BOM", "FILE");
            case BOM -> Set.of("BOM", "FILE");
            case MOLD -> Set.of("PART", "FILE", "MOLD");
            case PROCESS -> Set.of("SOP");
            case DIMENSION -> Set.of("PART", "FILE");
        };
    }

    @Override
    @Transactional
    public void voidEcn(Long id) {
        Ecn exist = getById(id);
        EcnStatus from = exist.getStatus();
        if (from == EcnStatus.EFFECTIVE) {
            throw new BusinessException("已生效ECN禁止作废");
        }
        exist.setStatus(EcnStatus.VOID);
        exist.setVoidTime(LocalDateTime.now());
        ecnMapper.updateById(exist);
        // 作废时同步撤销审批实例，避免待办箱残留
        try {
            approvalService.cancelByObject("ECN", exist.getEcnNo(), "ECN作废");
        } catch (Exception e) {
            log.warn("cancel approval on void failed: {}", e.getMessage());
        }
        recordFlow(exist.getId(), 4, "void", from, EcnStatus.VOID, "ECN作废");
    }

    @Override
    public List<EcnFlowLog> getFlowLogs(Long ecnId) {
        return flowLogMapper.selectList(
                new LambdaQueryWrapper<EcnFlowLog>()
                        .eq(EcnFlowLog::getEcnId, ecnId)
                        .orderByAsc(EcnFlowLog::getCreatedAt));
    }

    /**
     * 作废该料号的旧版图纸: 命中「版本号 = 升版前版本」, 并兜底命中「版本号为空的同料号文件」。
     *
     * 版本号为空的行(历史上传未记录版本、或上传时物料尚未建档)无法按版本精确匹配;
     * 若不兜底, 这些文件在 ECN 生效后仍可下载, AC1「旧文件自动作废」不成立。
     * 新上传的文件由 FileServiceImpl.upload 落版本号, 不会再产生空版本行;
     * 因此兜底只会吃掉真正的历史遗留数据, 不会误作废升版后的新文件。
     */
    private int obsoleteOldVersionFiles(String partNo, String oldVersion) {
        LambdaQueryWrapper<PlmFile> wrapper = new LambdaQueryWrapper<PlmFile>()
                .eq(PlmFile::getPartNo, partNo)
                .eq(PlmFile::getObsolete, 0);
        if (StringUtils.hasText(oldVersion)) {
            wrapper.and(w -> w.eq(PlmFile::getVersionNo, oldVersion).or().isNull(PlmFile::getVersionNo));
        } else {
            wrapper.isNull(PlmFile::getVersionNo);
        }
        List<PlmFile> oldFiles = fileMapper.selectList(wrapper);
        for (PlmFile f : oldFiles) {
            fileService.markObsolete(f.getId());
        }
        if (!oldFiles.isEmpty()) {
            log.info("料号[{}]旧版[{}]文件自动作废({}个)", partNo, oldVersion, oldFiles.size());
        }
        return oldFiles.size();
    }

    private void assertStatus(Ecn ecn, EcnStatus expected) {
        if (ecn.getStatus() != expected) {
            throw new BusinessException(ResultCode.ECN_STATUS_ERROR);
        }
    }

    private void recordFlow(Long ecnId, Integer step, String action,
                            EcnStatus from, EcnStatus to, String comment) {
        EcnFlowLog flow = new EcnFlowLog();
        flow.setEcnId(ecnId);
        flow.setStep(step);
        flow.setAction(action);
        flow.setOperator(SecurityUtils.getCurrentRealName());
        flow.setOperatorRole(SecurityUtils.getCurrentRole());
        flow.setFromStatus(from.name());
        flow.setToStatus(to.name());
        flow.setComment(comment);
        flow.setCreatedAt(LocalDateTime.now());
        flowLogMapper.insert(flow);
    }

    private String incVersion(String version) {
        if (!StringUtils.hasText(version)) {
            return "V1.1";
        }
        String numPart = version.replaceAll("[^0-9.]", "");
        String[] parts = numPart.split("\\.");
        if (parts.length >= 2) {
            int minor = Integer.parseInt(parts[1]) + 1;
            return "V" + parts[0] + "." + minor;
        }
        return version + ".1";
    }
}
