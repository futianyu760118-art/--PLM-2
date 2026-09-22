package com.hjgd.plm.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.project.entity.Project;
import com.hjgd.plm.project.entity.ProjectGateLog;
import com.hjgd.plm.project.entity.ProjectInitiation;
import com.hjgd.plm.project.mapper.ProjectGateLogMapper;
import com.hjgd.plm.project.mapper.ProjectInitiationMapper;
import com.hjgd.plm.system.service.SequenceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 研发项目立项申请书(来源: sales「研发中心-项目管理-立项申请」)。
 * 五阶段审批流: apply(立项发起) → dept(部门审核) → review(研发/财务审核)
 *              → gm(总经理批准) → execute(项目经理执行);
 * 批准后可一键转为研发项目(plm_project)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectInitiationService {

    private final ProjectInitiationMapper initiationMapper;
    private final ProjectGateLogMapper gateLogMapper;
    private final ProjectService projectService;
    private final SequenceService sequenceService;

    /** 五阶段顺序 */
    public static final String[] STAGES = {"apply", "dept", "review", "gm", "execute"};
    private static final Map<String, String> STAGE_NAMES = new LinkedHashMap<>() {{
        put("apply", "立项发起");
        put("dept", "部门审核");
        put("review", "研发/财务审核");
        put("gm", "总经理批准");
        put("execute", "项目经理执行");
        put("rejected", "已驳回");
    }};

    @Data
    public static class AdvanceReq {
        private String fromStage;
        private String reviewer;
        private String opinion;
        private String result;           // 通过 / reject
        private String rdReviewer;
        private String rdOpinion;
        private String financeReviewer;
        private String financeOpinion;
    }

    @Data
    public static class ApproveReq {
        private String approver;
        private String approvalDate;
        private String approvalOpinion;
    }

    public PageResult<ProjectInitiation> page(int pageNum, int pageSize, String status, String stage, String keyword) {
        LambdaQueryWrapper<ProjectInitiation> w = new LambdaQueryWrapper<>();
        w.eq(StringUtils.hasText(status), ProjectInitiation::getApprovalStatus, status);
        w.eq(StringUtils.hasText(stage), ProjectInitiation::getWorkflowStage, stage);
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(ProjectInitiation::getProjectName, keyword)
                    .or().like(ProjectInitiation::getProjectNo, keyword)
                    .or().like(ProjectInitiation::getInitNo, keyword)
                    .or().like(ProjectInitiation::getCustomerNo, keyword)
                    .or().like(ProjectInitiation::getOwner, keyword));
        }
        w.orderByDesc(ProjectInitiation::getId);
        return PageResult.of(initiationMapper.selectPage(new Page<>(pageNum, pageSize), w));
    }

    public ProjectInitiation getById(Long id) {
        ProjectInitiation r = initiationMapper.selectById(id);
        if (r == null) throw new BusinessException("立项申请书不存在");
        return r;
    }

    public ProjectInitiation getByProjectId(Long projectId) {
        return initiationMapper.selectOne(new LambdaQueryWrapper<ProjectInitiation>()
                .eq(ProjectInitiation::getProjectId, projectId).last("LIMIT 1"));
    }

    @Transactional
    public ProjectInitiation create(ProjectInitiation r) {
        if (!StringUtils.hasText(r.getProjectName())) throw new BusinessException("项目名称必填");
        String today = LocalDate.now().toString();
        if (!StringUtils.hasText(r.getInitNo())) r.setInitNo(sequenceService.nextNo("INITIATION_NO"));
        if (!StringUtils.hasText(r.getApprovalStatus())) r.setApprovalStatus("draft");
        if (!StringUtils.hasText(r.getWorkflowStage())) r.setWorkflowStage("apply");
        if (!StringUtils.hasText(r.getApplicant())) {
            try { r.setApplicant(SecurityUtils.getCurrentRealName()); } catch (Exception ignored) {}
        }
        if (!StringUtils.hasText(r.getApplyDate())) r.setApplyDate(today);
        if (!StringUtils.hasText(r.getStep1Applicant())) r.setStep1Applicant(r.getApplicant());
        if (!StringUtils.hasText(r.getStep1ApplyDate())) r.setStep1ApplyDate(today);
        if (!StringUtils.hasText(r.getDepartment())) r.setDepartment("研发中心");
        if (r.getBudgetTotal() == null) r.setBudgetTotal(java.math.BigDecimal.ZERO);
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        initiationMapper.insert(r);
        return r;
    }

    @Transactional
    public ProjectInitiation update(ProjectInitiation r) {
        ProjectInitiation exist = getById(r.getId());
        if ("approved".equals(exist.getApprovalStatus())) {
            throw new BusinessException("已批准的立项申请书不可修改");
        }
        r.setInitNo(exist.getInitNo());
        r.setUpdatedAt(LocalDateTime.now());
        initiationMapper.updateById(r);
        return getById(r.getId());
    }

    @Transactional
    public void delete(Long id) {
        ProjectInitiation exist = getById(id);
        if ("approved".equals(exist.getApprovalStatus())) {
            throw new BusinessException("已批准的立项申请书不可删除");
        }
        initiationMapper.deleteById(id);
    }

    /** 推进到下一审批阶段(含阶段权限校验)。 */
    @Transactional
    public ProjectInitiation advance(Long id, AdvanceReq req) {
        ProjectInitiation rec = getById(id);
        String cur = StringUtils.hasText(req.getFromStage()) ? req.getFromStage() : rec.getWorkflowStage();
        if ("rejected".equals(cur) || "rejected".equals(rec.getWorkflowStage())) {
            throw new BusinessException("已驳回的申请书不可推进, 请修改后重新提交");
        }
        int idx = indexOf(cur);
        if (idx < 0) throw new BusinessException("无效的当前阶段: " + cur);
        if (idx >= STAGES.length - 1) throw new BusinessException("已是最终阶段(" + STAGE_NAMES.get(cur) + ")");
        requireStagePermission(cur);

        String today = LocalDate.now().toString();
        String next = STAGES[idx + 1];
        String operator = StringUtils.hasText(req.getReviewer())
                ? req.getReviewer() : safeRealName();

        if ("apply".equals(cur)) {
            rec.setStep1Applicant(StringUtils.hasText(req.getReviewer()) ? req.getReviewer() : rec.getStep1Applicant());
            rec.setStep1ApplyDate(today);
            rec.setApprovalStatus("submitted");
        } else if ("dept".equals(cur)) {
            rec.setStep2Approver(operator);
            rec.setStep2Date(today);
            rec.setStep2Opinion(req.getOpinion());
            rec.setStep2Result(StringUtils.hasText(req.getResult()) ? req.getResult() : "通过");
        } else if ("review".equals(cur)) {
            if (StringUtils.hasText(req.getRdReviewer())) rec.setStep3RdReviewer(req.getRdReviewer());
            if (StringUtils.hasText(req.getRdOpinion())) rec.setStep3RdOpinion(req.getRdOpinion());
            if (StringUtils.hasText(req.getFinanceReviewer())) rec.setStep3FinanceReviewer(req.getFinanceReviewer());
            if (StringUtils.hasText(req.getFinanceOpinion())) rec.setStep3FinanceOpinion(req.getFinanceOpinion());
            rec.setStep3RdDate(today);
            rec.setStep3FinanceDate(today);
        } else if ("gm".equals(cur)) {
            rec.setStep4Approver(operator);
            rec.setStep4Date(today);
            rec.setStep4Opinion(req.getOpinion());
            if ("reject".equals(req.getResult())) {
                rec.setWorkflowStage("rejected");
                rec.setApprovalStatus("rejected");
                rec.setApprovalOpinion(req.getOpinion());
                rec.setUpdatedAt(LocalDateTime.now());
                initiationMapper.updateById(rec);
                log.info("[立项审批] {} 被驳回: {}", rec.getInitNo(), req.getOpinion());
                return getById(id);
            }
            rec.setApprovalStatus("approved");
            rec.setApprover(operator);
            rec.setApprovalDate(today);
            rec.setApprovalOpinion(req.getOpinion());
            rec.setStep5Owner(rec.getOwner());
            rec.setStep5StartDate(rec.getStartDate());
        }
        rec.setWorkflowStage(next);
        rec.setUpdatedAt(LocalDateTime.now());
        initiationMapper.updateById(rec);
        log.info("[立项审批] {} 推进: {} → {}", rec.getInitNo(), cur, next);
        return getById(id);
    }

    /** 驳回立项申请。 */
    @Transactional
    public ProjectInitiation reject(Long id, String opinion) {
        ProjectInitiation rec = getById(id);
        if ("approved".equals(rec.getApprovalStatus())) throw new BusinessException("已批准, 不可驳回");
        requireStagePermission(rec.getWorkflowStage());
        rec.setWorkflowStage("rejected");
        rec.setApprovalStatus("rejected");
        rec.setApprovalOpinion(opinion);
        rec.setUpdatedAt(LocalDateTime.now());
        initiationMapper.updateById(rec);
        return getById(id);
    }

    /** 立项批准 → 自动创建研发项目(plm_project)并建立 G0 阶段门记录。 */
    @Transactional
    public Map<String, Object> approveToProject(Long id, ApproveReq req) {
        ProjectInitiation rec = getById(id);
        requireExecutePermission();
        String stage = rec.getWorkflowStage();
        boolean canCreate = "approved".equals(rec.getApprovalStatus())
                || "gm".equals(stage) || "execute".equals(stage);
        if (!canCreate) throw new BusinessException("当前阶段(" + STAGE_NAMES.getOrDefault(stage, stage) + ")不可转为项目");

        String today = LocalDate.now().toString();
        rec.setApprovalStatus("approved");
        if (req != null && StringUtils.hasText(req.getApprover())) rec.setApprover(req.getApprover());
        else if (!StringUtils.hasText(rec.getApprover())) rec.setApprover(safeRealName());
        rec.setApprovalDate(req != null && StringUtils.hasText(req.getApprovalDate()) ? req.getApprovalDate() : today);
        if (req != null && StringUtils.hasText(req.getApprovalOpinion())) rec.setApprovalOpinion(req.getApprovalOpinion());
        rec.setWorkflowStage("execute");
        rec.setUpdatedAt(LocalDateTime.now());
        initiationMapper.updateById(rec);

        if (rec.getProjectId() != null) {
            Map<String, Object> already = new LinkedHashMap<>();
            already.put("message", "已批准, 项目已存在");
            already.put("projectId", rec.getProjectId());
            already.put("already", true);
            return already;
        }

        Project p = new Project();
        p.setProjectName(rec.getProjectName());
        p.setPartNo(rec.getProjectNo());
        p.setCustomerCode(rec.getCustomerNo());
        p.setCustomerName(StringUtils.hasText(rec.getCustomerNo()) ? rec.getCustomerNo() : rec.getCustomerType());
        String type = rec.getProjectType() == null ? "" : rec.getProjectType();
        p.setProjectType(type.contains("自研") && !type.contains("客户定制") ? "SELF" : "NEW");
        p.setProjectLevel(StringUtils.hasText(rec.getCustomerLevel()) ? truncate(rec.getCustomerLevel(), 8) : "C");
        p.setOwner(StringUtils.hasText(rec.getOwner()) ? rec.getOwner() : rec.getApplicant());
        p.setDepartment(StringUtils.hasText(rec.getDepartment()) ? rec.getDepartment() : "研发中心");
        p.setStartDate(parseDate(rec.getStartDate()));
        p.setProjectAmount(rec.getBudgetTotal());
        p.setInvestAmount(rec.getBudgetTotal());
        p.setStatus("ACTIVE");
        p.setCurrentGate("G0");
        p.setGateStatus("ON_TRACK");
        p.setRiskLevel("green");
        p.setRemarks("由立项申请书 " + rec.getInitNo() + " 批准转入");
        Project created = projectService.create(p);

        rec.setProjectId(created.getId());
        rec.setUpdatedAt(LocalDateTime.now());
        initiationMapper.updateById(rec);

        ProjectGateLog log = new ProjectGateLog();
        log.setProjectId(created.getId());
        log.setGateCode("G0");
        log.setGateName("概念立项");
        log.setResult("PASS");
        log.setActualDate(LocalDate.now());
        log.setOperator(safeRealName());
        log.setComment("立项申请书 " + rec.getInitNo() + " 批准转入");
        log.setCreatedAt(LocalDateTime.now());
        gateLogMapper.insert(log);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "已批准并创建研发项目");
        out.put("projectId", created.getId());
        out.put("project", created);
        return out;
    }

    public Map<String, Object> stats() {
        Long total = initiationMapper.selectCount(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : new String[]{"draft", "submitted", "approved", "rejected"}) {
            byStatus.put(s, initiationMapper.selectCount(
                    new LambdaQueryWrapper<ProjectInitiation>().eq(ProjectInitiation::getApprovalStatus, s)));
        }
        Map<String, Long> byStage = new LinkedHashMap<>();
        for (String s : STAGES) {
            byStage.put(s, initiationMapper.selectCount(
                    new LambdaQueryWrapper<ProjectInitiation>().eq(ProjectInitiation::getWorkflowStage, s)));
        }
        out.put("byStatus", byStatus);
        out.put("byStage", byStage);
        out.put("stageNames", STAGE_NAMES);
        return out;
    }

    public Map<String, String> stageNames() {
        return STAGE_NAMES;
    }

    // ---------- 内部 ----------

    private void requireStagePermission(String stage) {
        if (SecurityUtils.hasRole("ADMIN")) return;
        boolean engineer = SecurityUtils.hasRole("ENGINEER");
        switch (stage) {
            case "apply" -> { /* 任何登录用户可提交自己的立项 */ }
            case "dept", "review", "execute" -> {
                if (!engineer) throw new BusinessException("无权限执行[" + STAGE_NAMES.get(stage) + "]阶段操作");
            }
            case "gm" -> throw new BusinessException("仅系统管理员可执行[总经理批准]");
            default -> { }
        }
    }

    private void requireExecutePermission() {
        if (!SecurityUtils.hasRole("ADMIN") && !SecurityUtils.hasRole("ENGINEER")) {
            throw new BusinessException("无权限执行项目转入");
        }
    }

    private int indexOf(String stage) {
        for (int i = 0; i < STAGES.length; i++) if (STAGES[i].equals(stage)) return i;
        return -1;
    }

    private LocalDate parseDate(String s) {
        if (!StringUtils.hasText(s)) return null;
        try { return LocalDate.parse(s.substring(0, 10)); } catch (Exception e) { return null; }
    }

    private String truncate(String s, int len) {
        return s == null ? null : (s.length() <= len ? s : s.substring(0, len));
    }

    private String safeRealName() {
        try { return SecurityUtils.getCurrentRealName(); } catch (Exception e) { return ""; }
    }
}
