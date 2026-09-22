package com.hjgd.plm.archive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.archive.entity.ArchiveFile;
import com.hjgd.plm.archive.entity.ArchiveTreeNode;
import com.hjgd.plm.archive.mapper.ArchiveFileMapper;
import com.hjgd.plm.archive.mapper.ArchiveTreeNodeMapper;
import com.hjgd.plm.archive.service.ArchiveTreeService;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.template.service.TemplateResolverService;
import com.hjgd.plm.template.service.TemplateResolverService.ArchiveTreeTpl;
import com.hjgd.plm.template.service.TemplateResolverService.TplNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveTreeServiceImpl implements ArchiveTreeService {

    private final ArchiveTreeNodeMapper nodeMapper;
    private final ArchiveFileMapper fileMapper;
    private final TemplateResolverService templateResolverService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void generateForPart(String partNo) {
        String[] cpm = lookupCategoryMaterialType(partNo);
        generateForPart(partNo, cpm[0], cpm[1], cpm[2]);
    }

    @Override
    @Transactional
    public void generateForPart(String partNo, String partCategory, String productType, String materialType) {
        if (existsForPart(partNo)) {
            log.info("料号[{}]目录树已存在,跳过生成", partNo);
            return;
        }
        ArchiveTreeTpl tpl = templateResolverService.resolveArchiveTreeTpl(partCategory, productType, materialType);

        // 两遍法：先全插(parent_id 占位 0)，再查回 code→id 重映射 parent_id
        // 不依赖模板 sort_order 的父子顺序，也不依赖 insert 后 getId() 回填
        Map<String, String> codeToParent = new HashMap<>();
        int sort = 0;
        for (TplNode tplNode : tpl.nodes) {
            ArchiveTreeNode node = new ArchiveTreeNode();
            node.setPartNo(partNo);
            node.setNodeCode(tplNode.code);
            node.setNodeName(tplNode.name);
            node.setParentId(0L);
            node.setLevelNo(tplNode.parentCode == null ? 1 : 2);
            node.setSortOrder(sort++);
            node.setIsLeaf(tplNode.leaf ? 1 : 0);
            nodeMapper.insert(node);
            codeToParent.put(tplNode.code, tplNode.parentCode);
        }
        remapParents(partNo, codeToParent);
        log.info("料号[{}]按模板[{}]生成目录树({}个节点) category={} productType={} materialType={}",
                partNo, tpl.tplCode, tpl.nodes.size(), partCategory, productType, materialType);
    }

    /** 查回已插入节点的 DB id，按 code→parentCode 重写 parent_id，保证层级正确 */
    private void remapParents(String partNo, Map<String, String> codeToParent) {
        List<ArchiveTreeNode> inserted = nodeMapper.selectList(
                new LambdaQueryWrapper<ArchiveTreeNode>().eq(ArchiveTreeNode::getPartNo, partNo));
        Map<String, Long> codeToId = new HashMap<>();
        for (ArchiveTreeNode n : inserted) {
            if (n.getId() != null) {
                codeToId.put(n.getNodeCode(), n.getId());
            }
        }
        for (ArchiveTreeNode n : inserted) {
            String parentCode = codeToParent.get(n.getNodeCode());
            if (parentCode == null) {
                continue;
            }
            Long parentId = codeToId.get(parentCode);
            if (parentId != null && !parentId.equals(n.getParentId())) {
                jdbcTemplate.update("UPDATE plm_archive_tree SET parent_id=? WHERE id=?", parentId, n.getId());
            }
        }
    }

    /** 单参数入口回查物料品类/灯具品类/材质 */
    private String[] lookupCategoryMaterialType(String partNo) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT part_category, product_type, material_type FROM plm_material WHERE part_no=?",
                    partNo);
            return new String[]{
                    row.get("part_category") == null ? null : String.valueOf(row.get("part_category")),
                    row.get("product_type") == null ? null : String.valueOf(row.get("product_type")),
                    row.get("material_type") == null ? null : String.valueOf(row.get("material_type"))
            };
        } catch (Exception e) {
            log.warn("lookup material meta failed part={}, use defaults: {}", partNo, e.getMessage());
            return new String[]{null, null, null};
        }
    }

    @Override
    public boolean existsForPart(String partNo) {
        return nodeMapper.selectCount(
                new LambdaQueryWrapper<ArchiveTreeNode>().eq(ArchiveTreeNode::getPartNo, partNo)) > 0;
    }

    @Override
    public List<ArchiveTreeNode> getTree(String partNo) {
        List<ArchiveTreeNode> all = nodeMapper.selectList(
                new LambdaQueryWrapper<ArchiveTreeNode>()
                        .eq(ArchiveTreeNode::getPartNo, partNo)
                        .orderByAsc(ArchiveTreeNode::getSortOrder));
        if (all.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<ArchiveTreeNode>> grouped = all.stream()
                .collect(Collectors.groupingBy(n -> n.getParentId() == null ? 0L : n.getParentId()));
        all.forEach(n -> n.setChildren(grouped.getOrDefault(n.getId(), Collections.emptyList())));
        return grouped.getOrDefault(0L, Collections.emptyList());
    }

    @Override
    @Transactional
    public void attachFile(String partNo, String nodeCode, Long fileId) {
        ArchiveTreeNode node = nodeMapper.selectOne(new LambdaQueryWrapper<ArchiveTreeNode>()
                .eq(ArchiveTreeNode::getPartNo, partNo)
                .eq(ArchiveTreeNode::getNodeCode, nodeCode));
        if (node == null) {
            throw new BusinessException("档案目录节点不存在: " + nodeCode);
        }
        ArchiveFile af = new ArchiveFile();
        af.setPartNo(partNo);
        af.setArchiveNodeId(node.getId());
        af.setFileId(fileId);
        fileMapper.insert(af);
        log.info("文件挂载到档案: 料号{} 节点{} 文件ID{}", partNo, node.getNodeName(), fileId);
    }

    @Override
    public List<ArchiveFile> listFiles(Long nodeId) {
        return fileMapper.selectList(
                new LambdaQueryWrapper<ArchiveFile>()
                        .eq(ArchiveFile::getArchiveNodeId, nodeId)
                        .orderByDesc(ArchiveFile::getCreatedAt));
    }

    @Override
    public void removeFile(Long archiveFileId) {
        fileMapper.deleteById(archiveFileId);
    }
}
