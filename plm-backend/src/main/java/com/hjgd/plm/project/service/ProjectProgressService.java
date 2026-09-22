package com.hjgd.plm.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.file.entity.PlmFile;
import com.hjgd.plm.file.service.FileService;
import com.hjgd.plm.project.entity.Project;
import com.hjgd.plm.project.entity.ProjectNode;
import com.hjgd.plm.project.entity.ProjectNodeEvidence;
import com.hjgd.plm.project.mapper.ProjectMapper;
import com.hjgd.plm.project.mapper.ProjectNodeEvidenceMapper;
import com.hjgd.plm.project.mapper.ProjectNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 研发项目进度跟踪(小模型/试点)。
 * 19 节点矩阵 + 节点表单 + 证据文件 + 完成硬标准 + 自检(健康分)。
 * 标准见 docs/plm-project-progress-tracking-spec.md
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectProgressService {

    private final ProjectMapper projectMapper;
    private final ProjectNodeMapper nodeMapper;
    private final ProjectNodeEvidenceMapper evidenceMapper;
    private final FileService fileService;

    /** 19 节点定义: {编码, 名称, 是否关键} */
    public static final String[][] NODES = {
            {"PLAN", "计划表", "0"},
            {"BOM", "BOM", "0"},
            {"SPEC", "规格书", "0"},
            {"CONFIG", "配置表", "0"},
            {"MOLD_DRAWING", "模具图纸", "0"},
            {"MOLD_REVIEW", "开模评审", "1"},
            {"HAND_SAMPLE", "手样", "0"},
            {"MOLD", "模具", "1"},
            {"MOLD_SAMPLE", "模样", "0"},
            {"PACKAGING", "包装设计", "0"},
            {"ELEC_TRIAL", "电试", "0"},
            {"RD_TRIAL", "研试", "1"},
            {"ENG_TRIAL", "工试", "1"},
            {"PROD_TRIAL", "生试", "1"},
            {"TEST_REPORT", "测试报告", "1"},
            {"TECH_TRANSFER", "技转", "1"},
            {"SHIPMENT", "出货", "0"},
            {"REVIEW", "复盘", "0"},
            {"OTHER", "其他", "0"}
    };

    private static final List<String> DONE_LIKE = List.of("DONE");

    /** 项目创建时初始化 19 节点(幂等)。 */
    @Transactional
    public void initNodes(Long projectId, String projectNo) {
        Long cnt = nodeMapper.selectCount(new LambdaQueryWrapper<ProjectNode>()
                .eq(ProjectNode::getProjectId, projectId));
        if (cnt != null && cnt > 0) return;
        int seq = 0;
        for (String[] n : NODES) {
            ProjectNode node = new ProjectNode();
            node.setProjectId(projectId);
            node.setProjectNo(projectNo);
            node.setNodeCode(n[0]);
            node.setSeq(++seq);
            node.setNodeName(n[1]);
            node.setIsKey(Integer.parseInt(n[2]));
            node.setStatus("NOT_SET");
            node.setEvidenceCount(0);
            node.setEditCount(0);
            node.setCreatedAt(LocalDateTime.now());
            node.setUpdatedAt(LocalDateTime.now());
            nodeMapper.insert(node);
        }
        log.info("[进度] 项目 {} 初始化 {} 个进度节点", projectNo, NODES.length);
    }

    public Map<String, Object> matrix(Long projectId) {
        Project p = projectMapper.selectById(projectId);
        if (p == null) throw new BusinessException("项目不存在");
        initNodes(projectId, p.getProjectNo());
        List<ProjectNode> nodes = listNodes(projectId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("project", Map.of("id", p.getId(), "projectNo", p.getProjectNo(), "projectName", p.getProjectName(),
                "status", p.getStatus() == null ? "" : p.getStatus()));
        out.put("nodes", nodes);
        out.put("summary", summary(nodes));
        return out;
    }

    public List<ProjectNode> listNodes(Long projectId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<ProjectNode>()
                .eq(ProjectNode::getProjectId, projectId)
                .orderByAsc(ProjectNode::getSeq));
    }

    /** 更新节点(表单)。完成硬标准校验。 */
    @Transactional
    public ProjectNode updateNode(Long projectId, String code, ProjectNode req) {
        ProjectNode node = nodeMapper.selectOne(new LambdaQueryWrapper<ProjectNode>()
                .eq(ProjectNode::getProjectId, projectId).eq(ProjectNode::getNodeCode, code));
        if (node == null) throw new BusinessException("节点不存在: " + code);

        if (req.getPlanDate() != null) node.setPlanDate(req.getPlanDate());
        if (req.getActualDate() != null) node.setActualDate(req.getActualDate());
        if (req.getOwner() != null) node.setOwner(req.getOwner());
        if (req.getDeliveryDesc() != null) node.setDeliveryDesc(req.getDeliveryDesc());
        if (req.getRemark() != null) node.setRemark(req.getRemark());
        if (StringUtils.hasText(req.getStatus())) node.setStatus(req.getStatus());

        validateStatus(node);

        node.setEditCount((node.getEditCount() == null ? 0 : node.getEditCount()) + 1);
        node.setUpdatedAt(LocalDateTime.now());
        nodeMapper.updateById(node);

        // 若后续再取消完成(回退), 同步刷新证据数
        node.setEvidenceCount(refreshEvidenceCount(node.getId()));
        nodeMapper.updateById(node);
        return node;
    }

    /** 完成硬标准: DONE 必须 实际日期 + 证据>=1。 */
    private void validateStatus(ProjectNode node) {
        if (DONE_LIKE.contains(node.getStatus())) {
            if (node.getActualDate() == null) {
                throw new BusinessException("完成节点必须填写「实际完成日期」");
            }
            int ev = node.getEvidenceCount() == null ? 0 : node.getEvidenceCount();
            if (ev < 1) {
                throw new BusinessException("完成节点必须上传至少 1 个证据文件（完成证据标准）");
            }
        }
    }

    /** 上传节点证据文件。 */
    @Transactional
    public ProjectNodeEvidence uploadEvidence(Long nodeId, MultipartFile file, String docType, String note) {
        ProjectNode node = nodeMapper.selectById(nodeId);
        if (node == null) throw new BusinessException("节点不存在");
        PlmFile pf = fileService.upload(file, node.getProjectNo(), StringUtils.hasText(docType) ? docType : "NODE_EVIDENCE", "INTRANET");
        ProjectNodeEvidence ev = new ProjectNodeEvidence();
        ev.setNodeId(nodeId);
        ev.setProjectId(node.getProjectId());
        ev.setNodeCode(node.getNodeCode());
        ev.setFileId(pf.getId());
        ev.setFileName(pf.getFileName());
        ev.setDocType(StringUtils.hasText(docType) ? docType : "NODE_EVIDENCE");
        ev.setNote(note);
        ev.setUploadedBy(safeUser());
        ev.setUploadedAt(LocalDateTime.now());
        evidenceMapper.insert(ev);
        node.setEvidenceCount(refreshEvidenceCount(nodeId));
        node.setUpdatedAt(LocalDateTime.now());
        nodeMapper.updateById(node);
        return ev;
    }

    public List<ProjectNodeEvidence> listEvidence(Long nodeId) {
        return evidenceMapper.selectList(new LambdaQueryWrapper<ProjectNodeEvidence>()
                .eq(ProjectNodeEvidence::getNodeId, nodeId).orderByDesc(ProjectNodeEvidence::getId));
    }

    @Transactional
    public void deleteEvidence(Long evidenceId) {
        ProjectNodeEvidence ev = evidenceMapper.selectById(evidenceId);
        if (ev == null) return;
        evidenceMapper.deleteById(evidenceId);
        ProjectNode node = nodeMapper.selectById(ev.getNodeId());
        if (node != null) {
            node.setEvidenceCount(refreshEvidenceCount(node.getId()));
            node.setUpdatedAt(LocalDateTime.now());
            nodeMapper.updateById(node);
        }
    }

    private int refreshEvidenceCount(Long nodeId) {
        Long c = evidenceMapper.selectCount(new LambdaQueryWrapper<ProjectNodeEvidence>()
                .eq(ProjectNodeEvidence::getNodeId, nodeId));
        return c == null ? 0 : c.intValue();
    }

    /** 系统内填写证据(无需上传文件)。 */
    @Transactional
    public ProjectNodeEvidence createTextEvidence(Long nodeId, String docType, String note, String content) {
        ProjectNode node = nodeMapper.selectById(nodeId);
        if (node == null) throw new BusinessException("节点不存在");
        if (!StringUtils.hasText(content)) throw new BusinessException("证据内容不能为空");
        ProjectNodeEvidence ev = new ProjectNodeEvidence();
        ev.setNodeId(nodeId);
        ev.setProjectId(node.getProjectId());
        ev.setNodeCode(node.getNodeCode());
        ev.setFileName(node.getNodeName() + "_填写_" + LocalDate.now() + ".txt");
        ev.setDocType(StringUtils.hasText(docType) ? docType : "NODE_EVIDENCE");
        ev.setNote(note);
        ev.setContent(content);
        ev.setSource("TEXT");
        ev.setMimeType("text/plain");
        ev.setVersionNo("V1");
        ev.setUploadedBy(safeUser());
        ev.setUploadedAt(LocalDateTime.now());
        evidenceMapper.insert(ev);
        node.setEvidenceCount(refreshEvidenceCount(nodeId));
        node.setUpdatedAt(LocalDateTime.now());
        nodeMapper.updateById(node);
        return ev;
    }

    /** 修改证据(操作填写: 类型/说明/内容)。 */
    @Transactional
    public ProjectNodeEvidence updateEvidence(Long evidenceId, ProjectNodeEvidence req) {
        ProjectNodeEvidence ev = evidenceMapper.selectById(evidenceId);
        if (ev == null) throw new BusinessException("证据不存在");
        if (req.getDocType() != null) ev.setDocType(req.getDocType());
        if (req.getNote() != null) ev.setNote(req.getNote());
        if (req.getContent() != null) ev.setContent(req.getContent());
        evidenceMapper.updateById(ev);
        return ev;
    }

    public ProjectNodeEvidence getEvidence(Long evidenceId) {
        ProjectNodeEvidence ev = evidenceMapper.selectById(evidenceId);
        if (ev == null) throw new BusinessException("证据不存在");
        return ev;
    }

    /** 证据台账分页(可按项目/节点/类型/来源/关键字筛选)。 */
    public PageResult<ProjectNodeEvidence> evidencePage(int pageNum, int pageSize, Long projectId,
                                                        String nodeCode, String docType, String source, String keyword) {
        LambdaQueryWrapper<ProjectNodeEvidence> w = new LambdaQueryWrapper<>();
        w.eq(projectId != null, ProjectNodeEvidence::getProjectId, projectId);
        w.eq(StringUtils.hasText(nodeCode), ProjectNodeEvidence::getNodeCode, nodeCode);
        w.eq(StringUtils.hasText(docType), ProjectNodeEvidence::getDocType, docType);
        w.eq(StringUtils.hasText(source), ProjectNodeEvidence::getSource, source);
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(ProjectNodeEvidence::getFileName, keyword)
                    .or().like(ProjectNodeEvidence::getNote, keyword)
                    .or().like(ProjectNodeEvidence::getNodeCode, keyword));
        }
        w.orderByDesc(ProjectNodeEvidence::getId);
        return PageResult.of(evidenceMapper.selectPage(new Page<>(pageNum, pageSize), w));
    }

    /** 证据分析: 总量/来源/按节点/按类型/按上传人/节点覆盖率/无证据节点。 */
    public Map<String, Object> evidenceStats(Long projectId) {
        List<ProjectNodeEvidence> all = evidenceMapper.selectList(new LambdaQueryWrapper<ProjectNodeEvidence>()
                .eq(projectId != null, ProjectNodeEvidence::getProjectId, projectId));
        Map<String, Long> bySource = new LinkedHashMap<>();
        Map<String, Long> byDocType = new LinkedHashMap<>();
        Map<String, Long> byUploader = new LinkedHashMap<>();
        Map<String, Long> byNode = new LinkedHashMap<>();
        Map<String, Long> byProject = new LinkedHashMap<>();
        for (ProjectNodeEvidence e : all) {
            bySource.merge(nullTo(e.getSource(), "FILE"), 1L, Long::sum);
            byDocType.merge(nullTo(e.getDocType(), "未分类"), 1L, Long::sum);
            byUploader.merge(nullTo(e.getUploadedBy(), "未知"), 1L, Long::sum);
            byNode.merge(nullTo(e.getNodeCode(), "?"), 1L, Long::sum);
            if (e.getProjectId() != null) byProject.merge(String.valueOf(e.getProjectId()), 1L, Long::sum);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", all.size());
        out.put("bySource", bySource);
        out.put("byDocType", byDocType);
        out.put("byUploader", byUploader);
        out.put("byNode", byNode);
        out.put("byProject", byProject);
        if (projectId != null) {
            List<ProjectNode> nodes = listNodes(projectId);
            List<Map<String, Object>> noEvidence = new ArrayList<>();
            for (ProjectNode n : nodes) {
                if (n.getEvidenceCount() == null || n.getEvidenceCount() < 1) {
                    noEvidence.add(Map.of("nodeCode", n.getNodeCode(), "nodeName", n.getNodeName(),
                            "isKey", n.getIsKey() == null ? 0 : n.getIsKey(), "status", n.getStatus()));
                }
            }
            long withEvidence = nodes.size() - noEvidence.size();
            out.put("nodeTotal", nodes.size());
            out.put("nodeWithEvidence", withEvidence);
            out.put("coverage", nodes.isEmpty() ? 0 : round((double) withEvidence / nodes.size()));
            out.put("noEvidenceNodes", noEvidence);
        }
        return out;
    }

    private String nullTo(String v, String def) { return StringUtils.hasText(v) ? v : def; }

    /** 节点统计摘要(项目级)。 */
    public Map<String, Object> summary(List<ProjectNode> nodes) {
        int total = nodes.size();
        int done = 0, keyTotal = 0, keyDone = 0, overdue = 0, notSet = 0, planMissing = 0, onTime = 0, doneWithDate = 0, planSet = 0;
        int editSum = 0;
        LocalDate today = LocalDate.now();
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (ProjectNode n : nodes) {
            byStatus.merge(n.getStatus(), 1, Integer::sum);
            boolean isDone = DONE_LIKE.contains(n.getStatus());
            if (isDone) {
                done++;
                if (n.getActualDate() != null) doneWithDate++;
                if (n.getActualDate() != null && n.getPlanDate() != null && !n.getActualDate().isAfter(n.getPlanDate())) onTime++;
            }
            if ("NOT_SET".equals(n.getStatus())) notSet++;
            if (n.getPlanDate() == null) planMissing++;
            else planSet++;
            if (n.getIsKey() != null && n.getIsKey() == 1) {
                keyTotal++;
                if (isDone) keyDone++;
            }
            if (n.getPlanDate() != null && n.getPlanDate().isBefore(today) && !isDone) overdue++;
            if (n.getEditCount() != null) editSum += n.getEditCount();
        }
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("total", total);
        s.put("done", done);
        s.put("keyTotal", keyTotal);
        s.put("keyDone", keyDone);
        s.put("overdue", overdue);
        s.put("notSet", notSet);
        s.put("planMissing", planMissing);
        s.put("nodeRate", total == 0 ? 0 : round((double) done / total));
        s.put("keyRate", keyTotal == 0 ? 0 : round((double) keyDone / keyTotal));
        s.put("byStatus", byStatus);
        s.put("editSum", editSum);
        return s;
    }

    /** 自检: 健康分 + 问题清单。 */
    public Map<String, Object> selfCheck(Long projectId) {
        Project p = projectMapper.selectById(projectId);
        if (p == null) throw new BusinessException("项目不存在");
        initNodes(projectId, p.getProjectNo());
        List<ProjectNode> nodes = listNodes(projectId);
        Map<String, Object> sum = summary(nodes);
        int total = (int) sum.get("total");
        int done = (int) sum.get("done");
        int keyTotal = (int) sum.get("keyTotal");
        int keyDone = (int) sum.get("keyDone");
        int overdue = (int) sum.get("overdue");
        int planSet = 0, onTime = 0, doneWithDate = 0;
        for (ProjectNode n : nodes) {
            if (n.getPlanDate() != null) planSet++;
            if (DONE_LIKE.contains(n.getStatus())) {
                if (n.getActualDate() != null) doneWithDate++;
                if (n.getActualDate() != null && n.getPlanDate() != null && !n.getActualDate().isAfter(n.getPlanDate())) onTime++;
            }
        }
        double ncr = total == 0 ? 0 : (double) done / total;
        double kcr = keyTotal == 0 ? 0 : (double) keyDone / keyTotal;
        double otr = doneWithDate == 0 ? 1.0 : (double) onTime / doneWithDate;
        double cr = total == 0 ? 0 : (double) planSet / total;
        int editSum = (int) sum.get("editSum");
        double cf = Math.max(0, 1 - (double) editSum / 50.0);
        int health = (int) Math.round(100 * (0.35 * ncr + 0.25 * kcr + 0.20 * otr + 0.10 * cr + 0.10 * cf));

        List<Map<String, Object>> issues = new ArrayList<>();
        LocalDate today = LocalDate.now();
        boolean projectClosed = "CLOSED".equals(p.getStatus()) || "MP".equals(p.getStatus());
        for (ProjectNode n : nodes) {
            boolean isDone = DONE_LIKE.contains(n.getStatus());
            if (isDone) {
                if (n.getActualDate() == null || (n.getEvidenceCount() == null || n.getEvidenceCount() < 1)) {
                    addIssue(issues, "HIGH", n, "完成证据不足(缺实际日期或证据文件)");
                }
            }
            if (n.getIsKey() != null && n.getIsKey() == 1 && !isDone && !projectClosed) {
                addIssue(issues, "HIGH", n, "关键节点未闭环");
            }
            if (n.getPlanDate() != null && n.getPlanDate().isBefore(today) && !isDone) {
                addIssue(issues, "HIGH", n, "逾期未完成(" + n.getPlanDate() + ")");
            }
            if ("NOT_SET".equals(n.getStatus())) {
                addIssue(issues, "MEDIUM", n, "节点未设置");
            }
            if (n.getIsKey() != null && n.getIsKey() == 1 && n.getPlanDate() == null) {
                addIssue(issues, "MEDIUM", n, "关键节点缺少计划日期");
            }
        }
        long high = issues.stream().filter(i -> "HIGH".equals(i.get("severity"))).count();
        long medium = issues.stream().filter(i -> "MEDIUM".equals(i.get("severity"))).count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectId", projectId);
        out.put("projectNo", p.getProjectNo());
        out.put("projectName", p.getProjectName());
        out.put("totalRows", total);
        out.put("score", Math.max(0, Math.min(100, health)));
        out.put("completeness", round(cr));
        out.put("metrics", Map.of("ncr", round(ncr), "kcr", round(kcr), "otr", round(otr), "cr", round(cr), "cf", round(cf)));
        out.put("bySeverity", Map.of("HIGH", high, "MEDIUM", medium, "LOW", 0L));
        out.put("issues", issues);
        return out;
    }

    private void addIssue(List<Map<String, Object>> issues, String severity, ProjectNode n, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("severity", severity);
        m.put("nodeCode", n.getNodeCode());
        m.put("nodeName", n.getNodeName());
        m.put("isKey", n.getIsKey());
        m.put("status", n.getStatus());
        m.put("message", message);
        issues.add(m);
    }

    private double round(double v) { return Math.round(v * 1000.0) / 1000.0; }

    private String safeUser() {
        try { return SecurityUtils.getCurrentRealName(); } catch (Exception e) { return ""; }
    }
}
