package com.hjgd.plm.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.file.entity.PlmFile;
import com.hjgd.plm.file.service.FileService;
import com.hjgd.plm.gate.service.GateService;
import com.hjgd.plm.metric.domain.HealthScoreDef;
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
 * 22 节点矩阵 + 节点表单 + 证据文件 + 完成硬标准 + 自检(健康分)。
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
    private final com.hjgd.plm.event.service.DomainEventService domainEventService;
    private final com.hjgd.plm.rd.service.ChangeLogService changeLogService;
    /** 统一 Gate 引擎：节点完成放行的唯一判定入口（F08）。 */
    private final GateService gateService;
    /** 统一证据底座：节点证据同时登记进 plm_evidence，形成可穿透链路（F09）。 */
    private final com.hjgd.plm.evidence.service.EvidenceService evidenceService;

    /** 22 节点定义: {编码, 名称, 是否关键} (D1: 补 APPEARANCE/STRUCTURE/ELECTRONICS) */
    public static final String[][] NODES = {
            {"PLAN", "计划表", "0"},
            {"BOM", "基础BOM", "0"},
            {"SPEC", "规格书", "0"},
            {"CONFIG", "配置表", "0"},
            {"MOLD_DRAWING", "模具图纸", "0"},
            {"MOLD_REVIEW", "开模评审", "1"},
            {"HAND_SAMPLE", "手样", "0"},
            {"APPEARANCE", "外观", "0"},
            {"STRUCTURE", "结构", "0"},
            {"ELECTRONICS", "电子", "0"},
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

    /** 与 EBMS「研发项目跟踪」表一致的列顺序(22 节点) */
    public static final List<String> TRACKING_ORDER = List.of(
            "PLAN", "BOM", "SPEC", "CONFIG", "MOLD_DRAWING", "MOLD_REVIEW",
            "HAND_SAMPLE", "APPEARANCE", "STRUCTURE", "ELECTRONICS",
            "MOLD", "MOLD_SAMPLE", "PACKAGING", "ELEC_TRIAL", "RD_TRIAL",
            "TECH_TRANSFER", "ENG_TRIAL", "PROD_TRIAL", "TEST_REPORT",
            "SHIPMENT", "REVIEW", "OTHER");

    private static final List<String> DONE_LIKE = List.of("DONE");

    /** 节点编码 -> 名称 */
    public static String nodeName(String code) {
        for (String[] n : NODES) if (n[0].equals(code)) return n[1];
        return code;
    }

    /** 节点编码 -> 是否关键 */
    public static boolean isKey(String code) {
        for (String[] n : NODES) if (n[0].equals(code)) return "1".equals(n[2]);
        return false;
    }

    /** 结构化节点 -> EBMS 单元格值 (完成=实际日期/其他日期/进行中/待定/空) */
    public static String cellValue(ProjectNode n) {
        if (n == null) return "";
        if (n.getStatus() == null) return n.getPlanDate() != null ? n.getPlanDate().toString() : "";
        return switch (n.getStatus()) {
            case "DONE" -> {
                if (n.getActualDate() != null) yield n.getActualDate().toString();
                if (n.getPlanDate() != null) yield n.getPlanDate().toString();
                if (n.getUpdatedAt() != null) yield n.getUpdatedAt().toLocalDate().toString();
                yield "V";
            }
            case "FAILED" -> "X";
            case "IN_PROGRESS" -> "进行中";
            case "PENDING" -> "待定";
            case "PLANNED" -> n.getPlanDate() != null ? n.getPlanDate().toString() : "待进行";
            default -> n.getPlanDate() != null ? n.getPlanDate().toString() : "";
        };
    }

    /**
     * 按 EBMS 单元格值写入节点(跟踪总表内联编辑)。
     *
     * <p>ISS-001 修复：内联编辑曾直接置 DONE 而绕过完成硬标准，导致「未验证即完成」
     * 可从跟踪总表进入。现在与 {@link #updateNode} 共用同一条 {@link #validateStatus}
     * 校验链，并在关键节点完成时走统一 Gate 放行判定 —— 系统内不存在第二套完成判定。
     */
    @Transactional
    public ProjectNode applyCell(Long projectId, String code, String raw) {
        ProjectNode node = nodeMapper.selectOne(new LambdaQueryWrapper<ProjectNode>()
                .eq(ProjectNode::getProjectId, projectId).eq(ProjectNode::getNodeCode, code));
        if (node == null) throw new BusinessException("节点不存在: " + code);
        String oldStatus = node.getStatus();
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty() || "-".equals(v) || "/".equals(v)) {
            node.setStatus("NOT_SET");
        } else if (v.equals("V") || v.equals("√") || v.equals("完成")) {
            node.setStatus("DONE");
            if (node.getActualDate() == null) node.setActualDate(LocalDate.now());
        } else if (v.equals("X") || v.equals("未完成")) {
            node.setStatus("FAILED");
        } else if (v.equals("进行中")) {
            node.setStatus("IN_PROGRESS");
        } else if (v.equals("待定") || v.equals("暂停")) {
            node.setStatus("PENDING");
        } else if (v.equals("待进行") || v.equals("待设置")) {
            node.setStatus("PLANNED");
        } else if (v.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            node.setStatus("PLANNED");
            node.setPlanDate(LocalDate.parse(v.substring(0, 10)));
        } else {
            node.setRemark(v);
        }
        node.setEditCount((node.getEditCount() == null ? 0 : node.getEditCount()) + 1);
        node.setUpdatedAt(LocalDateTime.now());
        validateFacts(node);
        nodeMapper.updateById(node);
        validateGate(node, oldStatus);
        publishNodeEvent(node);
        changeLogService.record(projectId, node.getProjectNo(), "PROJECT_NODE", node.getId(),
                node.getNodeCode(), "CELL", "status", oldStatus, node.getStatus(), "UI");
        return node;
    }

    private void publishNodeEvent(ProjectNode node) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", node.getStatus());
            payload.put("plan_date", node.getPlanDate() == null ? null : node.getPlanDate().toString());
            payload.put("actual_date", node.getActualDate() == null ? null : node.getActualDate().toString());
            payload.put("owner", node.getOwner());
            payload.put("remark", node.getRemark());
            domainEventService.publish("m04.project_node.updated", "PROJECT_NODE",
                    node.getProjectNo() + ":" + node.getNodeCode(), payload);
        } catch (Exception e) {
            log.warn("[进度] 发布节点事件失败: {}", e.getMessage());
        }
    }

    /** 初始化/补齐进度节点(幂等): 缺失则插入, 顺序按 NODES 重排。 */
    @Transactional
    public void initNodes(Long projectId, String projectNo) {
        Map<String, ProjectNode> existing = new HashMap<>();
        for (ProjectNode n : nodeMapper.selectList(new LambdaQueryWrapper<ProjectNode>()
                .eq(ProjectNode::getProjectId, projectId))) {
            existing.put(n.getNodeCode(), n);
        }
        int seq = 0;
        int added = 0;
        for (String[] n : NODES) {
            int mySeq = ++seq;
            ProjectNode node = existing.get(n[0]);
            if (node == null) {
                node = new ProjectNode();
                node.setProjectId(projectId);
                node.setProjectNo(projectNo);
                node.setNodeCode(n[0]);
                node.setSeq(mySeq);
                node.setNodeName(n[1]);
                node.setIsKey(Integer.parseInt(n[2]));
                node.setStatus("NOT_SET");
                node.setEvidenceCount(0);
                node.setEditCount(0);
                node.setCreatedAt(LocalDateTime.now());
                node.setUpdatedAt(LocalDateTime.now());
                nodeMapper.insert(node);
                added++;
            } else if (node.getSeq() == null || node.getSeq() != mySeq) {
                node.setSeq(mySeq);
                node.setUpdatedAt(LocalDateTime.now());
                nodeMapper.updateById(node);
            }
        }
        if (added > 0) {
            log.info("[进度] 项目 {} 补齐 {} 个节点(共 {} 个)", projectNo, added, NODES.length);
        }
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
        String oldStatus = node.getStatus();

        if (req.getPlanDate() != null) node.setPlanDate(req.getPlanDate());
        if (req.getActualDate() != null) node.setActualDate(req.getActualDate());
        if (req.getOwner() != null) node.setOwner(req.getOwner());
        if (req.getDeliveryDesc() != null) node.setDeliveryDesc(req.getDeliveryDesc());
        if (req.getRemark() != null) node.setRemark(req.getRemark());
        if (StringUtils.hasText(req.getStatus())) node.setStatus(req.getStatus());

        validateFacts(node);

        node.setEditCount((node.getEditCount() == null ? 0 : node.getEditCount()) + 1);
        node.setUpdatedAt(LocalDateTime.now());
        nodeMapper.updateById(node);

        validateGate(node, oldStatus);

        // 若后续再取消完成(回退), 同步刷新证据数
        node.setEvidenceCount(refreshEvidenceCount(node.getId()));
        nodeMapper.updateById(node);

        // 发布领域事件(供同步中枢出站 EBMS)
        publishNodeEvent(node);
        // 变更列表留痕
        changeLogService.record(projectId, node.getProjectNo(), "PROJECT_NODE", node.getId(),
                node.getNodeCode(), "UPDATE", "status", oldStatus, node.getStatus(), "UI");
        return node;
    }

    /**
     * 完成硬标准（事实层，内存可判定）：DONE 必须 实际日期 + 证据>=1。
     * 与 {@link #validateGate} 一起构成唯一的完成判定链，表单与内联编辑共用。
     */
    private void validateFacts(ProjectNode node) {
        if (!DONE_LIKE.contains(node.getStatus())) return;
        if (node.getActualDate() == null) {
            throw new BusinessException("完成节点必须填写「实际完成日期」");
        }
        int ev = node.getEvidenceCount() == null ? 0 : node.getEvidenceCount();
        if (ev < 1) {
            throw new BusinessException("完成节点必须上传至少 1 个证据文件（完成证据标准）");
        }
    }

    /**
     * 放行判定（统一 Gate）：关键节点「进入」DONE 时，必须通过 GATE_NODE_KEY_DONE
     * —— 该门包含双级审批（研发主管 → 总经理）与证据规则（F07/F08）。
     *
     * <p>只在状态由非 DONE 变为 DONE 的那一刻判定：已经放行的节点再次编辑（改备注等）
     * 不重复判门，避免历史已完成节点因缺少审批记录而无法修改。
     *
     * <p>必须在节点事实入库之后调用 —— 门的 NODE 规则读取的是库里的实际完成日期。
     * 判定不通过时抛出异常，所在事务整体回滚，节点不会停在「未审批的 DONE」。
     */
    private void validateGate(ProjectNode node, String oldStatus) {
        if (!DONE_LIKE.contains(node.getStatus()) || DONE_LIKE.contains(oldStatus)) return;
        if (node.getIsKey() == null || node.getIsKey() != 1) return;
        gateService.assertPassed(GateService.GATE_NODE_KEY_DONE, "node", String.valueOf(node.getId()));
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
        registerUnifiedEvidence(node, ev, "file", pf.getId() == null ? null : String.valueOf(pf.getId()));
        node.setEvidenceCount(refreshEvidenceCount(nodeId));
        node.setUpdatedAt(LocalDateTime.now());
        nodeMapper.updateById(node);
        return ev;
    }

    /**
     * 节点证据同时登记进统一证据底座 {@code plm_evidence}（F09 / ISS-005）。
     *
     * <p>旧证据表继续作为业务明细使用（兼容既有页面），统一底座只保存引用：
     * {@code source_record_id} 指向原始记录（上传件指向 {@code plm_file.id}），
     * {@code legacy_ref_*} 回指旧表行，两者共同构成「管理结果 → 证据 → 原始记录」的穿透链。
     *
     * <p>登记失败不阻断证据上传（登记只是补链），但会记录告警——失败时该条证据
     * 会在穿透查询里表现为断点，由自检暴露，而不是被静默忽略。
     */
    private void registerUnifiedEvidence(ProjectNode node, ProjectNodeEvidence ev,
                                         String evidenceType, String sourceRecordId) {
        try {
            evidenceService.register(new com.hjgd.plm.evidence.service.EvidenceService.RegisterCommand(
                    "node", String.valueOf(node.getId()), evidenceType,
                    "PLM2", StringUtils.hasText(sourceRecordId) ? sourceRecordId : String.valueOf(ev.getId()),
                    null, null,
                    StringUtils.hasText(ev.getVersionNo()) ? ev.getVersionNo() : "V1",
                    java.math.BigDecimal.ONE,
                    null, null, node.getProjectNo(),
                    "plm_project_node_evidence", String.valueOf(ev.getId())));
        } catch (Exception e) {
            log.warn("[进度] 统一证据登记失败 node={} evidence={}: {}", node.getId(), ev.getId(), e.getMessage());
        }
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
        registerUnifiedEvidence(node, ev, "record", String.valueOf(ev.getId()));
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
        long requiredFields = 0, satisfiedFields = 0;
        for (ProjectNode n : nodes) {
            if (n.getPlanDate() != null) planSet++;
            boolean isDone = DONE_LIKE.contains(n.getStatus());
            if (isDone) {
                if (n.getActualDate() != null) doneWithDate++;
                if (n.getActualDate() != null && n.getPlanDate() != null && !n.getActualDate().isAfter(n.getPlanDate())) onTime++;
            }
            boolean isKeyNode = n.getIsKey() != null && n.getIsKey() == 1;
            requiredFields += HealthScoreDef.requiredFieldCount(isKeyNode, isDone);
            satisfiedFields += satisfiedFieldCount(n, isKeyNode, isDone);
        }
        // 口径唯一来源: com.hjgd.plm.metric.domain.HealthScoreDef（F10 / ISS-002 / ISS-003）
        // ncr/kcr/otr 与字段完整率 cr 的定义式、CF 的取数来源全部由该口径类给出，
        // 实现侧只负责提供分子分母，不再自行定义公式。
        double ncr = HealthScoreDef.nodeCompletionRate(done, total);
        double kcr = HealthScoreDef.keyCompletionRate(keyDone, keyTotal);
        double otr = HealthScoreDef.onTimeRate(onTime, doneWithDate);
        double cr = HealthScoreDef.fieldCompleteness(satisfiedFields, requiredFields);
        // CF 取「正式变更次数」（CREATE/UPDATE/DELETE），不再用节点编辑次数近似——
        // 后者把系统派生的 ROLLUP/CELL 回写也算成变更，是 ISS-002 的口径错误。
        long formalChanges = changeLogService.countFormalChanges(p.getProjectNo());
        double cf = HealthScoreDef.changeFactor((int) Math.min(Integer.MAX_VALUE, formalChanges));
        int health = HealthScoreDef.health(ncr, kcr, otr, cr, cf);

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
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("ncr", round(ncr));
        metrics.put("kcr", round(kcr));
        metrics.put("otr", round(otr));
        metrics.put("cr", round(cr));
        metrics.put("cf", round(cf));
        // 分子分母一并给出，使口径可复核（定义式与实现值不再有偏差空间）
        metrics.put("requiredFields", requiredFields);
        metrics.put("satisfiedFields", satisfiedFields);
        metrics.put("formalChanges", formalChanges);
        metrics.put("planDateCoverage", total == 0 ? 0.0 : round((double) planSet / total));
        out.put("metrics", metrics);
        out.put("caliber", caliber());
        out.put("bySeverity", Map.of("HIGH", high, "MEDIUM", medium, "LOW", 0L));
        out.put("issues", issues);
        return out;
    }

    /** 该节点在当前状态下满足的必填项数（口径见 {@link HealthScoreDef.RequiredField}）。 */
    private static long satisfiedFieldCount(ProjectNode n, boolean isKeyNode, boolean isDone) {
        long ok = 0;
        if (StringUtils.hasText(n.getStatus()) && !"NOT_SET".equals(n.getStatus())) ok++;
        if (StringUtils.hasText(n.getNodeName())) ok++;
        if (n.getSeq() != null) ok++;
        if (isKeyNode) {
            if (n.getPlanDate() != null) ok++;
            if (StringUtils.hasText(n.getOwner())) ok++;
        }
        if (isDone) {
            if (n.getActualDate() != null) ok++;
            if (n.getEvidenceCount() != null && n.getEvidenceCount() >= 1) ok++;
        }
        return ok;
    }

    /** 健康分口径（对外暴露，供管理侧核对定义式与实现值）。 */
    public static Map<String, Object> caliber() {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("metricCode", "M_PROJECT_HEALTH");
        c.put("formula", "health = 100*(w_ncr*ncr + w_kcr*kcr + w_otr*otr + w_cr*cr + w_cf*cf)");
        c.put("weights", Map.of(
                "ncr", HealthScoreDef.W_NODE_COMPLETION,
                "kcr", HealthScoreDef.W_KEY_COMPLETION,
                "otr", HealthScoreDef.W_ON_TIME,
                "cr", HealthScoreDef.W_FIELD_COMPLETENESS,
                "cf", HealthScoreDef.W_CHANGE_FACTOR));
        c.put("changeFactorCeiling", HealthScoreDef.CHANGE_FACTOR_CEILING);
        c.put("changeCategories", HealthScoreDef.ChangeCategory.values());
        c.put("requiredFields", HealthScoreDef.RequiredField.values());
        c.put("collectionMethod", HealthScoreDef.COLLECTION_METHOD);
        return c;
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
