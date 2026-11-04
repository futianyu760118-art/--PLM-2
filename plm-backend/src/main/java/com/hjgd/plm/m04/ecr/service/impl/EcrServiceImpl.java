package com.hjgd.plm.m04.ecr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.m04.approval.dto.ApprovalProgressVO;
import com.hjgd.plm.m04.approval.entity.ApprovalInstance;
import com.hjgd.plm.m04.approval.enums.ApprovalAction;
import com.hjgd.plm.m04.approval.service.ApprovalService;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.ecn.dto.EcnDTO;
import com.hjgd.plm.ecn.entity.Ecn;
import com.hjgd.plm.ecn.enums.EcnChangeType;
import com.hjgd.plm.ecn.mapper.EcnMapper;
import com.hjgd.plm.ecn.service.EcnService;
import com.hjgd.plm.m04.ecr.dto.EcrDTO;
import com.hjgd.plm.m04.ecr.dto.EcrQueryDTO;
import com.hjgd.plm.m04.ecr.entity.Ecr;
import com.hjgd.plm.m04.ecr.enums.EcrChangeType;
import com.hjgd.plm.m04.ecr.enums.EcrStatus;
import com.hjgd.plm.m04.ecr.mapper.EcrMapper;
import com.hjgd.plm.m04.ecr.service.EcrService;
import com.hjgd.plm.system.service.SequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EcrServiceImpl implements EcrService {

    private final EcrMapper ecrMapper;
    private final EcnMapper ecnMapper;
    private final EcnService ecnService;
    private final SequenceService sequenceService;
    private final ApprovalService approvalService;

    @Override
    public PageResult<Ecr> page(EcrQueryDTO query) {
        LambdaQueryWrapper<Ecr> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getEcrNo()), Ecr::getEcrNo, query.getEcrNo())
                .like(StringUtils.hasText(query.getPartNo()), Ecr::getPartNo, query.getPartNo())
                .like(StringUtils.hasText(query.getProjectNo()), Ecr::getProjectNo, query.getProjectNo())
                .eq(query.getChangeType() != null, Ecr::getChangeType, query.getChangeType())
                .eq(query.getStatus() != null, Ecr::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getApplicant()), Ecr::getApplicant, query.getApplicant())
                .orderByDesc(Ecr::getCreatedAt);
        Page<Ecr> page = ecrMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page);
    }

    @Override
    public Ecr getById(Long id) {
        Ecr ecr = ecrMapper.selectById(id);
        if (ecr == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return ecr;
    }

    @Override
    @Transactional
    public Ecr create(EcrDTO dto) {
        Ecr ecr = new Ecr();
        BeanUtils.copyProperties(dto, ecr);
        ecr.setEcrNo(sequenceService.nextNo("ECR_NO"));
        ecr.setStatus(EcrStatus.DRAFT);
        ecr.setUrgency(StringUtils.hasText(dto.getUrgency()) ? dto.getUrgency() : "NORMAL");
        ecr.setApplicant(SecurityUtils.getCurrentRealName());
        ecr.setApplyTime(LocalDateTime.now());
        ecr.setCreatedAt(LocalDateTime.now());
        ecr.setUpdatedAt(LocalDateTime.now());
        ecrMapper.insert(ecr);
        return ecr;
    }

    @Override
    @Transactional
    public Ecr update(EcrDTO dto) {
        Ecr exist = getById(dto.getId());
        if (exist.getStatus() != EcrStatus.DRAFT && exist.getStatus() != EcrStatus.REJECTED) {
            throw new BusinessException("只有草稿或驳回状态的 ECR 可修改");
        }
        BeanUtils.copyProperties(dto, exist, "id", "ecrNo", "status", "applicant",
                "applyTime", "approvedBy", "approvedAt", "ecnNo", "createdAt", "deleted");
        exist.setUpdatedAt(LocalDateTime.now());
        ecrMapper.updateById(exist);
        return exist;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Ecr exist = getById(id);
        if (exist.getStatus() != EcrStatus.DRAFT) {
            throw new BusinessException("只有草稿状态的 ECR 可删除");
        }
        ecrMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void submit(Long id) {
        Ecr exist = getById(id);
        if (exist.getStatus() != EcrStatus.DRAFT && exist.getStatus() != EcrStatus.REJECTED) {
            throw new BusinessException("只有草稿或驳回状态的 ECR 可提交");
        }
        ApprovalInstance inst = approvalService.start("ECR", exist.getEcrNo(), null,
                Map.of("change_type", exist.getChangeType() == null ? "" : exist.getChangeType().name()));
        if (inst == null) {
            throw new BusinessException("未配置 ECR 审批流程，请先在审批流管理中启用 ECR 流程");
        }
        exist.setStatus("RUNNING".equals(inst.getStatus()) ? EcrStatus.REVIEWING : syncOf(inst));
        exist.setUpdatedAt(LocalDateTime.now());
        ecrMapper.updateById(exist);
    }

    @Override
    @Transactional
    public void approve(Long id, ApprovalAction action, String comment) {
        Ecr exist = getById(id);
        ApprovalInstance inst = approvalService.instanceOf("ECR", exist.getEcrNo());
        if (inst == null) {
            throw new BusinessException("该 ECR 尚未提交审批");
        }
        ApprovalInstance after = approvalService.act(inst.getId(), action, comment);
        exist.setStatus(syncOf(after));
        if (after.getStatus().equals("APPROVED")) {
            exist.setApprovedBy(SecurityUtils.getCurrentRealName());
            exist.setApprovedAt(LocalDateTime.now());
        }
        exist.setUpdatedAt(LocalDateTime.now());
        ecrMapper.updateById(exist);
    }

    @Override
    @Transactional
    public Ecr toEcn(Long id) {
        Ecr exist = getById(id);
        if (exist.getStatus() != EcrStatus.APPROVED) {
            throw new BusinessException("只有审批通过的 ECR 才能转 ECN");
        }
        if (StringUtils.hasText(exist.getEcnNo())) {
            throw new BusinessException("该 ECR 已转过 ECN：" + exist.getEcnNo());
        }
        if (!StringUtils.hasText(exist.getPartNo())) {
            throw new BusinessException("ECR 未填关联料号，无法转 ECN");
        }
        EcnDTO dto = new EcnDTO();
        dto.setPartNo(exist.getPartNo());
        dto.setChangeType(mapToEcnType(exist.getChangeType()));
        dto.setChangeReason(exist.getChangeReason());
        dto.setChangeLocation(exist.getChangeContent());
        dto.setImpactScope(exist.getImpactAssessment());
        Ecn ecn = ecnService.create(dto);

        ecn.setEcrId(exist.getId());
        ecn.setEcrNo(exist.getEcrNo());
        ecnMapper.updateById(ecn);

        exist.setEcnNo(ecn.getEcnNo());
        exist.setStatus(EcrStatus.CONVERTED);
        exist.setUpdatedAt(LocalDateTime.now());
        ecrMapper.updateById(exist);
        log.info("ECR[{}] 转 ECN[{}]", exist.getEcrNo(), ecn.getEcnNo());
        return exist;
    }

    @Override
    public ApprovalProgressVO progress(Long id) {
        Ecr exist = getById(id);
        return approvalService.progress("ECR", exist.getEcrNo());
    }

    @Override
    @Transactional
    public void close(Long id) {
        Ecr exist = getById(id);
        if (exist.getStatus() == EcrStatus.CONVERTED) {
            throw new BusinessException("已转 ECN 的 ECR 不能关闭");
        }
        approvalService.cancelByObject("ECR", exist.getEcrNo(), "ECR 关闭");
        exist.setStatus(EcrStatus.CLOSED);
        exist.setUpdatedAt(LocalDateTime.now());
        ecrMapper.updateById(exist);
    }

    private EcrStatus syncOf(ApprovalInstance inst) {
        return switch (inst.getStatus()) {
            case "RUNNING" -> EcrStatus.REVIEWING;
            case "APPROVED" -> EcrStatus.APPROVED;
            case "REJECTED" -> EcrStatus.REJECTED;
            case "CANCELLED" -> EcrStatus.CLOSED;
            default -> EcrStatus.REVIEWING;
        };
    }

    /**
     * ECR 变更类型 → ECN 变更类型。
     * ECN 没有 MATERIAL / DOCUMENT 两个枚举值，分别归到结构变更（物料替代/图纸替换本质都是结构改动），
     * 若后续 ECN 扩充枚举，改这一处即可。
     */
    private EcnChangeType mapToEcnType(EcrChangeType type) {
        if (type == null) {
            return EcnChangeType.STRUCTURE;
        }
        return switch (type) {
            case BOM -> EcnChangeType.BOM;
            case PROCESS -> EcnChangeType.PROCESS;
            case MATERIAL, DOCUMENT, STRUCTURE -> EcnChangeType.STRUCTURE;
        };
    }
}
