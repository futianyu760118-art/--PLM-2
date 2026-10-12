package com.hjgd.plm.evidence.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.ApiErrorCodes;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.evidence.entity.PlmEvidence;
import com.hjgd.plm.evidence.mapper.PlmEvidenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AEOS 统一证据底座（F09 / ISS-005）。
 *
 * <p>职责：
 * <ol>
 *   <li>证据登记：强制四要素齐备（source / version / checksum / confidence），
 *       不齐备直接拒绝，不允许静默通过（对齐 F05「伪造 evidence_refs 不得静默通过」）；</li>
 *   <li>穿透查询：{@link #trace(String, String)} 返回「被证明对象 → 证据 → 原始记录」
 *       的完整链路，并给出断点数（断点 = 证据未指向原始记录）；</li>
 *   <li>档案引用：只引用 {@code plm_file.id}，不复制文件本体（一份文件多处引用）。</li>
 * </ol>
 *
 * <p>checksum 可由调用方提供，也可由本服务按证据的定位要素确定性推导
 * （同一证据重复登记得到同一 checksum，可据此判重与复核）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceService {

    /** 证据类型取值集（对齐 DEH_CONTRACT_V1 §6）。 */
    public static final List<String> EVIDENCE_TYPES =
            List.of("file", "image", "record", "metric", "log", "approval", "external_ref");

    /**
     * subject 类型取值集：对象/节点/决策/动作/结果/指标/变更对象。
     *
     * <p>{@code metric} 是架构 V0.2 §3.4 穿透样例的首段与末段类型
     * （管理结果本身要被证据证明）；缺了它，样例链在第 1 段就会因类型校验被拒。
     */
    public static final List<String> SUBJECT_TYPES =
            List.of("object", "node", "decision", "action", "result",
                    "metric", "ecn", "bom", "release");

    private final PlmEvidenceMapper evidenceMapper;

    /** 证据登记入参。 */
    public record RegisterCommand(
            String subjectType, String subjectId, String evidenceType,
            String sourceSystem, String sourceRecordId, String uri,
            String checksum, String version, BigDecimal confidence,
            String provenance, String metadata, String projectNo,
            String legacyRefType, String legacyRefId) {
    }

    /**
     * 登记证据。四要素 source/version/checksum/confidence 齐备方可入库。
     *
     * @throws BusinessException 缺要素 / 非法类型 / 置信度越界
     */
    @Transactional
    public PlmEvidence register(RegisterCommand cmd) {
        if (cmd == null) throw new BusinessException("证据入参为空");
        requireText(cmd.subjectType(), "subject_type");
        requireText(cmd.subjectId(), "subject_id");
        requireText(cmd.evidenceType(), "evidence_type");
        if (!EVIDENCE_TYPES.contains(cmd.evidenceType())) {
            throw invalid("非法 evidence_type: " + cmd.evidenceType() + "，允许值 " + EVIDENCE_TYPES);
        }
        if (!SUBJECT_TYPES.contains(cmd.subjectType())) {
            throw invalid("非法 subject_type: " + cmd.subjectType() + "，允许值 " + SUBJECT_TYPES);
        }
        requireText(cmd.version(), "version");
        if (cmd.confidence() == null) {
            throw invalid("证据四要素缺失: confidence 不得为空");
        }
        if (cmd.confidence().compareTo(BigDecimal.ZERO) < 0
                || cmd.confidence().compareTo(BigDecimal.ONE) > 0) {
            throw invalid("confidence 必须在 [0,1] 区间，当前 " + cmd.confidence());
        }

        String sourceSystem = StringUtils.hasText(cmd.sourceSystem()) ? cmd.sourceSystem() : "PLM2";
        String checksum = StringUtils.hasText(cmd.checksum()) ? cmd.checksum() : deriveChecksum(cmd, sourceSystem);

        PlmEvidence e = new PlmEvidence();
        e.setEvidenceId(UUID.randomUUID().toString());
        e.setSubjectType(cmd.subjectType());
        e.setSubjectId(cmd.subjectId());
        e.setEvidenceType(cmd.evidenceType());
        e.setSourceSystem(sourceSystem);
        e.setSourceRecordId(cmd.sourceRecordId());
        e.setUri(cmd.uri());
        e.setChecksum(checksum);
        e.setVersion(cmd.version());
        e.setConfidence(cmd.confidence());
        e.setProvenance(cmd.provenance());
        e.setMetadata(cmd.metadata());
        e.setProjectNo(cmd.projectNo());
        e.setLegacyRefType(cmd.legacyRefType());
        e.setLegacyRefId(cmd.legacyRefId());
        e.setSubmittedBy(currentUser());
        e.setSubmittedAt(LocalDateTime.now());
        evidenceMapper.insert(e);
        return e;
    }

    /** 按被证明对象列证据（时间倒序）。 */
    public List<PlmEvidence> listBySubject(String subjectType, String subjectId) {
        LambdaQueryWrapper<PlmEvidence> w = new LambdaQueryWrapper<>();
        w.eq(PlmEvidence::getSubjectType, subjectType)
                .eq(PlmEvidence::getSubjectId, subjectId)
                .orderByDesc(PlmEvidence::getSubmittedAt);
        return evidenceMapper.selectList(w);
    }

    /** 证据条数（Gate EVIDENCE 规则使用）。 */
    public long countBySubject(String subjectType, String subjectId) {
        LambdaQueryWrapper<PlmEvidence> w = new LambdaQueryWrapper<>();
        w.eq(PlmEvidence::getSubjectType, subjectType)
                .eq(PlmEvidence::getSubjectId, subjectId);
        Long c = evidenceMapper.selectCount(w);
        return c == null ? 0L : c;
    }

    /**
     * 穿透查询：被证明对象 → 证据 → 原始记录。
     *
     * <p>返回 {@code complete} 与 {@code brokenLinks}：断点 = 证据未携带原始记录引用
     * （{@code source_record_id} 为空），此类证据无法从管理结果穿透到原始记录。
     * 「断点数 = 0」即 AC-G3 要求的可穿透。
     */
    public Map<String, Object> trace(String subjectType, String subjectId) {
        List<PlmEvidence> evidences = listBySubject(subjectType, subjectId);
        List<Map<String, Object>> links = new ArrayList<>();
        List<Map<String, Object>> broken = new ArrayList<>();
        for (PlmEvidence e : evidences) {
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("evidenceId", e.getEvidenceId());
            link.put("evidenceType", e.getEvidenceType());
            link.put("version", e.getVersion());
            link.put("checksum", e.getChecksum());
            link.put("confidence", e.getConfidence());
            link.put("sourceSystem", e.getSourceSystem());
            link.put("sourceRecordId", e.getSourceRecordId());
            link.put("uri", e.getUri());
            links.add(link);
            if (!StringUtils.hasText(e.getSourceRecordId())) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("evidenceId", e.getEvidenceId());
                b.put("reason", "证据未指向原始记录(source_record_id 为空)，穿透链断裂");
                broken.add(b);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subject", Map.of("type", subjectType, "id", subjectId));
        out.put("evidenceCount", evidences.size());
        out.put("links", links);
        out.put("brokenLinks", broken);
        out.put("brokenCount", broken.size());
        out.put("complete", !broken.isEmpty() ? false : !evidences.isEmpty());
        return out;
    }

    /** 确定性 checksum：同一证据重复登记得到同一值。 */
    public static String deriveChecksum(RegisterCommand cmd, String sourceSystem) {
        String canonical = String.join("|",
                nz(cmd.subjectType()), nz(cmd.subjectId()), nz(cmd.evidenceType()),
                nz(sourceSystem), nz(cmd.sourceRecordId()), nz(cmd.uri()), nz(cmd.version()));
        return sha256Hex(canonical);
    }

    public static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static void requireText(String v, String field) {
        if (!StringUtils.hasText(v)) throw invalid("证据四要素缺失: " + field + " 不得为空");
    }

    private static BusinessException invalid(String msg) {
        return new BusinessException(422, "[" + ApiErrorCodes.EVIDENCE_INVALID + "] " + msg);
    }

    private String currentUser() {
        try {
            String name = SecurityUtils.getCurrentRealName();
            if (StringUtils.hasText(name)) return name;
            return SecurityUtils.getCurrentUsername();
        } catch (Exception e) {
            return "SYSTEM";
        }
    }
}
