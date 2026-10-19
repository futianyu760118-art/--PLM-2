package com.hjgd.plm.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.event.service.DomainEventService;
import com.hjgd.plm.project.entity.Project;
import com.hjgd.plm.project.entity.ProjectGateLog;
import com.hjgd.plm.project.mapper.ProjectGateLogMapper;
import com.hjgd.plm.project.mapper.ProjectMapper;
import com.hjgd.plm.system.service.SequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectGateLogMapper gateLogMapper;
    private final SequenceService sequenceService;
    private final ProjectProgressService progressService;
    private final DomainEventService domainEventService;

    private static final String[] GATES = {"G0","G1","G2","G3","G4","G5","G6","G7","G8"};
    private static final Map<String,String> GATE_NAMES = Map.of(
            "G0","概念立项","G1","方案评审","G2","结构冻结","G3","开模",
            "G4","T0/T1试模","G5","T2/工试","G6","技转放行","G7","量产","G8","关闭");

    public PageResult<Project> page(int pageNum, int pageSize, String keyword, String status) {
        LambdaQueryWrapper<Project> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(Project::getProjectNo, keyword)
                    .or().like(Project::getProjectName, keyword)
                    .or().like(Project::getPartNo, keyword)
                    .or().like(Project::getCustomerName, keyword));
        }
        w.eq(StringUtils.hasText(status), Project::getStatus, status);
        // 数据可见性：1=全部(默认), 2=仅他人, 3=仅本人
        int ds = com.hjgd.plm.common.DataScope.scope();
        if (ds == com.hjgd.plm.common.DataScope.SCOPE_SELF) {
            w.eq(Project::getOwner, com.hjgd.plm.common.DataScope.name());
        } else if (ds == com.hjgd.plm.common.DataScope.SCOPE_OTHERS) {
            w.ne(Project::getOwner, com.hjgd.plm.common.DataScope.name());
        }
        w.orderByDesc(Project::getUpdatedAt);
        return PageResult.of(projectMapper.selectPage(new Page<>(pageNum, pageSize), w));
    }

    public Project getById(Long id) {
        Project p = projectMapper.selectById(id);
        if (p == null) throw new BusinessException(ResultCode.NOT_FOUND);
        return p;
    }

    public Project getByProjectNo(String projectNo) {
        return projectMapper.selectOne(
                new LambdaQueryWrapper<Project>().eq(Project::getProjectNo, projectNo));
    }

    @Transactional
    public Project create(Project p) {
        if (!StringUtils.hasText(p.getProjectNo())) {
            p.setProjectNo(sequenceService.nextNo("PROJECT_NO"));
        }
        if (!StringUtils.hasText(p.getStatus())) p.setStatus("ACTIVE");
        if (!StringUtils.hasText(p.getCurrentGate())) p.setCurrentGate("G0");
        if (!StringUtils.hasText(p.getGateStatus())) p.setGateStatus("ON_TRACK");
        if (!StringUtils.hasText(p.getRiskLevel())) p.setRiskLevel("green");
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(p);
        // 初始化 19 个进度节点
        progressService.initNodes(p.getId(), p.getProjectNo());
        domainEventService.publish("project.created", "PROJECT", p.getProjectNo(), projectPayload(p));
        return p;
    }

    @Transactional
    public Project update(Project p) {
        Project exist = getById(p.getId());
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(p);
        Project after = getById(p.getId());
        domainEventService.publish("project.updated", "PROJECT", after.getProjectNo(), projectPayload(after));
        return after;
    }

    @Transactional
    public void delete(Long id) {
        Project p = getById(id);
        projectMapper.deleteById(id);
        domainEventService.publish("project.deleted", "PROJECT", p.getProjectNo(),
                Map.of("projectNo", p.getProjectNo(), "projectName", p.getProjectName()));
    }

    public List<ProjectGateLog> getGateLogs(Long projectId) {
        return gateLogMapper.selectList(
                new LambdaQueryWrapper<ProjectGateLog>()
                        .eq(ProjectGateLog::getProjectId, projectId)
                        .orderByAsc(ProjectGateLog::getCreatedAt));
    }

    @Transactional
    public ProjectGateLog passGate(Long projectId, String gateCode, String comment, String evidenceRef) {
        Project p = getById(projectId);
        String current = p.getCurrentGate();
        int nextIdx = indexOf(gateCode) + 1;
        if (nextIdx <= indexOf(current)) {
            throw new BusinessException("门 " + gateCode + " 已通过或低于当前门 " + current);
        }
        p.setCurrentGate(gateCode);
        p.setGateStatus("ON_TRACK");
        if ("G7".equals(gateCode)) p.setStatus("MP");
        if ("G8".equals(gateCode)) { p.setStatus("CLOSED"); p.setCloseDate(LocalDate.now()); }
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(p);

        ProjectGateLog log = new ProjectGateLog();
        log.setProjectId(projectId);
        log.setGateCode(gateCode);
        log.setGateName(GATE_NAMES.getOrDefault(gateCode, gateCode));
        log.setResult("PASS");
        log.setActualDate(LocalDate.now());
        log.setOperator(SecurityUtils.getCurrentRealName());
        log.setComment(comment);
        log.setEvidenceRef(evidenceRef);
        log.setCreatedAt(LocalDateTime.now());
        gateLogMapper.insert(log);
        domainEventService.publish("project.gate_passed", "PROJECT", p.getProjectNo(),
                Map.of("projectNo", p.getProjectNo(), "gateCode", gateCode, "gateName",
                        GATE_NAMES.getOrDefault(gateCode, gateCode), "comment", comment == null ? "" : comment));
        return log;
    }

    @Transactional
    public ProjectGateLog failGate(Long projectId, String gateCode, String comment) {
        ProjectGateLog log = new ProjectGateLog();
        log.setProjectId(projectId);
        log.setGateCode(gateCode);
        log.setGateName(GATE_NAMES.getOrDefault(gateCode, gateCode));
        log.setResult("FAIL");
        log.setOperator(SecurityUtils.getCurrentRealName());
        log.setComment(comment);
        log.setCreatedAt(LocalDateTime.now());
        gateLogMapper.insert(log);

        Project p = getById(projectId);
        p.setGateStatus("BLOCKED");
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(p);
        domainEventService.publish("project.gate_failed", "PROJECT", p.getProjectNo(),
                Map.of("projectNo", p.getProjectNo(), "gateCode", gateCode));
        return log;
    }

    private Map<String, Object> projectPayload(Project p) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("projectNo", p.getProjectNo());
        m.put("projectName", p.getProjectName());
        m.put("partNo", p.getPartNo());
        m.put("customerCode", p.getCustomerCode());
        m.put("customerName", p.getCustomerName());
        m.put("projectType", p.getProjectType());
        m.put("projectLevel", p.getProjectLevel());
        m.put("owner", p.getOwner());
        m.put("department", p.getDepartment());
        m.put("startDate", p.getStartDate() == null ? null : p.getStartDate().toString());
        m.put("targetDate", p.getTargetDate() == null ? null : p.getTargetDate().toString());
        m.put("currentGate", p.getCurrentGate());
        m.put("gateStatus", p.getGateStatus());
        m.put("status", p.getStatus());
        m.put("projectAmount", p.getProjectAmount());
        m.put("orderAmount", p.getOrderAmount());
        m.put("investAmount", p.getInvestAmount());
        m.put("riskLevel", p.getRiskLevel());
        m.put("remarks", p.getRemarks());
        return m;
    }

    private int indexOf(String gateCode) {
        for (int i = 0; i < GATES.length; i++) if (GATES[i].equals(gateCode)) return i;
        return -1;
    }

    public List<String> getGateDefinitions() {
        return List.of(GATES);
    }
}
