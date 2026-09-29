package com.hjgd.plm.gate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Gate 判定实例（F08）：每次判定留痕，是「已放行」的唯一证据。 */
@Data
@TableName("plm_gate_instance")
public class GateInstance {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String gateCode;
    private String objectType;
    private String objectId;
    /** PENDING | PASS | FAIL | UNKNOWN */
    private String status;
    /** 未通过/无法判定的规则，JSON 数组文本。 */
    private String failedRules;
    /** 放行依据的证据引用，JSON 文本。 */
    private String evidenceRefs;
    private String reason;
    private String evaluatedBy;
    private LocalDateTime evaluatedAt;
}
