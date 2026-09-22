package com.hjgd.plm.ecn.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final PlatformTransactionManager transactionManager;

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
        exist.setStatus(EcnStatus.PENDING_L1);
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
        recordFlow(exist.getId(), 0, "submit", from, EcnStatus.PENDING_L1, "提交ECN审批");
    }

    @Override
    @Transactional
    public void reviewL1Approve(Long id, EcnReviewDTO dto) {
        Ecn exist = getById(id);
        assertStatus(exist, EcnStatus.PENDING_L1);
        exist.setStatus(EcnStatus.PENDING_L2);
        exist.setReviewL1By(SecurityUtils.getCurrentRealName());
        exist.setReviewL1Time(LocalDateTime.now());
        exist.setReviewL1Comment(dto.getComment());
        ecnMapper.updateById(exist);
        recordFlow(exist.getId(), 1, "approve", EcnStatus.PENDING_L1, EcnStatus.PENDING_L2, dto.getComment());
    }

    @Override
    @Transactional
    public void reviewL1Reject(Long id, EcnReviewDTO dto) {
        Ecn exist = getById(id);
        assertStatus(exist, EcnStatus.PENDING_L1);
        exist.setStatus(EcnStatus.REJECTED);
        exist.setReviewL1By(SecurityUtils.getCurrentRealName());
        exist.setReviewL1Time(LocalDateTime.now());
        exist.setReviewL1Comment(dto.getComment());
        ecnMapper.updateById(exist);
        recordFlow(exist.getId(), 1, "reject", EcnStatus.PENDING_L1, EcnStatus.REJECTED, dto.getComment());
    }

    @Override
    @Transactional
    public void reviewL2Approve(Long id, EcnReviewDTO dto) {
        Ecn exist = getById(id);
        assertStatus(exist, EcnStatus.PENDING_L2);
        exist.setStatus(EcnStatus.APPROVED);
        exist.setReviewL2By(SecurityUtils.getCurrentRealName());
        exist.setReviewL2Time(LocalDateTime.now());
        exist.setReviewL2Comment(dto.getComment());
        exist.setFinalComment(dto.getComment());
        ecnMapper.updateById(exist);
        recordFlow(exist.getId(), 2, "approve", EcnStatus.PENDING_L2, EcnStatus.APPROVED, dto.getComment());
    }

    @Override
    @Transactional
    public void reviewL2Reject(Long id, EcnReviewDTO dto) {
        Ecn exist = getById(id);
        assertStatus(exist, EcnStatus.PENDING_L2);
        exist.setStatus(EcnStatus.REJECTED);
        exist.setReviewL2By(SecurityUtils.getCurrentRealName());
        exist.setReviewL2Time(LocalDateTime.now());
        exist.setReviewL2Comment(dto.getComment());
        ecnMapper.updateById(exist);
        recordFlow(exist.getId(), 2, "reject", EcnStatus.PENDING_L2, EcnStatus.REJECTED, dto.getComment());
    }

    /**
     * ECN 生效入口 (v5 §4.4 生效管线)。
     *
     * 整条管线在单事务内完成: 校验 APPROVED → EFFECTING → 逐 impact 升版(version_no 落库 +
     * 版本快照 + 旧文件 obsolete/水印) → EFFECTIVE。任一 impact 失败即整体回滚,
     * ECN 不会停留在 EFFECTIVE 而版本没升 —— 这正是 Phase 0 要修的缺陷:
     * 旧实现先独立提交 EFFECTIVE, 再对每个 impact 单独 try/catch 只打日志,
     * 失败时 ECN 已生效但 version_no 仍是旧值。
     *
     * 失败时用独立事务(REQUIRES_NEW)把 ECN 置 FAILED 并记 flow_log, 该标记不会被回滚。
     */
    @Override
    public void effect(Long id) {
        Ecn exist = getById(id);
        assertStatus(exist, EcnStatus.APPROVED);
        try {
            txTemplate(TransactionDefinition.PROPAGATION_REQUIRED)
                    .executeWithoutResult(status -> runEffectPipeline(exist));
        } catch (RuntimeException e) {
            log.error("ECN[{}] 生效失败, 已回滚并置 FAILED: {}", exist.getEcnNo(), e.getMessage(), e);
            markEffectFailed(exist.getId(), e);
            throw e;
        }
    }

    /** 生效管线主体; 运行在 effect() 开启的事务内, 异常一律向上抛以触发整体回滚 */
    private void runEffectPipeline(Ecn exist) {
        exist.setStatus(EcnStatus.EFFECTING);
        ecnMapper.updateById(exist);

        Material material = materialService.getById(exist.getMaterialId());
        if (material == null) {
            throw new BusinessException("ECN " + exist.getEcnNo() + " 关联物料不存在, 无法生效");
        }
        String oldVersion = material.getVersionNo();
        boolean stayMp = "MASS_PRODUCTION".equals(material.getPhase());

        Set<String> impactTypes = loadOrInferImpacts(exist);

        boolean partBumped = false;
        boolean bomBumped = false;
        int filesObsolete = 0;

        if (impactTypes.contains("PART")) {
            materialService.applyEcnEffect(exist.getMaterialId(), exist.getVersionAfter(),
                    exist.getEcnNo(), stayMp);
            partBumped = true;
        }
        if (impactTypes.contains("BOM")) {
            String old = bomService.bumpVersionForEcn(material.getPartNo(),
                    exist.getEcnNo(), exist.getVersionAfter());
            bomBumped = (old != null);
        }
        if (impactTypes.contains("FILE")) {
            filesObsolete = obsoleteOldVersionFiles(material.getPartNo(), oldVersion);
        }
        // SOP/TRADE/MOLD: 仅记录影响，具体动作由后续模块承接

        String result = "{\"partBumped\":" + partBumped
                + ",\"bomBumped\":" + bomBumped
                + ",\"filesObsolete\":" + filesObsolete + "}";
        for (EcnImpact imp : ecnImpactService.listByEcn(exist.getId())) {
            if ("PENDING".equals(imp.getStatus())) {
                ecnImpactService.markHandled(imp.getId(), result);
            }
        }

        exist.setStatus(EcnStatus.EFFECTIVE);
        exist.setEffectiveTime(LocalDateTime.now());
        ecnMapper.updateById(exist);

        recordFlow(exist.getId(), 3, "effect", EcnStatus.EFFECTING, EcnStatus.EFFECTIVE, "ECN生效,版本已落库");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ecnNo", exist.getEcnNo());
        payload.put("partNo", exist.getPartNo());
        payload.put("versionBefore", exist.getVersionBefore());
        payload.put("versionAfter", exist.getVersionAfter());
        payload.put("impacts", impactTypes);
        payload.put("bomChanged", bomBumped);
        domainEventService.publish("ecn.effective", "ECN", exist.getEcnNo(), payload);
    }

    /** 独立事务标记 FAILED, 不受生效管线回滚影响; 本身失败只告警, 不掩盖原始异常 */
    private void markEffectFailed(Long ecnId, Throwable cause) {
        try {
            txTemplate(TransactionDefinition.PROPAGATION_REQUIRES_NEW).executeWithoutResult(status -> {
                Ecn current = ecnMapper.selectById(ecnId);
                if (current == null || current.getStatus() == EcnStatus.EFFECTIVE) {
                    return;
                }
                EcnStatus from = current.getStatus();
                current.setStatus(EcnStatus.FAILED);
                ecnMapper.updateById(current);
                recordFlow(ecnId, 3, "effect_failed", from, EcnStatus.FAILED,
                        "生效管线失败已回滚: " + cause.getMessage());
            });
        } catch (Exception e) {
            log.error("ECN[{}] FAILED 标记写入失败: {}", ecnId, e.getMessage());
        }
    }

    private TransactionTemplate txTemplate(int propagation) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(propagation);
        return template;
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
        recordFlow(exist.getId(), 4, "void", from, EcnStatus.VOID, "ECN作废");
    }

    @Override
    public List<EcnFlowLog> getFlowLogs(Long ecnId) {
        return flowLogMapper.selectList(
                new LambdaQueryWrapper<EcnFlowLog>()
                        .eq(EcnFlowLog::getEcnId, ecnId)
                        .orderByAsc(EcnFlowLog::getCreatedAt));
    }

    private int obsoleteOldVersionFiles(String partNo, String oldVersion) {
        List<PlmFile> oldFiles = fileMapper.selectList(
                new LambdaQueryWrapper<PlmFile>()
                        .eq(PlmFile::getPartNo, partNo)
                        .eq(PlmFile::getVersionNo, oldVersion)
                        .eq(PlmFile::getObsolete, 0));
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
