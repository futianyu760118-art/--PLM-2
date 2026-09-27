package com.hjgd.plm.sync.applier.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.project.entity.Project;
import com.hjgd.plm.project.mapper.ProjectMapper;
import com.hjgd.plm.project.service.ProjectProgressService;
import com.hjgd.plm.sync.applier.SyncApplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * 研发项目业务应用器: externalKey = project_no。
 * 与 EBMS 研发中心「研发项目跟踪表」的项目主记录对接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSyncApplier implements SyncApplier {

    private final ProjectMapper projectMapper;
    private final ProjectProgressService progressService;

    @Override
    public String objectType() {
        return "PROJECT";
    }

    @Override
    public void apply(String externalKey, String operation, Map<String, Object> payload) {
        if (externalKey == null) return;
        Project p = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getProjectNo, externalKey).last("limit 1"));
        boolean created = false;
        if (p == null) {
            p = new Project();
            p.setProjectNo(externalKey);
            p.setStatus("ACTIVE");
            p.setCurrentGate("G0");
            p.setGateStatus("ON_TRACK");
            p.setRiskLevel("green");
            p.setCreatedAt(LocalDateTime.now());
            created = true;
        }
        if (payload != null) {
            if (payload.get("project_name") != null) p.setProjectName(str(payload.get("project_name")));
            if (payload.get("customer_name") != null) p.setCustomerName(str(payload.get("customer_name")));
            if (payload.get("owner") != null) p.setOwner(str(payload.get("owner")));
            if (payload.get("department") != null) p.setDepartment(str(payload.get("department")));
            if (payload.get("project_level") != null) p.setProjectLevel(str(payload.get("project_level")));
            if (payload.get("urgency") != null) p.setUrgency(str(payload.get("urgency")));
            if (payload.get("remarks") != null) p.setRemarks(str(payload.get("remarks")));
            LocalDate sd = date(payload.get("start_date"));
            LocalDate td = date(payload.get("target_date"));
            if (sd != null) p.setStartDate(sd);
            if (td != null) p.setTargetDate(td);
            if (payload.get("project_type") != null) p.setProjectType(str(payload.get("project_type")));
            if (payload.get("status") != null) p.setStatus(mapStatus(str(payload.get("status"))));
        }
        p.setSourceSystem("EBMS");
        p.setRevision(p.getRevision() == null ? 1 : p.getRevision() + 1);
        p.setSyncStatus("SYNCED");
        p.setUpdatedAt(LocalDateTime.now());
        if (created) projectMapper.insert(p); else projectMapper.updateById(p);
        // 确保 22 节点存在
        progressService.initNodes(p.getId(), p.getProjectNo());
    }

    private String mapStatus(String s) {
        return switch (s) {
            case "init" -> "ACTIVE";
            case "executing" -> "ACTIVE";
            case "completed" -> "CLOSED";
            case "paused" -> "ON_HOLD";
            case "cancelled" -> "CANCELLED";
            default -> s;
        };
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
