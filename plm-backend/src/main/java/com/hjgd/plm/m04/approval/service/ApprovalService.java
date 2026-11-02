package com.hjgd.plm.m04.approval.service;

import com.hjgd.plm.m04.approval.dto.ApprovalFlowDTO;
import com.hjgd.plm.m04.approval.dto.ApprovalProgressVO;
import com.hjgd.plm.m04.approval.dto.PendingTaskVO;
import com.hjgd.plm.m04.approval.entity.ApprovalFlow;
import com.hjgd.plm.m04.approval.entity.ApprovalInstance;
import com.hjgd.plm.m04.approval.entity.ApprovalNode;
import com.hjgd.plm.m04.approval.entity.ApprovalTask;
import com.hjgd.plm.m04.approval.enums.ApprovalAction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审批引擎。所有单据（ECN / ECR / DOCUMENT / REQUIREMENT / RISK）共用一套流转规则，
 * 流程节点存在 plm_approval_flow / plm_approval_node，改节点不需要改代码。
 */
public interface ApprovalService {

    // ---------- 流程定义 ----------

    List<ApprovalFlow> listFlows(String objectType);

    List<ApprovalNode> listNodes(Long flowId);

    /** 保存流程定义（含节点整体覆盖）。id 为空则新建。 */
    ApprovalFlow saveFlow(ApprovalFlowDTO dto);

    /** 启用/停用。同一 objectType 只允许一个 ENABLED 流程，启用时会自动降级其余流程。 */
    void changeFlowStatus(Long flowId, String status);

    void deleteFlow(Long flowId);

    // ---------- 引擎 ----------

    /**
     * 发起审批。
     *
     * @param objectType 单据类型 ECN / ECR / DOCUMENT ...
     * @param objectNo   单据号
     * @param flowCode   指定流程编码；为空则取该 objectType 下 ENABLED 的流程
     * @param context    条件判断上下文（如 change_type=MATERIAL），可为 null
     * @return 审批实例；若该单据类型没有启用任何流程，返回 null（调用方走旧逻辑兜底）
     */
    ApprovalInstance start(String objectType, String objectNo, String flowCode, Map<String, Object> context);

    /** 审批动作：通过 / 驳回 / 退回 / 终止 */
    ApprovalInstance act(Long instanceId, ApprovalAction action, String comment);

    /** 申请人撤回 */
    ApprovalInstance cancel(Long instanceId, String comment);

    /** 按单据号取实例（可能为 null） */
    ApprovalInstance instanceOf(String objectType, String objectNo);

    ApprovalInstance instanceById(Long instanceId);

    /** 当前用户的待办 */
    List<PendingTaskVO> pendingOfCurrentUser();

    /** 动作流水（按时间正序） */
    List<ApprovalTask> history(Long instanceId);

    /** 审批进度（节点 + 动作） */
    ApprovalProgressVO progress(String objectType, String objectNo);

    /** 撤销某单据的审批实例（用于单据作废，避免待办残留） */
    void cancelByObject(String objectType, String objectNo, String comment);

    /**
     * 超时扫描：遍历 RUNNING 实例，当前节点配置了 timeout_hours 且待办已超期的，
     * 按节点 on_timeout 执行 REMIND（仅日志）/ AUTO_PASS（代签通过并推进）/ ESCALATE（转给 ADMIN）。
     *
     * @param now 判定基准时间，便于测试注入
     * @return 本轮处理的实例数
     */
    int handleTimeouts(LocalDateTime now);
}
