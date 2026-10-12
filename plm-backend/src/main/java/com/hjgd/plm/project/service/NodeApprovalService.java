package com.hjgd.plm.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.project.entity.ProjectNodeApproval;
import com.hjgd.plm.project.mapper.ProjectNodeApprovalMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 关键节点双级审批（F07 / ISS-001）。
 *
 * <p>规则：7 个关键节点在判定 DONE 前，必须同时具备 L1（研发主管）与 L2（总经理）的
 * APPROVED 记录，且两级审批人不得为同一人（双级 = 两个责任人）。
 *
 * <p>异常路径覆盖（PAND-51 F07 要求的五类）：
 * <ul>
 *   <li>越权：审批人角色不在该级允许角色集内 → 拒绝并留痕（不写记录）；</li>
 *   <li>重复：同级别已存在有效 APPROVED 记录时再次 APPROVED → 拒绝；</li>
 *   <li>驳回：REJECTED 记录使该级回到未通过，节点不得 DONE；</li>
 *   <li>回退/重开：{@link #revokeAll} 撤回两级审批，节点需重新审批；</li>
 *   <li>同人双级：L1 与 L2 审批人相同 → 不构成双级。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeApprovalService {

    private final ProjectNodeApprovalMapper approvalMapper;

    /** 双级审批的级别定义与允许角色集。 */
    public enum Level {
        RD_LEAD(1, "RD_LEAD", "研发主管", Set.of("RD_LEAD", "ADMIN")),
        GM(2, "GM", "总经理", Set.of("GM", "ADMIN"));

        private final int level;
        private final String roleCode;
        private final String label;
        private final Set<String> allowedRoles;

        Level(int level, String roleCode, String label, Set<String> allowedRoles) {
            this.level = level;
            this.roleCode = roleCode;
            this.label = label;
            this.allowedRoles = allowedRoles;
        }

        public int level() { return level; }
        public String roleCode() { return roleCode; }
        public String label() { return label; }
        public Set<String> allowedRoles() { return allowedRoles; }

        public static Level of(int level) {
            for (Level l : values()) {
                if (l.level == level) return l;
            }
            throw new BusinessException("未知审批级别: " + level);
        }
    }

    /** 某节点当前的审批状态快照（每级最新一条）。 */
    public record ApprovalState(String rdLeadDecision, String rdLeadApprover,
                                String gmDecision, String gmApprover) {
        public boolean dualApproved() {
            return ProjectNodeApproval.APPROVED.equals(rdLeadDecision)
                    && ProjectNodeApproval.APPROVED.equals(gmDecision)
                    && rdLeadApprover != null && gmApprover != null
                    && !rdLeadApprover.equals(gmApprover);
        }
    }

    /** 提交一级审批决定。 */
    @Transactional
    public ProjectNodeApproval decide(Long nodeId, Long projectId, String projectNo, String nodeCode,
                                      int level, String decision, String comment) {
        Level lv = Level.of(level);
        if (!ProjectNodeApproval.APPROVED.equals(decision)
                && !ProjectNodeApproval.REJECTED.equals(decision)
                && !ProjectNodeApproval.REVOKED.equals(decision)) {
            throw new BusinessException("非法审批决定: " + decision);
        }

        String role = currentRole();
        if (!lv.allowedRoles().contains(role)) {
            // 越权：拒绝并留痕（日志），不写入审批记录
            log.warn("[审批] 越权拒绝 node={} level={} role={} 允许={}", nodeId, level, role, lv.allowedRoles());
            throw new BusinessException(403, "[" + com.hjgd.plm.common.ApiErrorCodes.APPROVAL_DENIED
                    + "] 越权审批：级别[" + lv.label() + "]仅限 " + lv.allowedRoles() + "，当前角色 " + role);
        }

        ProjectNodeApproval current = latest(nodeId, level);
        if (current != null && ProjectNodeApproval.APPROVED.equals(current.getDecision())
                && ProjectNodeApproval.APPROVED.equals(decision)) {
            throw new BusinessException("重复审批：节点 " + nodeCode + " 的[" + lv.label() + "]已审批通过");
        }

        String approver = currentUser();
        ProjectNodeApproval rec = new ProjectNodeApproval();
        rec.setNodeId(nodeId);
        rec.setProjectId(projectId);
        rec.setProjectNo(projectNo);
        rec.setNodeCode(nodeCode);
        rec.setLevel(level);
        rec.setRoleCode(lv.roleCode());
        rec.setApprover(approver);
        rec.setDecision(decision);
        rec.setComment(comment);
        rec.setDecidedAt(LocalDateTime.now());
        approvalMapper.insert(rec);
        return rec;
    }

    /** 撤回该节点全部审批（回退 / 重开时调用），使节点重新回到「未审批」状态。 */
    @Transactional
    public int revokeAll(Long nodeId, Long projectId, String projectNo, String nodeCode, String comment) {
        String actor = currentUser();
        int n = 0;
        for (Level lv : Level.values()) {
            ProjectNodeApproval current = latest(nodeId, lv.level());
            if (current == null || ProjectNodeApproval.REVOKED.equals(current.getDecision())) continue;
            ProjectNodeApproval rec = new ProjectNodeApproval();
            rec.setNodeId(nodeId);
            rec.setProjectId(projectId);
            rec.setProjectNo(projectNo);
            rec.setNodeCode(nodeCode);
            rec.setLevel(lv.level());
            rec.setRoleCode(lv.roleCode());
            rec.setApprover(actor);
            rec.setDecision(ProjectNodeApproval.REVOKED);
            rec.setComment(comment);
            rec.setDecidedAt(LocalDateTime.now());
            approvalMapper.insert(rec);
            n++;
        }
        return n;
    }

    /** 每级最新一条审批记录（含 REVOKED）。 */
    public Map<Integer, ProjectNodeApproval> latestByLevel(Long nodeId) {
        Map<Integer, ProjectNodeApproval> out = new LinkedHashMap<>();
        for (ProjectNodeApproval a : history(nodeId)) {
            out.putIfAbsent(a.getLevel(), a);   // history 已按时间倒序
        }
        return out;
    }

    public ProjectNodeApproval latest(Long nodeId, int level) {
        return latestByLevel(nodeId).get(level);
    }

    /** 节点审批状态快照。 */
    public ApprovalState state(Long nodeId) {
        Map<Integer, ProjectNodeApproval> latest = latestByLevel(nodeId);
        ProjectNodeApproval l1 = latest.get(Level.RD_LEAD.level());
        ProjectNodeApproval l2 = latest.get(Level.GM.level());
        return new ApprovalState(
                l1 == null ? null : l1.getDecision(), l1 == null ? null : l1.getApprover(),
                l2 == null ? null : l2.getDecision(), l2 == null ? null : l2.getApprover());
    }

    public boolean isDualApproved(Long nodeId) {
        return state(nodeId).dualApproved();
    }

    /**
     * 双审批守卫：节点未经 L1+L2 双级审批通过时抛出 {@link BusinessException}。
     * 供节点 DONE 硬校验调用（唯一入口，防止绕过）。
     */
    public void assertDualApproved(Long nodeId, String nodeCode) {
        ApprovalState s = state(nodeId);
        if (!ProjectNodeApproval.APPROVED.equals(s.rdLeadDecision())) {
            throw new BusinessException("关键节点 " + nodeCode + " 未经研发主管审批，不得判定完成"
                    + (ProjectNodeApproval.REJECTED.equals(s.rdLeadDecision()) ? "（已被驳回）" : ""));
        }
        if (!ProjectNodeApproval.APPROVED.equals(s.gmDecision())) {
            throw new BusinessException("关键节点 " + nodeCode + " 未经总经理审批，不得判定完成"
                    + (ProjectNodeApproval.REJECTED.equals(s.gmDecision()) ? "（已被驳回）" : ""));
        }
        if (s.rdLeadApprover().equals(s.gmApprover())) {
            throw new BusinessException("关键节点 " + nodeCode + " 双级审批人不得为同一人，不构成双级审批");
        }
    }

    /** 审批历史（时间倒序）。 */
    public List<ProjectNodeApproval> history(Long nodeId) {
        LambdaQueryWrapper<ProjectNodeApproval> w = new LambdaQueryWrapper<>();
        w.eq(ProjectNodeApproval::getNodeId, nodeId)
                .orderByDesc(ProjectNodeApproval::getDecidedAt)
                .orderByDesc(ProjectNodeApproval::getId);
        return approvalMapper.selectList(w);
    }

    /** 级别清单（供前端渲染审批条）。 */
    public List<Map<String, Object>> levelCatalog() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Level lv : Level.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("level", lv.level());
            m.put("roleCode", lv.roleCode());
            m.put("label", lv.label());
            m.put("allowedRoles", lv.allowedRoles());
            out.add(m);
        }
        return out;
    }

    private String currentUser() {
        try {
            String name = SecurityUtils.getCurrentRealName();
            return StringUtils.hasText(name) ? name : currentUsername();
        } catch (Exception e) {
            return "SYSTEM";
        }
    }

    private String currentUsername() {
        try {
            String u = SecurityUtils.getCurrentUsername();
            return StringUtils.hasText(u) ? u : "SYSTEM";
        } catch (Exception e) {
            return "SYSTEM";
        }
    }

    private String currentRole() {
        try {
            String r = SecurityUtils.getCurrentRole();
            return StringUtils.hasText(r) ? r : "ANONYMOUS";
        } catch (Exception e) {
            return "ANONYMOUS";
        }
    }
}
