package com.hjgd.plm.gate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** Gate 规则（F08）：一条规则 = 类型 + 关键位 + 判定参数（JSONB）+ 严重度。 */
@Data
@TableName("plm_gate_rule")
public class GateRule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long gateId;
    /** NODE | APPROVAL | EVIDENCE | METRIC | CONTRACT */
    private String ruleType;
    private String ruleKey;
    /** 判定参数，JSON 文本（对应 JSONB 列）。 */
    private String predicate;
    /** BLOCK | WARN */
    private String severity;
    private Boolean enabled;
}
