package com.hjgd.plm.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.evidence.entity.AeosEvidence;
import com.hjgd.plm.evidence.mapper.AeosEvidenceMapper;
import com.hjgd.plm.file.entity.PlmFile;
import com.hjgd.plm.file.service.FileService;
import com.hjgd.plm.project.entity.Project;
import com.hjgd.plm.project.entity.ProjectChange;
import com.hjgd.plm.project.entity.ProjectNode;
import com.hjgd.plm.project.entity.ProjectNodeApproval;
import com.hjgd.plm.project.entity.ProjectNodeEvidence;
import com.hjgd.plm.project.mapper.ProjectChangeMapper;
import com.hjgd.plm.project.mapper.ProjectMapper;
import com.hjgd.plm.project.mapper.ProjectNodeApprovalMapper;
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
    private final ProjectNodeApprovalMapper approvalMapper;
    private final ProjectChangeMapper changeMapper;
    private final AeosEvidenceMapper aeosEvidenceMapper;
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
        if (StringUtils.hasText(req.getStatus())) {
            if (List.of("READY_FOR_APPROVAL", "RD_APPROVED").contains(req.getStatus())) {
                throw new BusinessException("审批中间状态只能通过关键节点审批接口变更");
            }
            node.setStatus(req.getStatus());
        }

        validateStatus(node);

        node.setEditCount((node.getEditCount() == null ? 0 : node.getEditCount()) + 1);
        node.setUpdatedAt(LocalDateTime.now());
        nodeMapper.updateById(node);

        // 若后续再取消完成(回退), 同步刷新证据数
        node.setEvidenceCount(refreshEvidenceCount(node.getId()));
        nodeMapper.updateById(node);
        return node;
    }

    /** 完成硬标准: 普通节点=实际日期+证据；关键节点再叠加 RD_LEAD→GM 双级审批。 */
    private void validateStatus(ProjectNode node) {
        if (DONE_LIKE.contains(node.getStatus())) {
            if (node.getActualDate() == null) {
                throw new BusinessException("完成节点必须填写「实际完成日期」");
            }
            int ev = node.getEvidenceCount() == null ? 0 : node.getEvidenceCount();
            if (ev < 1) {
                throw new BusinessException("完成节点必须上传至少 1 个证据文件（完成证据标准）");
            }
            if (isKeyNode(node) && !isApprovalComplete(node.getId())) {
                throw new BusinessException("关键节点必须完成「研发主管一审 → 总经理二审」后才能置 DONE");
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
        ev.setAeosEvidenceId(newEvidenceId());
        evidenceMapper.insert(ev);
        registerAeosEvidence(node, ev);
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
        revokeAeosEvidence(ev.getAeosEvidenceId());
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
        ev.setAeosEvidenceId(newEvidenceId());
        evidenceMapper.insert(ev);
        registerAeosEvidence(node, ev);
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

    /** 自检: AEOS-RD-HS-V0.2 健康分 + 问题清单。 */
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

        int onTime = 0, doneWithPlan = 0;
        for (ProjectNode n : nodes) {
            if (DONE_LIKE.contains(n.getStatus())
                    && n.getActualDate() != null
                    && n.getPlanDate() != null) {
                doneWithPlan++;
                if (!n.getActualDate().isAfter(n.getPlanDate())) onTime++;
            }
        }

        double ncr = total == 0 ? 0 : (double) done / total;
        double kcr = keyTotal == 0 ? 0 : (double) keyDone / keyTotal;
        double otr = doneWithPlan == 0 ? 1.0 : (double) onTime / doneWithPlan;
        double cr = projectCompleteness(nodes);

        Long changeCountLong = changeMapper.selectCount(new LambdaQueryWrapper<ProjectChange>()
                .eq(ProjectChange::getProjectId, projectId)
                .in(ProjectChange::getStatus, List.of("APPROVED", "EFFECTIVE")));
        int changeCount = changeCountLong == null ? 0 : changeCountLong.intValue();
        double cf = Math.max(0, 1 - (double) changeCount / 10.0);

        int health = (int) Math.round(100 * (0.35 * ncr + 0.25 * kcr + 0.20 * otr + 0.10 * cr + 0.10 * cf));

        List<Map<String, Object>> issues = new ArrayList<>();
        LocalDate today = LocalDate.now();
        boolean projectClosed = "CLOSED".equals(p.getStatus()) || "MP".equals(p.getStatus());
        for (ProjectNode n : nodes) {
            boolean isDone = DONE_LIKE.contains(n.getStatus());
            if (isDone && (n.getActualDate() == null || (n.getEvidenceCount() == null || n.getEvidenceCount() < 1))) {
                addIssue(issues, "HIGH", n, "完成证据不足(缺实际日期或证据文件)");
            }
            if (isKeyNode(n) && isDone && !isApprovalComplete(n.getId())) {
                addIssue(issues, "HIGH", n, "关键节点DONE但双级审批不完整");
            }
            if (isKeyNode(n) && !isDone && !projectClosed) {
                addIssue(issues, "HIGH", n, "关键节点未闭环");
            }
            if (n.getPlanDate() != null && n.getPlanDate().isBefore(today) && !isDone) {
                addIssue(issues, "HIGH", n, "逾期未完成(" + n.getPlanDate() + ")");
            }
            if ("NOT_SET".equals(n.getStatus())) {
                addIssue(issues, "MEDIUM", n, "节点未设置");
            }
            if (isKeyNode(n) && n.getPlanDate() == null) {
                addIssue(issues, "MEDIUM", n, "关键节点缺少计划日期");
            }
        }
        long high = issues.stream().filter(i -> "HIGH".equals(i.get("severity"))).count();
        long medium = issues.stream().filter(i -> "MEDIUM".equals(i.get("severity"))).count();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("ncr", round(ncr));
        metrics.put("kcr", round(kcr));
        metrics.put("otr", round(otr));
        metrics.put("cr", round(cr));
        metrics.put("cf", round(cf));
        metrics.put("approvedChangeCount", changeCount);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectId", projectId);
        out.put("projectNo", p.getProjectNo());
        out.put("projectName", p.getProjectName());
        out.put("formulaVersion", "AEOS-RD-HS-V0.2");
        out.put("totalRows", total);
        out.put("score", Math.max(0, Math.min(100, health)));
        out.put("completeness", round(cr));
        out.put("metrics", metrics);
        out.put("bySeverity", Map.of("HIGH", high, "MEDIUM", medium, "LOW", 0L));
        out.put("issues", issues);
        return out;
    }

    /** 关键节点提交审批：实际日期+Evidence齐全后进入READY_FOR_APPROVAL。 */
    @Transactional
    public Map<String, Object> submitApproval(Long nodeId, Long submitterId, String submitterName,
                                               String comment, String requestId) {
        ProjectNode node = nodeMapper.selectById(nodeId);
        if (node == null) throw new BusinessException("节点不存在");
        if (!isKeyNode(node)) throw new BusinessException("仅关键节点需要双级审批");
        if (DONE_LIKE.contains(node.getStatus())) throw new BusinessException("节点已完成，无需重复提交");

        if (StringUtils.hasText(requestId)) {
            ProjectNodeApproval existing = approvalMapper.selectOne(new LambdaQueryWrapper<ProjectNodeApproval>()
                    .eq(ProjectNodeApproval::getRequestId, requestId)
                    .last("LIMIT 1"));
            if (existing != null) return approvalSummary(nodeId);
        }

        node.setEvidenceCount(refreshEvidenceCount(nodeId));
        if (node.getActualDate() == null) throw new BusinessException("提交审批前必须填写实际完成日期");
        if (node.getEvidenceCount() == null || node.getEvidenceCount() < 1) {
            throw new BusinessException("提交审批前必须至少有 1 条Evidence");
        }
        if ("READY_FOR_APPROVAL".equals(node.getStatus()) || "RD_APPROVED".equals(node.getStatus())) {
            throw new BusinessException("当前节点已在审批流程中");
        }

        List<ProjectNodeApproval> old = approvalRecords(nodeId);
        int round = old.stream().map(ProjectNodeApproval::getApprovalRound)
                .filter(Objects::nonNull).max(Integer::compareTo).orElse(0) + 1;

        ProjectNodeApproval a = new ProjectNodeApproval();
        a.setNodeId(nodeId);
        a.setProjectId(node.getProjectId());
        a.setNodeCode(node.getNodeCode());
        a.setApprovalLevel("RD_LEAD");
        a.setApprovalSeq(1);
        a.setApprovalRound(round);
        a.setStatus("PENDING");
        a.setSubmittedById(submitterId);
        a.setSubmittedByName(submitterName);
        a.setComment(comment);
        a.setRequestId(requestId);
        a.setEvidenceSnapshot("{\"evidenceCount\":" + node.getEvidenceCount()
                + ",\"actualDate\":\"" + node.getActualDate() + "\"}");
        a.setSubmittedAt(LocalDateTime.now());
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        approvalMapper.insert(a);

        node.setStatus("READY_FOR_APPROVAL");
        node.setUpdatedAt(LocalDateTime.now());
        nodeMapper.updateById(node);
        return approvalSummary(nodeId);
    }

    /** RD_LEAD / GM 审批；GM批准后才真正置DONE。 */
    @Transactional
    public Map<String, Object> reviewApproval(Long nodeId, String level, String decision, String comment,
                                               Long approverId, String approverName) {
        ProjectNode node = nodeMapper.selectById(nodeId);
        if (node == null) throw new BusinessException("节点不存在");
        if (!isKeyNode(node)) throw new BusinessException("仅关键节点需要双级审批");

        String normalizedLevel = level == null ? "" : level.trim().toUpperCase(Locale.ROOT);
        String normalizedDecision = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        if (!List.of("RD_LEAD", "GM").contains(normalizedLevel)) {
            throw new BusinessException("未知审批层级");
        }
        if (!List.of("APPROVED", "REJECTED").contains(normalizedDecision)) {
            throw new BusinessException("审批结论只允许 APPROVED / REJECTED");
        }

        List<ProjectNodeApproval> records = approvalRecords(nodeId);
        int round = records.stream().map(ProjectNodeApproval::getApprovalRound)
                .filter(Objects::nonNull).max(Integer::compareTo).orElse(0);
        if (round < 1) throw new BusinessException("节点尚未提交审批");

        ProjectNodeApproval pending = records.stream()
                .filter(a -> Objects.equals(a.getApprovalRound(), round))
                .filter(a -> normalizedLevel.equals(a.getApprovalLevel()))
                .filter(a -> "PENDING".equals(a.getStatus()))
                .max(Comparator.comparing(ProjectNodeApproval::getId))
                .orElseThrow(() -> new BusinessException("当前审批层级不存在待处理记录"));

        if ("RD_LEAD".equals(normalizedLevel) && !"READY_FOR_APPROVAL".equals(node.getStatus())) {
            throw new BusinessException("节点当前不处于研发主管审批状态");
        }
        if ("GM".equals(normalizedLevel) && !"RD_APPROVED".equals(node.getStatus())) {
            throw new BusinessException("节点必须先完成研发主管一审");
        }

        if ("GM".equals(normalizedLevel) && "APPROVED".equals(normalizedDecision)) {
            ProjectNodeApproval rdApproved = records.stream()
                    .filter(a -> Objects.equals(a.getApprovalRound(), round))
                    .filter(a -> "RD_LEAD".equals(a.getApprovalLevel()))
                    .filter(a -> "APPROVED".equals(a.getStatus()))
                    .max(Comparator.comparing(ProjectNodeApproval::getId))
                    .orElseThrow(() -> new BusinessException("缺少研发主管批准记录"));
            if (rdApproved.getApproverId() != null && Objects.equals(rdApproved.getApproverId(), approverId)) {
                throw new BusinessException("研发主管与总经理审批必须由不同人员完成");
            }
        }

        pending.setStatus(normalizedDecision);
        pending.setApproverId(approverId);
        pending.setApproverName(approverName);
        pending.setComment(comment);
        pending.setDecidedAt(LocalDateTime.now());
        pending.setUpdatedAt(LocalDateTime.now());
        approvalMapper.updateById(pending);

        if ("REJECTED".equals(normalizedDecision)) {
            node.setStatus("IN_PROGRESS");
        } else if ("RD_LEAD".equals(normalizedLevel)) {
            ProjectNodeApproval gm = new ProjectNodeApproval();
            gm.setNodeId(nodeId);
            gm.setProjectId(node.getProjectId());
            gm.setNodeCode(node.getNodeCode());
            gm.setApprovalLevel("GM");
            gm.setApprovalSeq(2);
            gm.setApprovalRound(round);
            gm.setStatus("PENDING");
            gm.setSubmittedById(pending.getSubmittedById());
            gm.setSubmittedByName(pending.getSubmittedByName());
            gm.setSubmittedAt(LocalDateTime.now());
            gm.setCreatedAt(LocalDateTime.now());
            gm.setUpdatedAt(LocalDateTime.now());
            approvalMapper.insert(gm);
            node.setStatus("RD_APPROVED");
        } else {
            node.setStatus("DONE");
            validateStatus(node);
        }
        node.setUpdatedAt(LocalDateTime.now());
        nodeMapper.updateById(node);
        return approvalSummary(nodeId);
    }

    public Map<String, Object> approvalSummary(Long nodeId) {
        ProjectNode node = nodeMapper.selectById(nodeId);
        if (node == null) throw new BusinessException("节点不存在");
        List<ProjectNodeApproval> records = approvalRecords(nodeId);
        int round = records.stream().map(ProjectNodeApproval::getApprovalRound)
                .filter(Objects::nonNull).max(Integer::compareTo).orElse(0);
        List<Map<String, Object>> items = new ArrayList<>();
        for (ProjectNodeApproval a : records) {
            if (!Objects.equals(a.getApprovalRound(), round)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("level", a.getApprovalLevel());
            m.put("seq", a.getApprovalSeq());
            m.put("status", a.getStatus());
            m.put("approverId", a.getApproverId());
            m.put("approverName", a.getApproverName());
            m.put("comment", a.getComment());
            m.put("submittedAt", a.getSubmittedAt());
            m.put("decidedAt", a.getDecidedAt());
            items.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nodeId", nodeId);
        out.put("nodeCode", node.getNodeCode());
        out.put("nodeStatus", node.getStatus());
        out.put("approvalRound", round);
        out.put("complete", isApprovalComplete(nodeId));
        out.put("records", items);
        return out;
    }

    private List<ProjectNodeApproval> approvalRecords(Long nodeId) {
        return approvalMapper.selectList(new LambdaQueryWrapper<ProjectNodeApproval>()
                .eq(ProjectNodeApproval::getNodeId, nodeId)
                .orderByAsc(ProjectNodeApproval::getApprovalRound)
                .orderByAsc(ProjectNodeApproval::getApprovalSeq)
                .orderByAsc(ProjectNodeApproval::getId));
    }

    private boolean isApprovalComplete(Long nodeId) {
        List<ProjectNodeApproval> records = approvalRecords(nodeId);
        int round = records.stream().map(ProjectNodeApproval::getApprovalRound)
                .filter(Objects::nonNull).max(Integer::compareTo).orElse(0);
        if (round < 1) return false;
        boolean rd = records.stream().anyMatch(a -> Objects.equals(a.getApprovalRound(), round)
                && "RD_LEAD".equals(a.getApprovalLevel()) && "APPROVED".equals(a.getStatus()));
        boolean gm = records.stream().anyMatch(a -> Objects.equals(a.getApprovalRound(), round)
                && "GM".equals(a.getApprovalLevel()) && "APPROVED".equals(a.getStatus()));
        return rd && gm;
    }

    private boolean isKeyNode(ProjectNode node) {
        return node != null && node.getIsKey() != null && node.getIsKey() == 1;
    }

    private double projectCompleteness(List<ProjectNode> nodes) {
        int required = 0;
        int filled = 0;
        for (ProjectNode n : nodes) {
            required += 3; // plan_date / owner / delivery_desc
            if (n.getPlanDate() != null) filled++;
            if (StringUtils.hasText(n.getOwner())) filled++;
            if (StringUtils.hasText(n.getDeliveryDesc())) filled++;
            if (DONE_LIKE.contains(n.getStatus())) {
                required += 2; // actual_date / evidence
                if (n.getActualDate() != null) filled++;
                if (n.getEvidenceCount() != null && n.getEvidenceCount() > 0) filled++;
            }
        }
        return required == 0 ? 0 : (double) filled / required;
    }

    private String newEvidenceId() {
        return "EVID-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private void registerAeosEvidence(ProjectNode node, ProjectNodeEvidence ev) {
        AeosEvidence e = new AeosEvidence();
        e.setEvidenceId(ev.getAeosEvidenceId());
        e.setDomain("R&D");
        e.setProjectId(node.getProjectId());
        e.setObjectType("PROJECT_NODE");
        e.setObjectId(String.valueOf(node.getId()));
        e.setObjectVersion("EDIT-" + (node.getEditCount() == null ? 0 : node.getEditCount()));
        e.setEvidenceType(StringUtils.hasText(ev.getDocType()) ? ev.getDocType() : "NODE_EVIDENCE");
        e.setSourceType(StringUtils.hasText(ev.getSource()) ? ev.getSource() : "FILE");
        e.setSourceSystem("PLM-2");
        e.setFileId(ev.getFileId());
        e.setContentRef(ev.getContent() == null ? null : "plm_project_node_evidence.content");
        e.setRecordRef("plm_project_node_evidence:" + ev.getId());
        e.setStatus("SUBMITTED");
        e.setCreatedBy(ev.getUploadedBy());
        e.setCreatedAt(ev.getUploadedAt());
        e.setVersionNo(StringUtils.hasText(ev.getVersionNo()) ? ev.getVersionNo() : "V1");
        aeosEvidenceMapper.insert(e);
    }

    private void revokeAeosEvidence(String evidenceId) {
        if (!StringUtils.hasText(evidenceId)) return;
        AeosEvidence e = aeosEvidenceMapper.selectById(evidenceId);
        if (e == null) return;
        e.setStatus("REVOKED");
        aeosEvidenceMapper.updateById(e);
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
