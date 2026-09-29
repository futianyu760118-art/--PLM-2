package com.hjgd.plm.evidence.controller;

import com.hjgd.plm.common.Result;
import com.hjgd.plm.evidence.entity.PlmEvidence;
import com.hjgd.plm.evidence.service.EvidenceService;
import com.hjgd.plm.log.annotation.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 统一证据底座接口（F09 / ISS-005）。
 *
 * <p>对齐 DEH_CONTRACT_V1 §6：证据可登记、可查询、可穿透；
 * 穿透接口返回断点数，断点数为 0 才满足「可穿透」验收项。
 */
@Tag(name = "AEOS-证据底座")
@RestController
@RequestMapping("/evidence")
@RequiredArgsConstructor
public class EvidenceController {

    private final EvidenceService evidenceService;

    @Operation(summary = "登记证据（强制四要素）")
    @OperationLog(value = "登记证据")
    @PostMapping
    public Result<PlmEvidence> register(@RequestBody RegisterReq req) {
        return Result.success(evidenceService.register(new EvidenceService.RegisterCommand(
                req.getSubjectType(), req.getSubjectId(), req.getEvidenceType(),
                req.getSourceSystem(), req.getSourceRecordId(), req.getUri(),
                req.getChecksum(), req.getVersion(), req.getConfidence(),
                req.getProvenance(), req.getMetadata(), req.getProjectNo(),
                req.getLegacyRefType(), req.getLegacyRefId())));
    }

    @Operation(summary = "按被证明对象查询证据")
    @GetMapping("/{subjectType}/{subjectId}")
    public Result<List<PlmEvidence>> listBySubject(@PathVariable String subjectType,
                                                   @PathVariable String subjectId) {
        return Result.success(evidenceService.listBySubject(subjectType, subjectId));
    }

    @Operation(summary = "证据穿透链（对象→证据→原始记录，含断点数）")
    @GetMapping("/{subjectType}/{subjectId}/trace")
    public Result<Map<String, Object>> trace(@PathVariable String subjectType,
                                             @PathVariable String subjectId) {
        return Result.success(evidenceService.trace(subjectType, subjectId));
    }

    @Operation(summary = "证据类型取值集")
    @GetMapping("/types")
    public Result<List<String>> types() {
        return Result.success(EvidenceService.EVIDENCE_TYPES);
    }

    @Data
    public static class RegisterReq {
        private String subjectType;
        private String subjectId;
        private String evidenceType;
        private String sourceSystem;
        private String sourceRecordId;
        private String uri;
        private String checksum;
        private String version;
        private BigDecimal confidence;
        private String provenance;
        private String metadata;
        private String projectNo;
        private String legacyRefType;
        private String legacyRefId;
    }
}
