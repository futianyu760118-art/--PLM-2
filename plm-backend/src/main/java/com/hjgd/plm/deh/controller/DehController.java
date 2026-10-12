package com.hjgd.plm.deh.controller;

import com.hjgd.plm.agent.entity.AgentAction;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.deh.entity.AgentResult;
import com.hjgd.plm.deh.entity.Learning;
import com.hjgd.plm.deh.entity.ObjectOwner;
import com.hjgd.plm.deh.entity.PlmDecision;
import com.hjgd.plm.deh.service.ActionService;
import com.hjgd.plm.deh.service.ChainTraceService;
import com.hjgd.plm.deh.service.DecisionService;
import com.hjgd.plm.deh.service.LearningService;
import com.hjgd.plm.deh.service.ObjectOwnerService;
import com.hjgd.plm.deh.service.ResultService;
import com.hjgd.plm.log.annotation.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * DEH 决策-执行闭环接口（F01 / F09 / F16）。
 *
 * <p>链路：管理结果 → Decision → Action → Result → Verify → 原始记录。
 * 关键语义 {@code done ≠ verified}：动作置 done 只是执行者声明完成，
 * 结果经独立验证置 verified 才表示被接受。
 */
@Tag(name = "AEOS-DEH 决策执行闭环")
@RestController
@RequestMapping("/deh")
@RequiredArgsConstructor
public class DehController {

    private final DecisionService decisionService;
    private final ActionService actionService;
    private final ResultService resultService;
    private final LearningService learningService;
    private final ObjectOwnerService objectOwnerService;
    private final ChainTraceService chainTraceService;

    // ------------------------------------------------------------ Decision

    @Operation(summary = "建立决策（draft）")
    @OperationLog(value = "DEH 建立决策")
    @PostMapping("/decisions")
    public Result<PlmDecision> createDecision(@RequestBody DecisionReq req) {
        return Result.success(decisionService.create(new DecisionService.CreateCommand(
                req.sourceType(), req.sourceId(), req.objectType(), req.objectId(),
                req.content(), req.reason(), req.decisionOwnerId(),
                req.decisionCaseId(), req.goalId(), req.evidenceRefs())));
    }

    @Operation(summary = "确认决策（draft→confirmed）")
    @OperationLog(value = "DEH 确认决策")
    @PostMapping("/decisions/{decisionId}/confirm")
    public Result<PlmDecision> confirmDecision(@PathVariable String decisionId) {
        return Result.success(decisionService.confirm(decisionId));
    }

    @Operation(summary = "取代决策（保留历史，置 superseded）")
    @OperationLog(value = "DEH 取代决策")
    @PostMapping("/decisions/{decisionId}/supersede")
    public Result<PlmDecision> supersedeDecision(@PathVariable String decisionId,
                                                 @RequestParam(required = false) String reason) {
        return Result.success(decisionService.supersede(decisionId, reason));
    }

    @Operation(summary = "决策详情")
    @GetMapping("/decisions/{decisionId}")
    public Result<PlmDecision> getDecision(@PathVariable String decisionId) {
        return Result.success(decisionService.get(decisionId));
    }

    @Operation(summary = "按上游来源查决策（穿透链第 1→2 段）")
    @GetMapping("/decisions")
    public Result<List<PlmDecision>> listDecisions(@RequestParam String sourceType,
                                                   @RequestParam String sourceId) {
        return Result.success(decisionService.listBySource(sourceType, sourceId));
    }

    // ------------------------------------------------------------ Action

    @Operation(summary = "建立动作（五要素齐备方可成立）")
    @OperationLog(value = "DEH 建立动作")
    @PostMapping("/actions")
    public Result<AgentAction> createAction(@RequestBody ActionReq req) {
        return Result.success(actionService.create(new ActionService.CreateCommand(
                req.decisionId(), req.sourceType(), req.sourceId(),
                req.objectType(), req.objectId(), req.description(),
                req.expectedResult(), req.ownerId(), req.ownerRole(),
                req.priority(), req.dueAt(), req.verificationRule(),
                req.escalationRule(), req.evidenceRequired())));
    }

    @Operation(summary = "动作状态迁移（candidate→confirmed→in_progress→done）")
    @OperationLog(value = "DEH 动作状态迁移")
    @PostMapping("/actions/{actionId}/transition")
    public Result<AgentAction> transitionAction(@PathVariable String actionId,
                                                @RequestParam String to) {
        return Result.success(actionService.transition(actionId, to));
    }

    @Operation(summary = "动作详情")
    @GetMapping("/actions/{actionId}")
    public Result<AgentAction> getAction(@PathVariable String actionId) {
        return Result.success(actionService.get(actionId));
    }

    @Operation(summary = "动作逾期检查（派生事实，不落库）")
    @GetMapping("/actions/{actionId}/overdue")
    public Result<Map<String, Object>> actionOverdue(@PathVariable String actionId) {
        return Result.success(actionService.overdueOf(actionId));
    }

    // ------------------------------------------------------------ Result

    @Operation(summary = "提交结果（submitted，不等于已验证）")
    @OperationLog(value = "DEH 提交结果")
    @PostMapping("/results")
    public Result<AgentResult> submitResult(@RequestBody ResultReq req) {
        return Result.success(resultService.submit(new ResultService.SubmitCommand(
                req.actionId(), req.objectType(), req.objectId(),
                req.expectedResult(), req.actualResult(), req.evidenceRefs())));
    }

    @Operation(summary = "独立验证结果（verified，done≠verified）")
    @OperationLog(value = "DEH 验证结果")
    @PostMapping("/results/{resultId}/verify")
    public Result<AgentResult> verifyResult(@PathVariable String resultId,
                                            @RequestBody(required = false) VerifyReq req) {
        String evidenceRefs = req == null ? null : req.evidenceRefs();
        String exemption = req == null ? null : req.exemptionReason();
        return Result.success(resultService.verify(resultId, evidenceRefs, exemption));
    }

    @Operation(summary = "驳回结果（保留原因）")
    @OperationLog(value = "DEH 驳回结果")
    @PostMapping("/results/{resultId}/reject")
    public Result<AgentResult> rejectResult(@PathVariable String resultId,
                                            @RequestParam(required = false) String reason) {
        return Result.success(resultService.reject(resultId, reason));
    }

    @Operation(summary = "结果详情")
    @GetMapping("/results/{resultId}")
    public Result<AgentResult> getResult(@PathVariable String resultId) {
        return Result.success(resultService.get(resultId));
    }

    // ------------------------------------------------------------ Learning

    @Operation(summary = "沉淀学习条目（draft）")
    @PostMapping("/learnings")
    public Result<Learning> createLearning(@RequestBody LearningReq req) {
        return Result.success(learningService.create(new LearningService.CreateCommand(
                req.source(), req.content(), req.version(), req.rollbackRef())));
    }

    @Operation(summary = "发布学习（三门槛：独立验证/无收益退化越权/可回退）")
    @OperationLog(value = "DEH 发布学习")
    @PostMapping("/learnings/{learningId}/publish")
    public Result<Learning> publishLearning(@PathVariable String learningId,
                                            @RequestBody LearningService.PublishCommand cmd) {
        return Result.success(learningService.publish(learningId, cmd));
    }

    @Operation(summary = "回退学习（published→rolled_back，记录保留）")
    @PostMapping("/learnings/{learningId}/rollback")
    public Result<Learning> rollbackLearning(@PathVariable String learningId,
                                             @RequestParam(required = false) String reason) {
        return Result.success(learningService.rollback(learningId, reason));
    }

    @Operation(summary = "已发布学习清单")
    @GetMapping("/learnings/published")
    public Result<List<Learning>> publishedLearnings() {
        return Result.success(learningService.listPublished());
    }

    // ------------------------------------------------------------ 唯一事实源

    @Operation(summary = "对象 Owner 裁定清单（唯一事实源）")
    @GetMapping("/object-owners")
    public Result<List<ObjectOwner>> objectOwners() {
        return Result.success(objectOwnerService.list());
    }

    @Operation(summary = "对象 Owner 裁定完整性核对（缺项=0 才算生效）")
    @GetMapping("/object-owners/check")
    public Result<Map<String, Object>> objectOwnerCheck() {
        return Result.success(objectOwnerService.check());
    }

    @Operation(summary = "登记/变更对象 Owner")
    @PostMapping("/object-owners")
    public Result<ObjectOwner> saveObjectOwner(@RequestBody ObjectOwner req) {
        return Result.success(objectOwnerService.save(req));
    }

    // ------------------------------------------------------------ 穿透追溯

    @Operation(summary = "端到端穿透追溯（6 段，断点=0 为可穿透）")
    @GetMapping("/chain/{subjectType}/{subjectId}")
    public Result<Map<String, Object>> chain(@PathVariable String subjectType,
                                             @PathVariable String subjectId) {
        return Result.success(chainTraceService.trace(subjectType, subjectId));
    }

    // ------------------------------------------------------------ 请求体

    public static class DecisionReq {
        private String sourceType;
        private String sourceId;
        private String objectType;
        private String objectId;
        private String content;
        private String reason;
        private String decisionOwnerId;
        private String decisionCaseId;
        private String goalId;
        private String evidenceRefs;

        public String sourceType() { return sourceType; }
        public String sourceId() { return sourceId; }
        public String objectType() { return objectType; }
        public String objectId() { return objectId; }
        public String content() { return content; }
        public String reason() { return reason; }
        public String decisionOwnerId() { return decisionOwnerId; }
        public String decisionCaseId() { return decisionCaseId; }
        public String goalId() { return goalId; }
        public String evidenceRefs() { return evidenceRefs; }

        public void setSourceType(String v) { this.sourceType = v; }
        public void setSourceId(String v) { this.sourceId = v; }
        public void setObjectType(String v) { this.objectType = v; }
        public void setObjectId(String v) { this.objectId = v; }
        public void setContent(String v) { this.content = v; }
        public void setReason(String v) { this.reason = v; }
        public void setDecisionOwnerId(String v) { this.decisionOwnerId = v; }
        public void setDecisionCaseId(String v) { this.decisionCaseId = v; }
        public void setGoalId(String v) { this.goalId = v; }
        public void setEvidenceRefs(String v) { this.evidenceRefs = v; }
    }

    public static class ActionReq {
        private String decisionId;
        private String sourceType;
        private String sourceId;
        private String objectType;
        private String objectId;
        private String description;
        private String expectedResult;
        private String ownerId;
        private String ownerRole;
        private String priority;
        private OffsetDateTime dueAt;
        private String verificationRule;
        private String escalationRule;
        private Boolean evidenceRequired;

        public String decisionId() { return decisionId; }
        public String sourceType() { return sourceType; }
        public String sourceId() { return sourceId; }
        public String objectType() { return objectType; }
        public String objectId() { return objectId; }
        public String description() { return description; }
        public String expectedResult() { return expectedResult; }
        public String ownerId() { return ownerId; }
        public String ownerRole() { return ownerRole; }
        public String priority() { return priority; }
        public OffsetDateTime dueAt() { return dueAt; }
        public String verificationRule() { return verificationRule; }
        public String escalationRule() { return escalationRule; }
        public Boolean evidenceRequired() { return evidenceRequired; }

        public void setDecisionId(String v) { this.decisionId = v; }
        public void setSourceType(String v) { this.sourceType = v; }
        public void setSourceId(String v) { this.sourceId = v; }
        public void setObjectType(String v) { this.objectType = v; }
        public void setObjectId(String v) { this.objectId = v; }
        public void setDescription(String v) { this.description = v; }
        public void setExpectedResult(String v) { this.expectedResult = v; }
        public void setOwnerId(String v) { this.ownerId = v; }
        public void setOwnerRole(String v) { this.ownerRole = v; }
        public void setPriority(String v) { this.priority = v; }
        public void setDueAt(OffsetDateTime v) { this.dueAt = v; }
        public void setVerificationRule(String v) { this.verificationRule = v; }
        public void setEscalationRule(String v) { this.escalationRule = v; }
        public void setEvidenceRequired(Boolean v) { this.evidenceRequired = v; }
    }

    public static class ResultReq {
        private String actionId;
        private String objectType;
        private String objectId;
        private String expectedResult;
        private String actualResult;
        private String evidenceRefs;

        public String actionId() { return actionId; }
        public String objectType() { return objectType; }
        public String objectId() { return objectId; }
        public String expectedResult() { return expectedResult; }
        public String actualResult() { return actualResult; }
        public String evidenceRefs() { return evidenceRefs; }

        public void setActionId(String v) { this.actionId = v; }
        public void setObjectType(String v) { this.objectType = v; }
        public void setObjectId(String v) { this.objectId = v; }
        public void setExpectedResult(String v) { this.expectedResult = v; }
        public void setActualResult(String v) { this.actualResult = v; }
        public void setEvidenceRefs(String v) { this.evidenceRefs = v; }
    }

    public static class VerifyReq {
        private String evidenceRefs;
        private String exemptionReason;

        public String evidenceRefs() { return evidenceRefs; }
        public String exemptionReason() { return exemptionReason; }

        public void setEvidenceRefs(String v) { this.evidenceRefs = v; }
        public void setExemptionReason(String v) { this.exemptionReason = v; }
    }

    public static class LearningReq {
        private String source;
        private String content;
        private String version;
        private String rollbackRef;

        public String source() { return source; }
        public String content() { return content; }
        public String version() { return version; }
        public String rollbackRef() { return rollbackRef; }

        public void setSource(String v) { this.source = v; }
        public void setContent(String v) { this.content = v; }
        public void setVersion(String v) { this.version = v; }
        public void setRollbackRef(String v) { this.rollbackRef = v; }
    }
}
