package com.hjgd.plm.m04.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.m04.approval.dto.ApprovalFlowDTO;
import com.hjgd.plm.m04.approval.dto.ApprovalNodeDTO;
import com.hjgd.plm.m04.approval.dto.ApprovalProgressVO;
import com.hjgd.plm.m04.approval.dto.PendingTaskVO;
import com.hjgd.plm.m04.approval.entity.ApprovalFlow;
import com.hjgd.plm.m04.approval.entity.ApprovalInstance;
import com.hjgd.plm.m04.approval.entity.ApprovalNode;
import com.hjgd.plm.m04.approval.entity.ApprovalTask;
import com.hjgd.plm.m04.approval.enums.ApprovalAction;
import com.hjgd.plm.m04.approval.enums.ApprovalStatus;
import com.hjgd.plm.m04.approval.mapper.ApprovalFlowMapper;
import com.hjgd.plm.m04.approval.mapper.ApprovalInstanceMapper;
import com.hjgd.plm.m04.approval.mapper.ApprovalNodeMapper;
import com.hjgd.plm.m04.approval.mapper.ApprovalTaskMapper;
import com.hjgd.plm.m04.approval.service.ApprovalService;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalServiceImpl implements ApprovalService {

    private static final Pattern COND = Pattern.compile(
            "^(\\w+)\\s*(!=|=)\\s*'?([^']*)'?$");
    private static final String DEFAULT_APPROVER_ROLE = "ADMIN";

    private final ApprovalFlowMapper flowMapper;
    private final ApprovalNodeMapper nodeMapper;
    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalTaskMapper taskMapper;
    private final JdbcTemplate jdbcTemplate;

    // ================= 流程定义 =================

    @Override
    public List<ApprovalFlow> listFlows(String objectType) {
        return flowMapper.selectList(new LambdaQueryWrapper<ApprovalFlow>()
                .eq(StringUtils.hasText(objectType), ApprovalFlow::getObjectType, objectType)
                .orderByAsc(ApprovalFlow::getObjectType)
                .orderByAsc(ApprovalFlow::getFlowCode));
    }

    @Override
    public List<ApprovalNode> listNodes(Long flowId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<ApprovalNode>()
                .eq(ApprovalNode::getFlowId, flowId)
                .orderByAsc(ApprovalNode::getSeq));
    }

    @Override
    @Transactional
    public ApprovalFlow saveFlow(ApprovalFlowDTO dto) {
        if (dto.getNodes() == null || dto.getNodes().isEmpty()) {
            throw new BusinessException("审批流至少需要一个节点");
        }
        ApprovalFlow flow;
        if (dto.getId() != null) {
            flow = flowMapper.selectById(dto.getId());
            if (flow == null) {
                throw new BusinessException(ResultCode.NOT_FOUND);
            }
            if (hasRunningInstance(flow.getId())) {
                throw new BusinessException("该流程还有在途实例，禁止改节点；请先停用并新建版本");
            }
        } else {
            ApprovalFlow dup = flowMapper.selectOne(new LambdaQueryWrapper<ApprovalFlow>()
                    .eq(ApprovalFlow::getFlowCode, dto.getFlowCode()));
            if (dup != null) {
                throw new BusinessException("流程编码已存在: " + dto.getFlowCode());
            }
            flow = new ApprovalFlow();
            flow.setCreatedAt(LocalDateTime.now());
        }
        flow.setFlowCode(dto.getFlowCode());
        flow.setObjectType(dto.getObjectType());
        flow.setName(dto.getName());
        flow.setVersionNo(StringUtils.hasText(dto.getVersionNo()) ? dto.getVersionNo() : "V1");
        flow.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "DRAFT");
        flow.setRemark(dto.getRemark());
        flow.setUpdatedAt(LocalDateTime.now());

        if (flow.getId() == null) {
            flowMapper.insert(flow);
        } else {
            flowMapper.updateById(flow);
            nodeMapper.delete(new LambdaQueryWrapper<ApprovalNode>()
                    .eq(ApprovalNode::getFlowId, flow.getId()));
        }

        int seq = 0;
        for (ApprovalNodeDTO n : dto.getNodes()) {
            seq++;
            ApprovalNode node = new ApprovalNode();
            node.setFlowId(flow.getId());
            node.setSeq(seq);
            node.setNodeCode(StringUtils.hasText(n.getNodeCode()) ? n.getNodeCode() : "N" + seq);
            node.setNodeName(StringUtils.hasText(n.getNodeName()) ? n.getNodeName() : "审批节点" + seq);
            node.setApproverType(StringUtils.hasText(n.getApproverType()) ? n.getApproverType() : "ROLE");
            node.setApproverValue(n.getApproverValue());
            node.setConditionExpr(n.getConditionExpr());
            node.setParallelGroup(n.getParallelGroup());
            node.setTimeoutHours(n.getTimeoutHours());
            node.setOnReject(StringUtils.hasText(n.getOnReject()) ? n.getOnReject() : "BACK");
            node.setOnTimeout(StringUtils.hasText(n.getOnTimeout()) ? n.getOnTimeout() : "REMIND");
            node.setRemark(n.getRemark());
            nodeMapper.insert(node);
        }
        // 启用新流程时，同类型其它流程自动降级为 DISABLED，避免引擎选错
        if ("ENABLED".equals(flow.getStatus())) {
            demoteOthers(flow.getObjectType(), flow.getId());
        }
        return flow;
    }

    @Override
    @Transactional
    public void changeFlowStatus(Long flowId, String status) {
        ApprovalFlow flow = flowMapper.selectById(flowId);
        if (flow == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        flow.setStatus(status);
        flow.setUpdatedAt(LocalDateTime.now());
        flowMapper.updateById(flow);
        if ("ENABLED".equals(status)) {
            demoteOthers(flow.getObjectType(), flowId);
        }
    }

    @Override
    @Transactional
    public void deleteFlow(Long flowId) {
        Long n = instanceMapper.selectCount(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getFlowId, flowId));
        if (n != null && n > 0) {
            throw new BusinessException("该流程已有审批实例，禁止删除；可改为 DISABLED");
        }
        nodeMapper.delete(new LambdaQueryWrapper<ApprovalNode>()
                .eq(ApprovalNode::getFlowId, flowId));
        flowMapper.deleteById(flowId);
    }

    private void demoteOthers(String objectType, Long keepId) {
        List<ApprovalFlow> others = flowMapper.selectList(new LambdaQueryWrapper<ApprovalFlow>()
                .eq(ApprovalFlow::getObjectType, objectType)
                .ne(ApprovalFlow::getId, keepId)
                .eq(ApprovalFlow::getStatus, "ENABLED"));
        for (ApprovalFlow f : others) {
            f.setStatus("DISABLED");
            f.setUpdatedAt(LocalDateTime.now());
            flowMapper.updateById(f);
        }
    }

    private boolean hasRunningInstance(Long flowId) {
        Long n = instanceMapper.selectCount(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getFlowId, flowId)
                .eq(ApprovalInstance::getStatus, ApprovalStatus.RUNNING.name()));
        return n != null && n > 0;
    }

    // ================= 引擎 =================

    @Override
    @Transactional
    public ApprovalInstance start(String objectType, String objectNo, String flowCode, Map<String, Object> context) {
        ApprovalFlow flow = resolveFlow(objectType, flowCode);
        if (flow == null) {
            log.warn("审批流未配置，{} {} 走旧逻辑兜底", objectType, objectNo);
            return null;
        }
        List<ApprovalNode> nodes = effectiveNodes(flow.getId(), context);
        if (nodes.isEmpty()) {
            throw new BusinessException("审批流 " + flow.getFlowCode() + " 没有可用节点");
        }
        String ctxJson = toJson(context);

        ApprovalInstance inst = instanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getObjectType, objectType)
                .eq(ApprovalInstance::getObjectNo, objectNo)
                .eq(ApprovalInstance::getFlowId, flow.getId()));

        if (inst == null) {
            inst = new ApprovalInstance();
            inst.setFlowId(flow.getId());
            inst.setObjectType(objectType);
            inst.setObjectNo(objectNo);
            inst.setCurrentSeq(nodes.get(0).getSeq());
            inst.setStatus(ApprovalStatus.RUNNING.name());
            inst.setStartedBy(SecurityUtils.getCurrentRealName());
            inst.setStartedAt(LocalDateTime.now());
            inst.setContextJson(ctxJson);
            instanceMapper.insert(inst);
        } else if (ApprovalStatus.RUNNING.name().equals(inst.getStatus())) {
            // 幂等：重复提交不重建实例
            return inst;
        } else {
            // 驳回/撤回后重新提交：复用实例，复位进度
            closePending(inst.getId(), "CANCELLED", "重新发起，原待办作废");
            inst.setCurrentSeq(nodes.get(0).getSeq());
            inst.setStatus(ApprovalStatus.RUNNING.name());
            inst.setStartedBy(SecurityUtils.getCurrentRealName());
            inst.setStartedAt(LocalDateTime.now());
            inst.setFinishedAt(null);
            inst.setContextJson(ctxJson);
            instanceMapper.updateById(inst);
        }
        createTasks(inst, nodes.get(0), context);
        log.info("审批发起 {} {} flow={} seq={}", objectType, objectNo, flow.getFlowCode(), inst.getCurrentSeq());
        return inst;
    }

    @Override
    @Transactional
    public ApprovalInstance act(Long instanceId, ApprovalAction action, String comment) {
        ApprovalInstance inst = instanceById(instanceId);
        if (!ApprovalStatus.RUNNING.name().equals(inst.getStatus())) {
            throw new BusinessException("该审批已结束（" + inst.getStatus() + "），不能再处理");
        }
        List<ApprovalNode> nodes = effectiveNodes(inst.getFlowId(), parseCtx(inst.getContextJson()));
        ApprovalNode current = nodeBySeq(nodes, inst.getCurrentSeq());
        if (current == null) {
            throw new BusinessException("审批节点配置缺失: seq=" + inst.getCurrentSeq());
        }

        List<ApprovalTask> pending = taskMapper.selectList(new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getInstanceId, inst.getId())
                .eq(ApprovalTask::getNodeSeq, inst.getCurrentSeq())
                .isNull(ApprovalTask::getAction));
        ApprovalTask mine = matchTask(pending);
        if (mine == null) {
            if (!SecurityUtils.hasRole("ADMIN")) {
                throw new BusinessException("当前节点审批人不是你（节点：" + current.getNodeName() + "）");
            }
            // ADMIN 兜底：没有待办行时补一条，保证动作可留痕
            mine = new ApprovalTask();
            mine.setInstanceId(inst.getId());
            mine.setNodeSeq(inst.getCurrentSeq());
            mine.setApprover(SecurityUtils.getCurrentRealName());
            mine.setCreatedAt(LocalDateTime.now());
            taskMapper.insert(mine);
        }
        mine.setAction(action.name());
        mine.setComment(comment);
        mine.setActedAt(LocalDateTime.now());
        taskMapper.updateById(mine);

        if (action == ApprovalAction.APPROVE) {
            boolean stillWaiting = taskMapper.selectCount(new LambdaQueryWrapper<ApprovalTask>()
                    .eq(ApprovalTask::getInstanceId, inst.getId())
                    .eq(ApprovalTask::getNodeSeq, inst.getCurrentSeq())
                    .isNull(ApprovalTask::getAction)) > 0;
            if (stillWaiting) {
                log.info("会签未齐，instance={} seq={} 继续等待", inst.getId(), inst.getCurrentSeq());
                return inst;
            }
            advance(inst, nodes);
        } else {
            String onReject = action == ApprovalAction.BACK ? "BACK"
                    : action == ApprovalAction.TERMINATE ? "TERMINATE"
                    : (StringUtils.hasText(current.getOnReject()) ? current.getOnReject() : "BACK");
            if ("BACK".equals(onReject)) {
                ApprovalNode prev = prevNode(nodes, inst.getCurrentSeq());
                if (prev == null) {
                    finish(inst, ApprovalStatus.REJECTED);
                } else {
                    closePending(inst.getId(), "CANCELLED", "已退回，原待办作废");
                    inst.setCurrentSeq(prev.getSeq());
                    instanceMapper.updateById(inst);
                    createTasks(inst, prev, parseCtx(inst.getContextJson()));
                    log.info("审批退回 instance={} {} -> {} ({})", inst.getId(), current.getSeq(), prev.getSeq(), comment);
                }
            } else {
                finish(inst, ApprovalStatus.REJECTED);
            }
        }
        return inst;
    }

    @Override
    @Transactional
    public ApprovalInstance cancel(Long instanceId, String comment) {
        ApprovalInstance inst = instanceById(instanceId);
        if (!ApprovalStatus.RUNNING.name().equals(inst.getStatus())) {
            return inst;
        }
        closePending(inst.getId(), "CANCELLED", comment);
        finish(inst, ApprovalStatus.CANCELLED);
        return inst;
    }

    @Override
    @Transactional
    public void cancelByObject(String objectType, String objectNo, String comment) {
        ApprovalInstance inst = instanceOf(objectType, objectNo);
        if (inst != null && ApprovalStatus.RUNNING.name().equals(inst.getStatus())) {
            closePending(inst.getId(), "CANCELLED", comment);
            finish(inst, ApprovalStatus.CANCELLED);
        }
    }

    @Override
    public ApprovalInstance instanceOf(String objectType, String objectNo) {
        List<ApprovalInstance> list = instanceMapper.selectList(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getObjectType, objectType)
                .eq(ApprovalInstance::getObjectNo, objectNo)
                .orderByDesc(ApprovalInstance::getId));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public ApprovalInstance instanceById(Long instanceId) {
        ApprovalInstance inst = instanceMapper.selectById(instanceId);
        if (inst == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return inst;
    }

    @Override
    public List<PendingTaskVO> pendingOfCurrentUser() {
        String username = SecurityUtils.getCurrentUsername();
        String realName = SecurityUtils.getCurrentRealName();
        String sql = "SELECT t.id task_id, t.instance_id, i.object_type, i.object_no, i.started_at, "
                + "t.node_seq, t.created_at, f.name flow_name, n.node_name "
                + "FROM plm_approval_task t "
                + "JOIN plm_approval_instance i ON i.id = t.instance_id "
                + "JOIN plm_approval_flow f ON f.id = i.flow_id "
                + "LEFT JOIN plm_approval_node n ON n.flow_id = i.flow_id AND n.seq = t.node_seq "
                + "WHERE t.action IS NULL AND i.status = 'RUNNING' "
                + "AND (t.approver = ? OR t.approver = ?) "
                + "ORDER BY t.created_at ASC";

        return jdbcTemplate.query(sql, (rs, i) -> {
            PendingTaskVO vo = new PendingTaskVO();
            vo.setTaskId(rs.getLong("task_id"));
            vo.setInstanceId(rs.getLong("instance_id"));
            vo.setObjectType(rs.getString("object_type"));
            vo.setObjectNo(rs.getString("object_no"));
            vo.setNodeSeq(rs.getInt("node_seq"));
            vo.setNodeName(rs.getString("node_name"));
            vo.setFlowName(rs.getString("flow_name"));
            vo.setApprover(realName);
            java.sql.Timestamp created = rs.getTimestamp("created_at");
            if (created != null) {
                LocalDateTime c = created.toLocalDateTime();
                vo.setCreatedAt(c);
                vo.setWaitingHours(Duration.between(c, LocalDateTime.now()).toHours());
            }
            return vo;
        }, username, realName);
    }

    @Override
    public List<ApprovalTask> history(Long instanceId) {
        return taskMapper.selectList(new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getInstanceId, instanceId)
                .orderByAsc(ApprovalTask::getNodeSeq)
                .orderByAsc(ApprovalTask::getId));
    }

    @Override
    public ApprovalProgressVO progress(String objectType, String objectNo) {
        ApprovalProgressVO vo = new ApprovalProgressVO();
        ApprovalInstance inst = instanceOf(objectType, objectNo);
        if (inst == null) {
            return vo;
        }
        vo.setInstanceId(inst.getId());
        vo.setObjectType(inst.getObjectType());
        vo.setObjectNo(inst.getObjectNo());
        vo.setStatus(inst.getStatus());
        vo.setCurrentSeq(inst.getCurrentSeq());
        vo.setStartedBy(inst.getStartedBy());
        vo.setStartedAt(inst.getStartedAt());
        vo.setFinishedAt(inst.getFinishedAt());
        vo.setNodes(listNodes(inst.getFlowId()));
        vo.setTasks(history(inst.getId()));
        return vo;
    }

    // ================= 超时策略 =================

    @Override
    @Transactional
    public int handleTimeouts(LocalDateTime now) {
        List<ApprovalInstance> running = instanceMapper.selectList(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getStatus, ApprovalStatus.RUNNING.name()));
        if (running.isEmpty()) {
            return 0;
        }
        int handled = 0;
        for (ApprovalInstance inst : running) {
            List<ApprovalNode> nodes = effectiveNodes(inst.getFlowId(), parseCtx(inst.getContextJson()));
            ApprovalNode current = nodeBySeq(nodes, inst.getCurrentSeq());
            if (current == null || current.getTimeoutHours() == null || current.getTimeoutHours() <= 0) {
                continue;
            }
            List<ApprovalTask> pending = taskMapper.selectList(new LambdaQueryWrapper<ApprovalTask>()
                    .eq(ApprovalTask::getInstanceId, inst.getId())
                    .eq(ApprovalTask::getNodeSeq, inst.getCurrentSeq())
                    .isNull(ApprovalTask::getAction));
            if (pending.isEmpty() || pending.get(0).getCreatedAt() == null) {
                continue;
            }
            // 以最早一条待办的到达时间起算，避免退回后重算把已拖的时间抹掉
            LocalDateTime arrivedAt = pending.get(0).getCreatedAt();
            for (ApprovalTask t : pending) {
                if (t.getCreatedAt() != null && t.getCreatedAt().isBefore(arrivedAt)) {
                    arrivedAt = t.getCreatedAt();
                }
            }
            if (arrivedAt.plusHours(current.getTimeoutHours()).isAfter(now)) {
                continue;
            }
            String strategy = StringUtils.hasText(current.getOnTimeout())
                    ? current.getOnTimeout().toUpperCase() : "REMIND";
            switch (strategy) {
                case "AUTO_PASS" -> {
                    for (ApprovalTask t : pending) {
                        t.setAction(ApprovalAction.APPROVE.name());
                        t.setComment("[超时自动通过] 节点限时 " + current.getTimeoutHours() + " 小时");
                        t.setActedAt(now);
                        taskMapper.updateById(t);
                    }
                    advance(inst, nodes);
                    log.info("[审批超时] instance={} 节点[{}] 超时自动通过", inst.getId(), current.getNodeName());
                }
                case "ESCALATE" -> {
                    List<String> admins = jdbcTemplate.queryForList(
                            "SELECT u.username FROM sys_user u "
                                    + "JOIN sys_user_role ur ON ur.user_id = u.id "
                                    + "JOIN sys_role r ON r.id = ur.role_id "
                                    + "WHERE r.role_code = ? AND u.status = 1 AND u.deleted = 0",
                            String.class, DEFAULT_APPROVER_ROLE);
                    if (admins.isEmpty()) {
                        log.warn("[审批超时] instance={} 需升级但角色 {} 下无启用用户",
                                inst.getId(), DEFAULT_APPROVER_ROLE);
                        continue;
                    }
                    int reassigned = 0;
                    for (ApprovalTask t : pending) {
                        if (admins.contains(t.getApprover())) {
                            continue;
                        }
                        t.setApprover(admins.get(0));
                        taskMapper.updateById(t);
                        reassigned++;
                    }
                    if (reassigned == 0) {
                        continue;
                    }
                    log.warn("[审批超时] instance={} 节点[{}] 超期 {} 小时，{} 条待办升级给 {}",
                            inst.getId(), current.getNodeName(), current.getTimeoutHours(),
                            reassigned, admins.get(0));
                }
                default -> log.warn("[审批超时] instance={} 节点[{}] 已超期 {} 小时（策略 REMIND，仅提醒）",
                        inst.getId(), current.getNodeName(), current.getTimeoutHours());
            }
            handled++;
        }
        return handled;
    }

    // ================= 内部 =================

    private ApprovalFlow resolveFlow(String objectType, String flowCode) {
        if (StringUtils.hasText(flowCode)) {
            ApprovalFlow flow = flowMapper.selectOne(new LambdaQueryWrapper<ApprovalFlow>()
                    .eq(ApprovalFlow::getFlowCode, flowCode));
            if (flow != null && "ENABLED".equals(flow.getStatus())) {
                return flow;
            }
            log.warn("流程 {} 不存在或未启用", flowCode);
            return null;
        }
        List<ApprovalFlow> list = flowMapper.selectList(new LambdaQueryWrapper<ApprovalFlow>()
                .eq(ApprovalFlow::getObjectType, objectType)
                .eq(ApprovalFlow::getStatus, "ENABLED")
                .orderByAsc(ApprovalFlow::getId));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 取按条件过滤后的节点；全部被条件过滤掉时回落到全量节点，避免流程卡死 */
    private List<ApprovalNode> effectiveNodes(Long flowId, Map<String, Object> ctx) {
        List<ApprovalNode> all = listNodes(flowId);
        if (all.isEmpty()) {
            return all;
        }
        List<ApprovalNode> hit = new ArrayList<>();
        for (ApprovalNode n : all) {
            if (evalCondition(n.getConditionExpr(), ctx)) {
                hit.add(n);
            }
        }
        return hit.isEmpty() ? all : hit;
    }

    private boolean evalCondition(String expr, Map<String, Object> ctx) {
        if (!StringUtils.hasText(expr)) {
            return true;
        }
        for (String part : expr.split("(?i)\\s+(and|&&)\\s+")) {
            Matcher m = COND.matcher(part.trim());
            if (!m.find()) {
                log.warn("条件表达式无法解析，按满足处理: {}", part);
                continue;
            }
            Object v = ctx == null ? null : ctx.get(m.group(1));
            String actual = v == null ? "" : String.valueOf(v);
            boolean eq = actual.equalsIgnoreCase(m.group(3).trim());
            if ("=".equals(m.group(2)) ? !eq : eq) {
                return false;
            }
        }
        return true;
    }

    private ApprovalNode nodeBySeq(List<ApprovalNode> nodes, Integer seq) {
        for (ApprovalNode n : nodes) {
            if (n.getSeq().equals(seq)) {
                return n;
            }
        }
        return null;
    }

    private ApprovalNode prevNode(List<ApprovalNode> nodes, Integer seq) {
        ApprovalNode prev = null;
        for (ApprovalNode n : nodes) {
            if (n.getSeq() < seq && (prev == null || n.getSeq() > prev.getSeq())) {
                prev = n;
            }
        }
        return prev;
    }

    private ApprovalNode nextNode(List<ApprovalNode> nodes, Integer seq) {
        ApprovalNode next = null;
        for (ApprovalNode n : nodes) {
            if (n.getSeq() > seq && (next == null || n.getSeq() < next.getSeq())) {
                next = n;
            }
        }
        return next;
    }

    private void advance(ApprovalInstance inst, List<ApprovalNode> nodes) {
        ApprovalNode next = nextNode(nodes, inst.getCurrentSeq());
        if (next == null) {
            finish(inst, ApprovalStatus.APPROVED);
            return;
        }
        inst.setCurrentSeq(next.getSeq());
        instanceMapper.updateById(inst);
        createTasks(inst, next, parseCtx(inst.getContextJson()));
        log.info("审批推进 instance={} -> seq={} ({})", inst.getId(), next.getSeq(), next.getNodeName());
    }

    private void finish(ApprovalInstance inst, ApprovalStatus status) {
        inst.setStatus(status.name());
        inst.setFinishedAt(LocalDateTime.now());
        instanceMapper.updateById(inst);
        closePending(inst.getId(), "CANCELLED", "审批结束，待办自动作废");
    }

    private void closePending(Long instanceId, String action, String comment) {
        List<ApprovalTask> pending = taskMapper.selectList(new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getInstanceId, instanceId)
                .isNull(ApprovalTask::getAction));
        for (ApprovalTask t : pending) {
            t.setAction(action);
            t.setComment(comment);
            t.setActedAt(LocalDateTime.now());
            taskMapper.updateById(t);
        }
    }

    private void createTasks(ApprovalInstance inst, ApprovalNode node, Map<String, Object> ctx) {
        List<String> approvers = resolveApprovers(node);
        if (approvers.isEmpty()) {
            throw new BusinessException("节点[" + node.getNodeName() + "]解析不到审批人，请检查流程配置");
        }
        for (String a : approvers) {
            // 同节点同一人已有待办则不重复插入
            Long exist = taskMapper.selectCount(new LambdaQueryWrapper<ApprovalTask>()
                    .eq(ApprovalTask::getInstanceId, inst.getId())
                    .eq(ApprovalTask::getNodeSeq, node.getSeq())
                    .eq(ApprovalTask::getApprover, a)
                    .isNull(ApprovalTask::getAction));
            if (exist != null && exist > 0) {
                continue;
            }
            ApprovalTask t = new ApprovalTask();
            t.setInstanceId(inst.getId());
            t.setNodeSeq(node.getSeq());
            t.setApprover(a);
            t.setCreatedAt(LocalDateTime.now());
            taskMapper.insert(t);
        }
    }

    /** 审批人解析：ROLE/USER 精确解析，其余类型降级为角色，最终回落 ADMIN */
    private List<String> resolveApprovers(ApprovalNode node) {
        List<String> result = new ArrayList<>();
        String type = node.getApproverType() == null ? "ROLE" : node.getApproverType().toUpperCase();
        String value = node.getApproverValue() == null ? "" : node.getApproverValue().trim();

        if ("USER".equals(type)) {
            for (String u : value.split(",")) {
                if (StringUtils.hasText(u)) {
                    result.add(u.trim());
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }

        String roleCodes = value;
        if ("DEPT_LEADER".equals(type)) {
            log.warn("节点[{}]配置 DEPT_LEADER，但 sys_department 无主管字段，降级按角色 {} 解析",
                    node.getNodeName(), roleCodes.isEmpty() ? DEFAULT_APPROVER_ROLE : roleCodes);
        }
        if (roleCodes.isEmpty()) {
            roleCodes = DEFAULT_APPROVER_ROLE;
        }
        for (String role : roleCodes.split(",")) {
            String r = role.trim();
            if (r.isEmpty()) {
                continue;
            }
            List<String> users = jdbcTemplate.queryForList(
                    "SELECT u.username FROM sys_user u "
                            + "JOIN sys_user_role ur ON ur.user_id = u.id "
                            + "JOIN sys_role r ON r.id = ur.role_id "
                            + "WHERE r.role_code = ? AND u.status = 1 AND u.deleted = 0",
                    String.class, r);
            result.addAll(users);
        }
        if (result.isEmpty()) {
            log.warn("节点[{}]角色 {} 下没有启用用户，回落 {}", node.getNodeName(), roleCodes, DEFAULT_APPROVER_ROLE);
            result.addAll(jdbcTemplate.queryForList(
                    "SELECT u.username FROM sys_user u "
                            + "JOIN sys_user_role ur ON ur.user_id = u.id "
                            + "JOIN sys_role r ON r.id = ur.role_id "
                            + "WHERE r.role_code = ? AND u.status = 1 AND u.deleted = 0",
                    String.class, DEFAULT_APPROVER_ROLE));
        }
        List<String> distinct = new ArrayList<>();
        for (String s : result) {
            if (!distinct.contains(s)) {
                distinct.add(s);
            }
        }
        return distinct;
    }

    private ApprovalTask matchTask(List<ApprovalTask> pending) {
        String username = SecurityUtils.getCurrentUsername();
        String realName = SecurityUtils.getCurrentRealName();
        for (ApprovalTask t : pending) {
            if (t.getApprover() != null
                    && (t.getApprover().equals(username) || t.getApprover().equals(realName))) {
                return t;
            }
        }
        return null;
    }

    /** 上下文以 JSON 存在实例上，供后续节点条件判断与审计使用 */
    private String toJson(Map<String, Object> ctx) {
        if (ctx == null || ctx.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : ctx.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\":\"")
                    .append(e.getValue() == null ? "" : String.valueOf(e.getValue()).replace("\"", "\\\""))
                    .append('"');
        }
        return sb.append('}').toString();
    }

    private Map<String, Object> parseCtx(String json) {
        java.util.Map<String, Object> ctx = new java.util.HashMap<>();
        if (!StringUtils.hasText(json)) {
            return ctx;
        }
        Matcher m = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        while (m.find()) {
            ctx.put(m.group(1), m.group(2));
        }
        return ctx;
    }
}
