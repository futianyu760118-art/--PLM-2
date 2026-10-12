package com.hjgd.plm.deh.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DEH 结果（{@code plm_agent_result}，DEH_CONTRACT_V1 §5）。
 *
 * <p>核心语义 {@code done ≠ verified}：执行者提交只是 {@code submitted}，
 * 只有独立验证人（{@code verifiedBy ≠ submittedBy}）确认后才置 {@code verified}。
 */
@Data
@TableName("plm_agent_result")
public class AgentResult {

    @TableId(type = IdType.INPUT)
    private String resultId;
    private String actionId;
    private String objectType;
    private String objectId;
    private String expectedResult;
    private String actualResult;
    /** submitted | verified | rejected | superseded */
    private String status;
    private String submittedBy;
    private LocalDateTime submittedAt;
    private String verifiedBy;
    private LocalDateTime verifiedAt;
    private String verificationNote;
    private String evidenceRefs;
    private LocalDateTime createdAt;
}
