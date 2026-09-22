package com.hjgd.plm.bom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.bom.dto.BomDTO;
import com.hjgd.plm.bom.dto.BomItemDTO;
import com.hjgd.plm.bom.entity.Bom;
import com.hjgd.plm.bom.entity.BomItem;
import com.hjgd.plm.bom.entity.BomVersion;
import com.hjgd.plm.bom.mapper.BomItemMapper;
import com.hjgd.plm.bom.mapper.BomMapper;
import com.hjgd.plm.bom.mapper.BomTemplateMapper;
import com.hjgd.plm.bom.mapper.BomVersionMapper;
import com.hjgd.plm.bom.entity.BomTemplate;
import com.hjgd.plm.bom.entity.BomTemplateItem;
import com.hjgd.plm.bom.service.BomService;
import com.hjgd.plm.common.ApiErrorCodes;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.lifecycle.service.LifecycleService;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.service.MaterialService;
import com.hjgd.plm.system.service.SequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BomServiceImpl implements BomService {

    private final BomMapper bomMapper;
    private final BomItemMapper bomItemMapper;
    private final BomVersionMapper bomVersionMapper;
    private final BomTemplateMapper bomTemplateMapper;
    private final SequenceService sequenceService;
    private final MaterialService materialService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final LifecycleService lifecycleService;

    @Override
    public PageResult<Bom> page(Integer pageNum, Integer pageSize, String rootPartNo, String status) {
        LambdaQueryWrapper<Bom> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(rootPartNo), Bom::getRootPartNo, rootPartNo)
                .eq(StringUtils.hasText(status), Bom::getStatus, status)
                .orderByDesc(Bom::getCreatedAt);
        Page<Bom> page = bomMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page);
    }

    @Override
    public Bom getById(Long id) {
        Bom bom = bomMapper.selectById(id);
        if (bom == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return bom;
    }

    @Override
    public Bom getByPartNo(String rootPartNo) {
        return bomMapper.selectOne(
                new LambdaQueryWrapper<Bom>().eq(Bom::getRootPartNo, rootPartNo)
                        .orderByDesc(Bom::getCreatedAt).last("LIMIT 1"));
    }

    @Override
    public Bom getByBomNo(String bomNo) {
        if (!StringUtils.hasText(bomNo)) {
            return null;
        }
        return bomMapper.selectOne(new LambdaQueryWrapper<Bom>().eq(Bom::getBomNo, bomNo));
    }

    @Override
    @Transactional
    public Bom create(BomDTO dto) {
        Material material = materialService.getByPartNo(dto.getRootPartNo());
        if (material == null) {
            throw new BusinessException("顶级成品料号不存在");
        }
        Bom bom = new Bom();
        bom.setBomNo(sequenceService.nextNo("BOM_NO"));
        bom.setRootPartNo(dto.getRootPartNo());
        bom.setMaterialId(material.getId());
        bom.setVersionNo(StringUtils.hasText(dto.getVersionNo()) ? dto.getVersionNo() : "V1.0");
        bom.setStatus("DRAFT");
        bom.setBomType(StringUtils.hasText(dto.getBomType()) ? dto.getBomType() : "EBOM");
        bom.setSource(0);
        bom.setRemark(dto.getRemark());
        bom.setCreatedBy(SecurityUtils.getCurrentRealName());
        bomMapper.insert(bom);

        BomItem root = new BomItem();
        root.setBomId(bom.getId());
        root.setParentItemId(0L);
        root.setLevelNo(1);
        root.setPartNo(material.getPartNo());
        root.setMaterialId(material.getId());
        root.setPartName(material.getMaterialName());
        root.setUnit(material.getUnit());
        root.setQuantity(BigDecimal.ONE);
        root.setVersionNo(bom.getVersionNo());
        root.setSortOrder(0);
        bomItemMapper.insert(root);
        rebuildPath(bom.getId());
        return bom;
    }

    @Override
    @Transactional
    public BomItem addItem(BomItemDTO dto) {
        Bom bom = getById(dto.getBomId());
        Material material = materialService.getByPartNo(dto.getPartNo());
        if (material == null) {
            throw new BusinessException("子件料号不存在: " + dto.getPartNo());
        }
        if (!StringUtils.hasText(dto.getPartName())) {
            dto.setPartName(material.getMaterialName());
        }
        int levelNo = 1;
        long parentId = dto.getParentItemId() == null ? 0L : dto.getParentItemId();
        if (parentId != 0L) {
            BomItem parent = bomItemMapper.selectById(parentId);
            if (parent == null) {
                throw new BusinessException(ResultCode.BOM_ITEM_NOT_FOUND);
            }
            levelNo = parent.getLevelNo() + 1;
            dto.setParentItemId(parent.getId());
            checkCycle(parent, dto.getPartNo());
        } else {
            dto.setParentItemId(0L);
        }
        BomItem item = new BomItem();
        BeanUtils.copyProperties(dto, item);
        item.setBomId(bom.getId());
        item.setMaterialId(material.getId());
        item.setLevelNo(levelNo);
        if (item.getSortOrder() == null) {
            item.setSortOrder(0);
        }
        bomItemMapper.insert(item);
        rebuildPath(bom.getId());
        log.info("BOM[{}]新增明细: {} (层级{})", bom.getBomNo(), dto.getPartNo(), levelNo);
        return item;
    }

    @Override
    @Transactional
    public BomItem updateItem(BomItemDTO dto) {
        BomItem exist = bomItemMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BusinessException(ResultCode.BOM_ITEM_NOT_FOUND);
        }
        BeanUtils.copyProperties(dto, exist, "id", "bomId", "parentItemId", "levelNo",
                "path", "createdAt", "partNo", "materialId");
        bomItemMapper.updateById(exist);
        return exist;
    }

    @Override
    @Transactional
    public void deleteItem(Long itemId) {
        BomItem item = bomItemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ResultCode.BOM_ITEM_NOT_FOUND);
        }
        List<BomItem> all = getFlatList(item.getBomId());
        Set<Long> toDelete = new HashSet<>();
        toDelete.add(itemId);
        collectChildren(all, itemId, toDelete);
        bomItemMapper.deleteBatchIds(toDelete);
        rebuildPath(item.getBomId());
        log.info("删除BOM明细: {} (含{}项子件)", itemId, toDelete.size());
    }

    @Override
    public List<BomItem> getTree(Long bomId) {
        List<BomItem> all = getFlatList(bomId);
        Map<Long, List<BomItem>> grouped = all.stream()
                .collect(Collectors.groupingBy(i -> i.getParentItemId() == null ? 0L : i.getParentItemId()));
        all.forEach(i -> i.setChildren(grouped.getOrDefault(i.getId(), Collections.emptyList())));
        return grouped.getOrDefault(0L, Collections.emptyList());
    }

    @Override
    public List<BomItem> getFlatList(Long bomId) {
        return bomItemMapper.selectList(
                new LambdaQueryWrapper<BomItem>()
                        .eq(BomItem::getBomId, bomId)
                        .orderByAsc(BomItem::getSortOrder));
    }

    /**
     * BOM 发布: 守卫(矩阵 + 角色) + 动作(门禁 + 归档版本快照) + 事件。
     * DRAFT 走 release 动作, ECN 升版后的 CHANGING 走 finish_change 动作;
     * 其余状态(已发布/作废/封存)在矩阵中无 release 出边, 一律拒绝。
     * BOM 的 DQ 门禁由 checkReleaseGate 承担(无 BOM 维度 DQ 执行器)。
     */
    @Override
    @Transactional
    public void release(Long bomId) {
        Bom bom = getById(bomId);
        String from = bom.getStatus();
        String action = "CHANGING".equals(from) ? "finish_change" : "release";
        lifecycleService.assertTransition("BOM", from, action);
        if (!lifecycleService.isRoleAllowed("BOM", from, action)) {
            throw new BusinessException(403, "[" + ApiErrorCodes.LIFECYCLE_DENIED
                    + "] 当前角色无权发布 BOM, 需要: "
                    + String.join("/", lifecycleService.allowedRoles("BOM", from, action)));
        }
        if ("DRAFT".equals(from)) {
            checkReleaseGate(bomId);
        }
        bom.setStatus("RELEASED");
        bom.setArchiveVersionNo(saveVersionSnapshot(bom, null, "正式发布"));
        bomMapper.updateById(bom);
        lifecycleService.recordHistory("BOM", bom.getBomNo(), from, "RELEASED", action,
                SecurityUtils.getCurrentRealName(), "BOM发布");
        log.info("BOM[{}]发布并写版本快照, 归档版本={}", bom.getBomNo(), bom.getArchiveVersionNo());
    }

    @Override
    @Transactional
    public void archiveVersion(Long bomId, String ecnNo, String reason) {
        Bom bom = getById(bomId);
        bom.setArchiveVersionNo(saveVersionSnapshot(bom, ecnNo, reason));
        bom.setEcnNo(ecnNo);
        bomMapper.updateById(bom);
        log.info("BOM[{}]版本归档, ECN={}, 归档版本={}", bom.getBomNo(), ecnNo, bom.getArchiveVersionNo());
    }

    @Override
    @Transactional
    public Bom buildMbom(Long ebomId) {
        Bom ebom = getById(ebomId);
        if (!"EBOM".equals(ebom.getBomType())) {
            throw new BusinessException("仅EBOM可构建MBOM");
        }
        Bom mbom = new Bom();
        mbom.setBomNo(sequenceService.nextNo("BOM_NO"));
        mbom.setRootPartNo(ebom.getRootPartNo());
        mbom.setMaterialId(ebom.getMaterialId());
        mbom.setVersionNo(ebom.getVersionNo());
        mbom.setStatus("DRAFT");
        mbom.setBomType("MBOM");
        mbom.setSource(0);
        mbom.setCreatedBy(SecurityUtils.getCurrentRealName());
        bomMapper.insert(mbom);

        List<BomItem> eItems = getFlatList(ebomId);
        Map<Long, Long> idMap = new HashMap<>();
        for (BomItem src : eItems) {
            BomItem copy = new BomItem();
            BeanUtils.copyProperties(src, copy, "id", "bomId", "createdAt");
            copy.setBomId(mbom.getId());
            Long oldParent = src.getParentItemId() == null ? 0L : src.getParentItemId();
            copy.setParentItemId(idMap.getOrDefault(oldParent, 0L));
            bomItemMapper.insert(copy);
            idMap.put(src.getId(), copy.getId());
        }
        rebuildPath(mbom.getId());
        log.info("MBOM[{}]从EBOM[{}]构建, {}行", mbom.getBomNo(), ebom.getBomNo(), eItems.size());
        return mbom;
    }

    @Override
    @Transactional
    public Bom buildSbom(Long sourceBomId) {
        Bom src = getById(sourceBomId);
        Bom sbom = new Bom();
        sbom.setBomNo(sequenceService.nextNo("BOM_NO"));
        sbom.setRootPartNo(src.getRootPartNo());
        sbom.setMaterialId(src.getMaterialId());
        sbom.setVersionNo(src.getVersionNo());
        sbom.setStatus("DRAFT");
        sbom.setBomType("SBOM");
        sbom.setSource(0);
        sbom.setCreatedBy(SecurityUtils.getCurrentRealName());
        bomMapper.insert(sbom);

        List<BomItem> items = getFlatList(sourceBomId);
        Map<Long, Long> idMap = new HashMap<>();
        for (BomItem s : items) {
            BomItem copy = new BomItem();
            BeanUtils.copyProperties(s, copy, "id", "bomId", "createdAt", "sbomClass");
            copy.setBomId(sbom.getId());
            Long oldParent = s.getParentItemId() == null ? 0L : s.getParentItemId();
            copy.setParentItemId(idMap.getOrDefault(oldParent, 0L));
            if (copy.getSbomClass() == null) {
                copy.setSbomClass(classifySbom(s));
            }
            bomItemMapper.insert(copy);
            idMap.put(s.getId(), copy.getId());
        }
        rebuildPath(sbom.getId());
        log.info("SBOM[{}]构建, {}行", sbom.getBomNo(), items.size());
        return sbom;
    }

    /**
     * where-used: 该料号被哪些父件/整机使用。
     * 主查询走 ltree 祖先包含 (child.path &lt;@ parent.path), 直接给出「谁用了我」的层级答案;
     * path 尚未物化的历史数据用 parent_part_no 反向查询兜底 (两列均由 rebuildPath 维护)。
     */
    @Override
    public List<Map<String, Object>> whereUsed(String partNo) {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT DISTINCT child.bom_id, b.bom_no, b.root_part_no, b.bom_type, b.version_no, b.status, "
                            + "parent.id AS parent_item_id, parent.part_no AS parent_part_no, "
                            + "parent.part_name AS parent_part_name, child.level_no AS child_level "
                            + "FROM plm_bom_item child "
                            + "JOIN plm_bom_item parent ON parent.bom_id = child.bom_id "
                            + "  AND parent.id <> child.id "
                            + "  AND child.path IS NOT NULL AND parent.path IS NOT NULL "
                            + "  AND child.path <@ parent.path "
                            + "JOIN plm_bom b ON b.id = child.bom_id "
                            + "WHERE child.part_no = ? AND b.deleted = 0 "
                            + "ORDER BY child.bom_id", partNo);
        } catch (Exception e) {
            log.warn("where-used ltree 查询失败, 回退反向料号: {}", e.getMessage());
            return jdbcTemplate.queryForList(
                    "SELECT DISTINCT bi.bom_id, b.bom_no, b.root_part_no, b.bom_type, b.version_no, b.status, "
                            + "bi.parent_item_id, bi.parent_part_no "
                            + "FROM plm_bom_item bi JOIN plm_bom b ON bi.bom_id=b.id "
                            + "WHERE bi.part_no=? AND b.deleted=0 ORDER BY bi.bom_id", partNo);
        }
    }

    @Override
    public List<Map<String, Object>> cbomExpand(String rootPartNo) {
        Bom bom = findReleased(rootPartNo, "EBOM");
        if (bom == null) {
            bom = getByPartNo(rootPartNo);
        }
        if (bom == null) {
            return Collections.emptyList();
        }
        List<BomItem> flat = getFlatList(bom.getId());
        if (flat.isEmpty()) {
            return Collections.emptyList();
        }

        // 预算每个节点的「有效用量」= 从根到该节点各层 quantity 连乘(单件成品对该零件的需求数)
        Map<Long, Long> idToParent = new HashMap<>();
        Map<Long, BigDecimal> idToQty = new HashMap<>();
        for (BomItem it : flat) {
            idToParent.put(it.getId(), it.getParentItemId() == null ? 0L : it.getParentItemId());
            idToQty.put(it.getId(), it.getQuantity() == null ? BigDecimal.ONE : it.getQuantity());
        }

        // 单价缓存(按料号)
        Map<String, BigDecimal> costCache = new HashMap<>();

        List<Map<String, Object>> lines = new ArrayList<>();
        for (BomItem item : flat) {
            // 跳过根节点(成品本身)
            if (item.getParentItemId() != null && item.getParentItemId() == 0L) {
                continue;
            }
            BigDecimal effQty = effectiveQty(item.getId(), idToParent, idToQty, new HashSet<>());
            BigDecimal unitCost = costCache.computeIfAbsent(item.getPartNo(), p -> {
                Material m = materialService.getByPartNo(p);
                return (m == null || m.getStandardCost() == null) ? BigDecimal.ZERO : m.getStandardCost();
            });
            BigDecimal extended = effQty.multiply(unitCost).setScale(4, RoundingMode.HALF_UP);
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("partNo", item.getPartNo());
            line.put("partName", item.getPartName());
            line.put("level", item.getLevelNo());
            line.put("quantity", item.getQuantity());
            line.put("effectiveQuantity", effQty);
            line.put("unit", item.getUnit());
            line.put("makeType", item.getMakeType());
            line.put("specification", item.getSpecification());
            line.put("sbomClass", item.getSbomClass());
            line.put("unitCost", unitCost);
            line.put("extendedCost", extended);
            lines.add(line);
        }
        return lines;
    }

    /** 有效用量 = 自身 quantity × 父节点有效用量(递归到根)；带环保护 */
    private BigDecimal effectiveQty(Long id, Map<Long, Long> idToParent, Map<Long, BigDecimal> idToQty, Set<Long> seen) {
        if (id == null || !seen.add(id)) {
            return BigDecimal.ONE;
        }
        BigDecimal own = idToQty.getOrDefault(id, BigDecimal.ONE);
        Long parent = idToParent.get(id);
        if (parent == null || parent == 0L) {
            return own;
        }
        return effectiveQty(parent, idToParent, idToQty, seen).multiply(own);
    }

    @Override
    @Transactional
    public String bumpVersionForEcn(String rootPartNo, String ecnNo, String newVersionNo) {
        Bom bom = findReleased(rootPartNo, "EBOM");
        if (bom == null) {
            bom = getByPartNo(rootPartNo);
        }
        if (bom == null) {
            log.info("ECN[{}]升版: 料号[{}]无BOM，跳过", ecnNo, rootPartNo);
            return null;
        }
        String oldVersion = bom.getVersionNo();
        bom.setArchiveVersionNo(saveVersionSnapshot(bom, ecnNo, "ECN " + ecnNo + " 生效升版"));
        bom.setEcnNo(ecnNo);
        if (StringUtils.hasText(newVersionNo)) {
            bom.setVersionNo(newVersionNo);
        }
        bom.setStatus("CHANGING");
        bomMapper.updateById(bom);
        log.info("BOM[{}]因ECN[{}]升版 {} -> {}, 归档版本={}, 标记待重新发布",
                bom.getBomNo(), ecnNo, oldVersion, bom.getVersionNo(), bom.getArchiveVersionNo());
        return oldVersion;
    }

    @Override
    @Transactional
    public Bom createFromTemplates(String rootPartNo, List<String> templateCodes, boolean includeOptional) {
        Material material = materialService.getByPartNo(rootPartNo);
        if (material == null) {
            throw new BusinessException("顶级成品料号不存在: " + rootPartNo);
        }
        // 拉取模板 + 子件
        List<BomTemplate> templates = new ArrayList<>();
        for (String code : templateCodes) {
            BomTemplate t = bomTemplateMapper.findByCode(code);
            if (t == null) continue;
            t.setItems(bomTemplateMapper.listItems(t.getId()));
            templates.add(t);
        }
        if (templates.isEmpty()) {
            throw new BusinessException("未找到任何有效 BOM 模板");
        }
        // 创建空 BOM
        Bom bom = new Bom();
        bom.setBomNo(sequenceService.nextNo("BOM_NO"));
        bom.setRootPartNo(rootPartNo);
        bom.setMaterialId(material.getId());
        bom.setVersionNo("V1.0");
        bom.setStatus("DRAFT");
        bom.setBomType("EBOM");
        bom.setSource(0);
        bom.setRemark("套用模板: " + String.join(",", templateCodes));
        bom.setCreatedBy(SecurityUtils.getCurrentRealName());
        bomMapper.insert(bom);

        // 写入根节点
        BomItem root = new BomItem();
        root.setBomId(bom.getId());
        root.setParentItemId(0L);
        root.setLevelNo(1);
        root.setPartNo(material.getPartNo());
        root.setMaterialId(material.getId());
        root.setPartName(material.getMaterialName());
        root.setUnit(material.getUnit());
        root.setQuantity(BigDecimal.ONE);
        root.setSortOrder(0);
        bomItemMapper.insert(root);

        // 按模板顺序合并子件
        int order = 1;
        for (BomTemplate t : templates) {
            if (t.getItems() == null) continue;
            for (BomTemplateItem ti : t.getItems()) {
                if (!includeOptional && Boolean.TRUE.equals(ti.getIsOptional())) continue;
                Material child = materialService.getByPartNo(ti.getChildPartNo());
                if (child == null) {
                    log.warn("[BomTemplate] 子件 {} 主数据缺失,跳过(套用 BOM {})", ti.getChildPartNo(), bom.getBomNo());
                    continue;
                }
                BomItem item = new BomItem();
                item.setBomId(bom.getId());
                item.setParentItemId(root.getId());
                item.setLevelNo(2);
                item.setPartNo(child.getPartNo());
                item.setMaterialId(child.getId());
                item.setPartName(child.getMaterialName() != null ? child.getMaterialName() : ti.getChildName());
                item.setQuantity(ti.getQuantity() == null ? BigDecimal.ONE : ti.getQuantity());
                item.setUnit(ti.getUnit() == null ? child.getUnit() : ti.getUnit());
                item.setMakeType(child.getMakeType());
                item.setSortOrder(order++);
                if (Boolean.TRUE.equals(ti.getIsOptional())) {
                    item.setRemark("[可选] " + ti.getRemark());
                } else if (ti.getRemark() != null) {
                    item.setRemark(ti.getRemark());
                }
                bomItemMapper.insert(item);
            }
        }
        rebuildPath(bom.getId());
        log.info("[BomTemplate] BOM[{}] 从 {} 个模板创建, 共 {} 行明细",
                bom.getBomNo(), templates.size(), order - 1);
        return bom;
    }

    @Override
    @Transactional
    public void reorderItems(Long bomId, List<Long> itemIdsInOrder) {
        if (itemIdsInOrder == null || itemIdsInOrder.isEmpty()) return;
        // 校验: 所有 itemId 都属于该 bom
        List<BomItem> all = getFlatList(bomId);
        Set<Long> allowed = new HashSet<>();
        for (BomItem i : all) if (i.getParentItemId() != null && i.getParentItemId() != 0L) allowed.add(i.getId());
        for (int idx = 0; idx < itemIdsInOrder.size(); idx++) {
            Long id = itemIdsInOrder.get(idx);
            if (!allowed.contains(id)) {
                throw new BusinessException("BOM 明细 " + id + " 不属于 BOM " + bomId);
            }
            BomItem item = new BomItem();
            item.setId(id);
            item.setSortOrder(idx + 1);
            bomItemMapper.updateById(item);
        }
        rebuildPath(bomId);
        log.info("[BOM] 拖拽排序完成 bomId={} count={}", bomId, itemIdsInOrder.size());
    }

    private Bom findReleased(String rootPartNo, String bomType) {
        return bomMapper.selectOne(
                new LambdaQueryWrapper<Bom>()
                        .eq(Bom::getRootPartNo, rootPartNo)
                        .eq(Bom::getBomType, bomType)
                        .eq(Bom::getStatus, "RELEASED")
                        .orderByDesc(Bom::getCreatedAt).last("LIMIT 1"));
    }

    private void checkReleaseGate(Long bomId) {
        List<BomItem> items = getFlatList(bomId);
        if (items.size() <= 1) {
            throw new BusinessException("BOM无明细行，禁止发布");
        }
        for (BomItem item : items) {
            if (item.getParentItemId() != null && item.getParentItemId() != 0L) {
                if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("BOM行数量必须>0: " + item.getPartNo());
                }
            }
        }
    }

    /** @return 本次归档到 plm_bom_version 的版本号 */
    private String saveVersionSnapshot(Bom bom, String ecnNo, String reason) {
        try {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("bom", bom);
            snapshot.put("items", getFlatList(bom.getId()));
            String json = objectMapper.writeValueAsString(snapshot);

            BomVersion bv = new BomVersion();
            bv.setBomId(bom.getId());
            bv.setVersionNo(bom.getVersionNo());
            bv.setSnapshot(json);
            bv.setEcnNo(ecnNo);
            bv.setChangeReason(reason);
            bv.setCreatedBy(SecurityUtils.getCurrentRealName());
            bv.setCreatedAt(LocalDateTime.now());
            try {
                bomVersionMapper.insert(bv);
            } catch (Exception dup) {
                jdbcTemplate.update(
                        "UPDATE plm_bom_version SET snapshot=?::jsonb, ecn_no=?, change_reason=? WHERE bom_id=? AND version_no=?",
                        json, ecnNo, reason, bom.getId(), bom.getVersionNo());
            }
            return bv.getVersionNo();
        } catch (Exception e) {
            log.error("BOM版本快照失败 bomId={}", bom.getId(), e);
            throw new BusinessException("BOM版本快照写入失败");
        }
    }

    /**
     * 重算物化路径: path = 父 path . 本条 id (ltree 标签为纯数字, 合法)。
     * 同时回写 parent_part_no 支撑 where-used 的反向料号查询。
     * 父件缺失/成环即抛错 —— path 是 where-used 的唯一索引, 静默留空等于树查询失效。
     */
    private void rebuildPath(Long bomId) {
        List<BomItem> all = getFlatList(bomId);
        Map<Long, BomItem> byId = new HashMap<>();
        for (BomItem item : all) {
            byId.put(item.getId(), item);
        }
        for (BomItem item : all) {
            String path = itemPath(byId, item, new HashSet<>());
            Long parentId = item.getParentItemId() == null ? 0L : item.getParentItemId();
            BomItem parent = parentId == 0L ? null : byId.get(parentId);
            jdbcTemplate.update(
                    "UPDATE plm_bom_item SET path=?::ltree, parent_part_no=? WHERE id=?",
                    path, parent == null ? null : parent.getPartNo(), item.getId());
        }
    }

    private String itemPath(Map<Long, BomItem> byId, BomItem item, Set<Long> seen) {
        if (!seen.add(item.getId())) {
            throw new BusinessException(ResultCode.BOM_CYCLE_ERROR);
        }
        Long parentId = item.getParentItemId() == null ? 0L : item.getParentItemId();
        if (parentId == 0L) {
            return String.valueOf(item.getId());
        }
        BomItem parent = byId.get(parentId);
        if (parent == null) {
            throw new BusinessException(ResultCode.BOM_ITEM_NOT_FOUND,
                    "BOM明细 " + item.getId() + " 的父件 " + parentId + " 不存在, 无法生成 path");
        }
        return itemPath(byId, parent, seen) + "." + item.getId();
    }

    private String classifySbom(BomItem item) {
        if (item.getMakeType() != null && item.getMakeType() == 1) {
            return "C";
        }
        if (item.getLevelNo() != null && item.getLevelNo() <= 2) {
            return "A";
        }
        return "B";
    }

    private void checkCycle(BomItem parent, String newPartNo) {
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(parent.getId());
        Set<Long> visited = new HashSet<>();
        while (!stack.isEmpty()) {
            Long currentId = stack.pop();
            if (!visited.add(currentId)) {
                continue;
            }
            BomItem current = bomItemMapper.selectById(currentId);
            if (current == null) {
                continue;
            }
            if (newPartNo.equals(current.getPartNo())) {
                throw new BusinessException(ResultCode.BOM_CYCLE_ERROR);
            }
            if (current.getParentItemId() != null && current.getParentItemId() != 0L) {
                stack.push(current.getParentItemId());
            }
        }
    }

    private void collectChildren(List<BomItem> all, Long parentId, Set<Long> acc) {
        for (BomItem item : all) {
            if (parentId.equals(item.getParentItemId()) && !acc.contains(item.getId())) {
                acc.add(item.getId());
                collectChildren(all, item.getId(), acc);
            }
        }
    }
}
