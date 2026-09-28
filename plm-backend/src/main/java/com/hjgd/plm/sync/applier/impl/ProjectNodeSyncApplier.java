package com.hjgd.plm.sync.applier.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.project.entity.Project;
import com.hjgd.plm.project.entity.ProjectNode;
import com.hjgd.plm.project.mapper.ProjectMapper;
import com.hjgd.plm.project.mapper.ProjectNodeMapper;
import com.hjgd.plm.project.service.ProjectProgressService;
import com.hjgd.plm.sync.applier.SyncApplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * 进度节点业务应用器: externalKey = "{project_no}:{node_code}"。
 * 将 EBMS 入站的节点变更落到 plm_project_node。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectNodeSyncApplier implements SyncApplier {

    private final ProjectMapper projectMapper;
    private final ProjectNodeMapper nodeMapper;
    private final ProjectProgressService progressService;

    @Override
    public String objectType() {
        return "PROJECT_NODE";
    }

    @Override
    public void apply(String externalKey, String operation, Map<String, Object> payload) {
        if (externalKey == null || !externalKey.contains(":")) {
            log.warn("[sync] PROJECT_NODE externalKey 非法: {}", externalKey);
            return;
        }
        int idx = externalKey.lastIndexOf(':');
        String projectNo = externalKey.substring(0, idx);
        String nodeCode = externalKey.substring(idx + 1);

        Project p = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getProjectNo, projectNo).last("limit 1"));
        if (p == null) {
            log.warn("[sync] 项目不存在: {}", projectNo);
            return;
        }
        // EBMS 原始单元格值(V/X/进行中/待定/日期/文字) 直接同步
        if (payload != null && payload.containsKey("value")) {
            try {
                progressService.applyCell(p.getId(), nodeCode, Objects.toString(payload.get("value"), ""));
            } catch (Exception e) {
                log.warn("[sync] 节点单元格写入失败 {}/{}: {}", projectNo, nodeCode, e.getMessage());
            }
            return;
        }
        ProjectNode node = nodeMapper.selectOne(new LambdaQueryWrapper<ProjectNode>()
                .eq(ProjectNode::getProjectId, p.getId())
                .eq(ProjectNode::getNodeCode, nodeCode));
        if (node == null) {
            log.warn("[sync] 节点不存在: {}/{}", projectNo, nodeCode);
            return;
        }
        if (payload != null) {
            if (payload.get("status") != null) node.setStatus(str(payload.get("status")));
            LocalDate plan = date(payload.get("plan_date"));
            LocalDate actual = date(payload.get("actual_date"));
            if (plan != null) node.setPlanDate(plan);
            if (actual != null) node.setActualDate(actual);
            if (payload.get("owner") != null) node.setOwner(str(payload.get("owner")));
            if (payload.get("delivery_desc") != null) node.setDeliveryDesc(str(payload.get("delivery_desc")));
            if (payload.get("remark") != null) node.setRemark(str(payload.get("remark")));
        }
        node.setSourceSystem("EBMS");
        node.setSyncStatus("SYNCED");
        nodeMapper.updateById(node);
    }

    private String str(Object o) {
        return Objects.toString(o, null);
    }

    private LocalDate date(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
    }
}
