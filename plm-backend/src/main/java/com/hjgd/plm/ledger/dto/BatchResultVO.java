package com.hjgd.plm.ledger.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 批量操作结果：成功/失败计数与逐条失败原因（AC-A5-7/A5-8）。 */
@Data
public class BatchResultVO {

    private int successCount;
    private int failCount;
    /** [{target, reason}] */
    private List<Map<String, Object>> failures = new ArrayList<>();
    /** 批量上传生成的台账 ID，或批量关联命中的台账 ID */
    private List<Long> ledgerIds = new ArrayList<>();

    public void addFailure(Object target, String reason) {
        failures.add(Map.of("target", String.valueOf(target), "reason", reason));
        failCount++;
    }
}
