package com.hjgd.plm.material.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.archive.service.ArchiveTreeService;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.codegen.service.CodeGenService;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.dq.dto.DqRunResult;
import com.hjgd.plm.dq.service.DataQualityService;
import com.hjgd.plm.event.service.DomainEventService;
import com.hjgd.plm.lifecycle.service.LifecycleService;
import com.hjgd.plm.lifecycle.service.impl.LifecycleServiceImpl;
import com.hjgd.plm.material.dto.MaterialDTO;
import com.hjgd.plm.material.dto.MaterialQueryDTO;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.entity.MaterialVersion;
import com.hjgd.plm.material.enums.MaterialStatus;
import com.hjgd.plm.material.enums.MaterialType;
import com.hjgd.plm.material.mapper.MaterialMapper;
import com.hjgd.plm.material.mapper.MaterialVersionMapper;
import com.hjgd.plm.material.service.EntityHistoryService;
import com.hjgd.plm.material.service.MaterialService;
import com.hjgd.plm.material.service.MaterialSupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private final MaterialMapper materialMapper;
    private final MaterialVersionMapper materialVersionMapper;
    private final CodeGenService codeGenService;
    private final ArchiveTreeService archiveTreeService;
    private final LifecycleService lifecycleService;
    private final DataQualityService dataQualityService;
    private final DomainEventService domainEventService;
    private final ObjectMapper objectMapper;
    private final MaterialSupplierService materialSupplierService;
    private final EntityHistoryService entityHistoryService;

    @Override
    public PageResult<Material> page(MaterialQueryDTO query) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getPartNo()), Material::getPartNo, query.getPartNo())
                .like(StringUtils.hasText(query.getMaterialName()), Material::getMaterialName, query.getMaterialName())
                .eq(query.getMaterialType() != null, Material::getMaterialType, query.getMaterialType())
                .eq(query.getStatus() != null, Material::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getProductSeries()), Material::getProductSeries, query.getProductSeries())
                .eq(StringUtils.hasText(query.getProjectNo()), Material::getProjectNo, query.getProjectNo())
                .orderByDesc(Material::getCreatedAt);
        Page<Material> page = materialMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page);
    }

    @Override
    public Material getById(Long id) {
        Material m = materialMapper.selectById(id);
        if (m == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return m;
    }

    @Override
    public Material getByPartNo(String partNo) {
        return materialMapper.selectOne(
                new LambdaQueryWrapper<Material>().eq(Material::getPartNo, partNo));
    }

    @Override
    @Transactional
    public Material create(MaterialDTO dto) {
        String partNo = dto.getPartNo();
        if (StringUtils.hasText(partNo)) {
            if (!SecurityUtils.hasRole("ADMIN") && !SecurityUtils.hasPermission("material:override-code")) {
                throw new BusinessException("禁止手工指定料号，请留空由系统自动生成");
            }
            if (checkPartNoDuplicate(partNo, null)) {
                throw new BusinessException(ResultCode.PART_NO_DUPLICATE);
            }
        } else {
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("materialType", dto.getMaterialType() == null ? null : dto.getMaterialType().name());
            ctx.put("productType", dto.getProductType());
            partNo = codeGenService.allocate("PART", ctx, null, "UI");
        }

        Material material = new Material();
        BeanUtils.copyProperties(dto, material);
        material.setPartNo(partNo);
        material.setStatus(MaterialStatus.DRAFT);
        material.setVersionNo("V1.0");
        material.setPhase(StringUtils.hasText(dto.getPhase()) ? dto.getPhase() : "CONCEPT");
        material.setPartCategory(resolveCategory(dto));
        if (!StringUtils.hasText(material.getUnit())) {
            material.setUnit("PCS");
        }
        material.setCreatedBy(SecurityUtils.getCurrentRealName());

        // 图纸号变更 → 自动拼接描述
        syncDrawingIntoDescription(material);

        DqRunResult dq = dataQualityService.runForPart(material, "CREATE");
        dataQualityService.assertNoBlock(dq);

        materialMapper.insert(material);
        try {
            archiveTreeService.generateForPart(material.getPartNo(),
                    material.getPartCategory(),
                    material.getProductType(),
                    material.getMaterialType() == null ? null : material.getMaterialType().name());
        } catch (Exception e) {
            log.warn("archive tree gen failed: {}", e.getMessage());
        }

        // 写入多阶梯供应商
        materialSupplierService.replaceSuppliers(material.getId(), dto.getSuppliers());

        // 记录创建历史
        entityHistoryService.recordCreate("PART", material.getPartNo(),
                toHistorySnapshot(material), "USER", SecurityUtils.getCurrentRealName());

        domainEventService.publish("part.created", "PART", material.getPartNo(), Map.of(
                "partNo", material.getPartNo(),
                "status", material.getStatus().name(),
                "versionNo", material.getVersionNo()
        ));
        return material;
    }

    /**
     * 若 drawingNo 或 drawingRevision 有值,自动追加到规格描述里,
     * 形如: 规格原值 | 图纸 DX202401 V2.0
     */
    private void syncDrawingIntoDescription(Material m) {
        String dn = m.getDrawingNo();
        String dr = m.getDrawingRevision();
        if (!StringUtils.hasText(dn) && !StringUtils.hasText(dr)) return;
        String tag = "图纸 " + (dn == null ? "" : dn) + (dr == null || dr.isBlank() ? "" : " " + dr);
        String spec = m.getSpecification() == null ? "" : m.getSpecification();
        if (spec.contains(tag)) return; // 已包含,不重复追加
        m.setSpecification(spec.isEmpty() ? tag : spec + " | " + tag);
    }

    private Map<String, Object> toHistorySnapshot(Material m) {
        Map<String, Object> snap = new HashMap<>();
        snap.put("partNo", m.getPartNo());
        snap.put("materialName", m.getMaterialName());
        snap.put("nameEn", m.getNameEn());
        snap.put("materialType", m.getMaterialType() == null ? null : m.getMaterialType().name());
        snap.put("partCategory", m.getPartCategory());
        snap.put("productType", m.getProductType());
        snap.put("unit", m.getUnit());
        snap.put("versionNo", m.getVersionNo());
        snap.put("status", m.getStatus() == null ? null : m.getStatus().name());
        snap.put("specification", m.getSpecification());
        snap.put("ipRating", m.getIpRating());
        snap.put("powerW", m.getPowerW());
        snap.put("makeType", m.getMakeType());
        snap.put("phase", m.getPhase());
        snap.put("projectNo", m.getProjectNo());
        snap.put("standardCost", m.getStandardCost());
        snap.put("currency", m.getCostCurrency());
        snap.put("drawingNo", m.getDrawingNo());
        snap.put("drawingRevision", m.getDrawingRevision());
        snap.put("remark", m.getRemark());
        return snap;
    }

    @Override
    @Transactional
    public Material update(MaterialDTO dto) {
        Material exist = getById(dto.getId());
        if (LifecycleServiceImpl.isEditLocked(exist.getStatus())) {
            throw new BusinessException(ResultCode.MATERIAL_LOCKED);
        }
        // 快照 before
        Map<String, Object> before = toHistorySnapshot(exist);

        BeanUtils.copyProperties(dto, exist, "id", "partNo", "status", "versionNo",
                "createdBy", "createdAt", "updatedAt", "deleted");
        if (StringUtils.hasText(dto.getPartCategory())) {
            exist.setPartCategory(dto.getPartCategory());
        }
        // 同步图纸号到描述
        syncDrawingIntoDescription(exist);
        DqRunResult dq = dataQualityService.runForPart(exist, "UPDATE");
        dataQualityService.assertNoBlock(dq);
        materialMapper.updateById(exist);

        // 多阶梯供应商(完整替换)
        if (dto.getSuppliers() != null) {
            materialSupplierService.replaceSuppliers(exist.getId(), dto.getSuppliers());
        }

        // 记录变更历史(只记有差异的字段)
        entityHistoryService.recordUpdate("PART", exist.getPartNo(), before,
                toHistorySnapshot(exist), "USER", null,
                SecurityUtils.getCurrentRealName(), null);

        return exist;
    }

    @Override
    public void updateQuietly(Material m) {
        materialMapper.updateById(m);
    }

    @Override
    public java.util.List<com.hjgd.plm.material.dto.MaterialSupplierDTO> listSuppliers(Long materialId) {
        return materialSupplierService.listByMaterialId(materialId);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Material exist = getById(id);
        if (LifecycleServiceImpl.isDeleteBlocked(exist.getStatus())) {
            throw new BusinessException("当前状态物料禁止删除,请改用作废操作");
        }
        materialMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void submitReview(Long id) {
        transition(id, "submit_review", null);
    }

    @Override
    @Transactional
    public void release(Long id) {
        Material before = getById(id);
        transition(id, "release", null);
        Material after = getById(id);
        saveSnapshot(after, "正式发布", null);
        domainEventService.publish("part.released", "PART", after.getPartNo(), Map.of(
                "partNo", after.getPartNo(),
                "versionNo", after.getVersionNo(),
                "from", before.getStatus().name(),
                "to", after.getStatus().name()
        ));
        domainEventService.publish("part.state_changed", "PART", after.getPartNo(), Map.of(
                "partNo", after.getPartNo(),
                "from", before.getStatus().name(),
                "to", after.getStatus().name(),
                "versionNo", after.getVersionNo()
        ));
    }

    @Override
    @Transactional
    public void toProduction(Long id) {
        transition(id, "to_production", null, "MASS_PRODUCTION");
    }

    @Override
    @Transactional
    public void obsolete(Long id) {
        transition(id, "obsolete", null);
    }

    @Override
    @Transactional
    public void seal(Long id) {
        transition(id, "seal", null);
    }

    @Override
    @Transactional
    public void startChange(Long id) {
        transition(id, "start_change", null);
    }

    @Override
    @Transactional
    public void applyEcnEffect(Long materialId, String versionAfter, String ecnNo, boolean stayInProduction) {
        Material material = getById(materialId);
        String from = material.getStatus().name();
        String oldVersion = material.getVersionNo();

        String targetVersion = StringUtils.hasText(versionAfter) ? versionAfter : oldVersion;
        if (!StringUtils.hasText(targetVersion)) {
            throw new BusinessException("ECN " + ecnNo + " 缺少生效版本号, 拒绝升版");
        }

        if (material.getStatus() != MaterialStatus.CHANGING) {
            lifecycleService.assertTransition("PART", material.getStatus(), "start_change");
            material.setStatus(MaterialStatus.CHANGING);
        }

        material.setVersionNo(targetVersion);
        if (stayInProduction || "MASS_PRODUCTION".equals(material.getPhase())) {
            material.setStatus(lifecycleService.resolveToState("PART", MaterialStatus.CHANGING, "finish_change_mp"));
        } else {
            material.setStatus(lifecycleService.resolveToState("PART", MaterialStatus.CHANGING, "finish_change"));
        }
        materialMapper.updateById(material);

        lifecycleService.recordHistory("PART", material.getPartNo(), from, material.getStatus().name(),
                "ecn_effect", SecurityUtils.getCurrentRealName(), "ECN " + ecnNo + " " + oldVersion + "->" + versionAfter);
        saveSnapshot(material, "ECN生效升版", ecnNo);

        domainEventService.publish("part.state_changed", "PART", material.getPartNo(), Map.of(
                "partNo", material.getPartNo(),
                "from", from,
                "to", material.getStatus().name(),
                "versionNo", material.getVersionNo(),
                "ecnNo", ecnNo
        ));
        log.info("ECN effect persisted part={} version {} -> {}", material.getPartNo(), oldVersion, versionAfter);
    }

    @Override
    public DqRunResult checkQuality(Long id) {
        return dataQualityService.runForPart(getById(id), "MANUAL");
    }

    @Override
    public List<MaterialVersion> listVersions(Long id) {
        Material m = getById(id);
        return materialVersionMapper.selectList(
                new LambdaQueryWrapper<MaterialVersion>()
                        .eq(MaterialVersion::getMaterialId, m.getId())
                        .orderByDesc(MaterialVersion::getCreatedAt));
    }

    @Override
    public boolean checkPartNoDuplicate(String partNo, Long excludeId) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<Material>()
                .eq(Material::getPartNo, partNo)
                .ne(excludeId != null, Material::getId, excludeId);
        return materialMapper.selectCount(wrapper) > 0;
    }

    private void transition(Long id, String action, String comment) {
        transition(id, action, comment, null);
    }

    /**
     * 转换 = 守卫 + 动作 + 事件, 单事务。
     * 守卫: 矩阵合法流转(非法抛 LIFECYCLE_DENIED) + require_dq 时的 DQ 门禁。
     */
    private Material transition(Long id, String action, String comment, String phaseAfter) {
        Material exist = getById(id);
        MaterialStatus from = exist.getStatus();
        MaterialStatus to = lifecycleService.resolveToState("PART", from, action);
        lifecycleService.assertTransition("PART", from, action);

        if (lifecycleService.requiresDq("PART", from.name(), action)) {
            DqRunResult dq = dataQualityService.runForPart(exist, "PRE_TRANSITION");
            dataQualityService.assertNoBlock(dq);
        }

        exist.setStatus(to);
        if (StringUtils.hasText(phaseAfter)) {
            exist.setPhase(phaseAfter);
        }
        materialMapper.updateById(exist);
        lifecycleService.recordHistory("PART", exist.getPartNo(), from.name(), to.name(),
                action, SecurityUtils.getCurrentRealName(), comment);
        domainEventService.publish("part.state_changed", "PART", exist.getPartNo(), Map.of(
                "partNo", exist.getPartNo(),
                "from", from.name(),
                "to", to.name(),
                "versionNo", exist.getVersionNo()
        ));
        return exist;
    }

    private void saveSnapshot(Material material, String reason, String ecnNo) {
        try {
            MaterialVersion ver = new MaterialVersion();
            ver.setMaterialId(material.getId());
            ver.setPartNo(material.getPartNo());
            ver.setVersionNo(material.getVersionNo());
            ver.setSnapshot(objectMapper.writeValueAsString(material));
            ver.setChangeReason(reason);
            ver.setEcnNo(ecnNo);
            ver.setCreatedBy(SecurityUtils.getCurrentRealName());
            ver.setCreatedAt(LocalDateTime.now());
            materialVersionMapper.insert(ver);
        } catch (Exception e) {
            log.error("version snapshot failed part={}", material.getPartNo(), e);
            throw new BusinessException("版本快照写入失败: " + e.getMessage());
        }
    }

    private String resolveCategory(MaterialDTO dto) {
        if (StringUtils.hasText(dto.getPartCategory())) {
            return dto.getPartCategory();
        }
        if (dto.getMaterialType() == MaterialType.FINISHED) {
            return "PRODUCT";
        }
        if (dto.getMaterialType() == MaterialType.SEMI) {
            return "ASSEMBLY";
        }
        if (dto.getMaterialType() == MaterialType.STANDARD) {
            return "STANDARD";
        }
        return "COMPONENT";
    }
}
