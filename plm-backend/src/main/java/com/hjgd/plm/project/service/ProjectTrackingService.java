package com.hjgd.plm.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.project.entity.Project;
import com.hjgd.plm.project.entity.ProjectNode;
import com.hjgd.plm.project.mapper.ProjectMapper;
import com.hjgd.plm.project.mapper.ProjectNodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 研发项目跟踪总表 (对应 EBMS 研发中心「研发项目跟踪」)。
 * 一行一项目, 列为 22 个进度节点, 单元格值沿用 EBMS 语义: V/X/进行中/待定/日期/文字。
 */
@Service
@RequiredArgsConstructor
public class ProjectTrackingService {

    private final ProjectMapper projectMapper;
    private final ProjectNodeMapper nodeMapper;
    private final ProjectProgressService progressService;
    private final JdbcTemplate jdbcTemplate;

    /** 节点 ↔ 模板工作表 对照表 */
    public List<Map<String, Object>> nodeSheetMap() {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT node_code AS nodeCode, seq, node_name AS nodeName, is_key AS isKey, " +
                            "worksheet_keys AS worksheetKeys, source_tables AS sourceTables, " +
                            "done_rule AS doneRule, summary_col AS summaryCol " +
                            "FROM plm_node_sheet_map ORDER BY seq");
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** 列定义(EBMS 顺序) */
    public List<Map<String, Object>> nodeDefs() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String code : ProjectProgressService.TRACKING_ORDER) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("label", ProjectProgressService.nodeName(code));
            m.put("key", ProjectProgressService.isKey(code));
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> page(int pageNum, int pageSize, String keyword, String status, String nodeStatus) {
        LambdaQueryWrapper<Project> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(Project::getProjectNo, keyword)
                    .or().like(Project::getProjectName, keyword)
                    .or().like(Project::getCustomerName, keyword)
                    .or().like(Project::getOwner, keyword));
        }
        String st = mapStatus(status);
        w.eq(StringUtils.hasText(st), Project::getStatus, st);
        w.orderByDesc(Project::getUpdatedAt);
        List<Project> projects = projectMapper.selectList(w);

        List<Long> ids = projects.stream().map(Project::getId).toList();
        Map<Long, List<ProjectNode>> byProject = new HashMap<>();
        if (!ids.isEmpty()) {
            List<ProjectNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<ProjectNode>()
                    .in(ProjectNode::getProjectId, ids)
                    .orderByAsc(ProjectNode::getSeq));
            for (ProjectNode n : nodes) {
                byProject.computeIfAbsent(n.getProjectId(), k -> new ArrayList<>()).add(n);
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Project p : projects) {
            List<ProjectNode> nodes = byProject.getOrDefault(p.getId(), Collections.emptyList());
            Map<String, ProjectNode> byCode = new HashMap<>();
            for (ProjectNode n : nodes) byCode.put(n.getNodeCode(), n);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("projectNo", p.getProjectNo());
            row.put("projectName", p.getProjectName());
            row.put("customerName", p.getCustomerName());
            row.put("owner", p.getOwner());
            row.put("projectType", p.getProjectType());
            row.put("projectLevel", p.getProjectLevel());
            row.put("currentStage", p.getCurrentGate());
            row.put("investAmount", p.getInvestAmount());
            row.put("orderAmount", p.getOrderAmount());
            row.put("status", p.getStatus());

            Map<String, String> cells = new LinkedHashMap<>();
            int done = 0, keyDone = 0, keyTotal = 0, inProgress = 0, notSet = 0, hasDate = 0;
            for (String code : ProjectProgressService.TRACKING_ORDER) {
                ProjectNode n = byCode.get(code);
                String v = ProjectProgressService.cellValue(n);
                cells.put(code, v);
                if (ProjectProgressService.isKey(code)) {
                    keyTotal++;
                    if ("V".equals(v)) keyDone++;
                }
                if ("V".equals(v)) done++;
                if ("进行中".equals(v)) inProgress++;
                if (v == null || v.isEmpty()) notSet++;
                if (n != null && n.getPlanDate() != null) hasDate++;
            }
            row.put("cells", cells);
            row.put("nodeTotal", ProjectProgressService.TRACKING_ORDER.size());
            row.put("nodeDone", done);
            row.put("keyDone", keyDone);
            row.put("keyTotal", keyTotal);
            row.put("inProgress", inProgress);
            row.put("notSet", notSet);
            row.put("hasDate", hasDate);

            if (matchNodeStatus(nodeStatus, inProgress, hasDate, notSet, done, keyTotal, keyDone)) {
                rows.add(row);
            }
        }

        int total = rows.size();
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(total, from + pageSize);
        List<Map<String, Object>> pageRows = from >= total ? Collections.emptyList() : rows.subList(from, to);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", pageRows);
        out.put("total", total);
        out.put("pageNum", pageNum);
        out.put("pageSize", pageSize);
        return out;
    }

    public ProjectNode setCell(Long projectId, String nodeCode, String value) {
        return progressService.applyCell(projectId, nodeCode, value);
    }

    private boolean matchNodeStatus(String nodeStatus, int inProgress, int hasDate, int notSet,
                                    int done, int keyTotal, int keyDone) {
        if (!StringUtils.hasText(nodeStatus)) return true;
        return switch (nodeStatus) {
            case "in_progress" -> inProgress > 0;
            case "has_date" -> hasDate > 0;
            case "incomplete" -> notSet > 0;
            case "all_done" -> keyTotal > 0 && keyDone == keyTotal;
            default -> true;
        };
    }

    private String mapStatus(String s) {
        if (!StringUtils.hasText(s)) return null;
        return switch (s) {
            case "init", "executing" -> "ACTIVE";
            case "completed" -> "CLOSED";
            case "paused" -> "ON_HOLD";
            case "cancelled" -> "CANCELLED";
            default -> s;
        };
    }
}
