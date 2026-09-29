package com.hjgd.plm.project.controller;

import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.log.annotation.OperationLog;
import com.hjgd.plm.project.entity.ProjectNode;
import com.hjgd.plm.project.entity.ProjectNodeApproval;
import com.hjgd.plm.project.mapper.ProjectNodeMapper;
import com.hjgd.plm.project.service.NodeApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 关键节点双级审批接口（F07 / ISS-001）。
 *
 * <p>审批只负责「记录事实」；是否放行由统一 Gate 引擎在节点置 DONE 时判定，
 * 审批接口无法直接把节点置为完成 —— 不存在绕过 Gate 的路径。
 */
@Tag(name = "研发项目-关键节点双审批")
@RestController
@RequestMapping("/project/nodes/{nodeId}/approval")
@RequiredArgsConstructor
public class NodeApprovalController {

    private final NodeApprovalService approvalService;
    private final ProjectNodeMapper nodeMapper;

    @Operation(summary = "节点审批状态(含双级是否齐备)")
    @GetMapping
    public Result<Map<String, Object>> state(@PathVariable Long nodeId) {
        ProjectNode node = require(nodeId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nodeId", nodeId);
        out.put("nodeCode", node.getNodeCode());
        out.put("nodeName", node.getNodeName());
        out.put("isKey", node.getIsKey());
        out.put("dualApproved", approvalService.isDualApproved(nodeId));
        NodeApprovalService.ApprovalState s = approvalService.state(nodeId);
        out.put("rdLeadDecision", s.rdLeadDecision());
        out.put("rdLeadApprover", s.rdLeadApprover());
        out.put("gmDecision", s.gmDecision());
        out.put("gmApprover", s.gmApprover());
        out.put("history", approvalService.history(nodeId));
        out.put("levels", approvalService.levelCatalog());
        return Result.success(out);
    }

    @Operation(summary = "提交审批决定(1=研发主管, 2=总经理)")
    @OperationLog(value = "节点审批")
    @PostMapping
    public Result<ProjectNodeApproval> decide(@PathVariable Long nodeId, @RequestBody DecideReq req) {
        ProjectNode node = require(nodeId);
        return Result.success(approvalService.decide(nodeId, node.getProjectId(), node.getProjectNo(),
                node.getNodeCode(), req.getLevel() == null ? 0 : req.getLevel(),
                req.getDecision(), req.getComment()));
    }

    @Operation(summary = "撤回全部审批(回退/重开)")
    @OperationLog(value = "节点审批撤回")
    @PostMapping("/revoke")
    public Result<Map<String, Object>> revoke(@PathVariable Long nodeId, @RequestBody(required = false) DecideReq req) {
        ProjectNode node = require(nodeId);
        int n = approvalService.revokeAll(nodeId, node.getProjectId(), node.getProjectNo(),
                node.getNodeCode(), req == null ? null : req.getComment());
        return Result.success(Map.of("nodeId", nodeId, "revokedLevels", n));
    }

    @Operation(summary = "审批级别与允许角色")
    @GetMapping("/levels")
    public Result<List<Map<String, Object>>> levels(@PathVariable Long nodeId) {
        return Result.success(approvalService.levelCatalog());
    }

    private ProjectNode require(Long nodeId) {
        ProjectNode node = nodeMapper.selectById(nodeId);
        if (node == null) throw new BusinessException("节点不存在: " + nodeId);
        return node;
    }

    @Data
    public static class DecideReq {
        /** 1=研发主管(L1), 2=总经理(L2) */
        private Integer level;
        /** APPROVED | REJECTED */
        private String decision;
        private String comment;
    }
}
