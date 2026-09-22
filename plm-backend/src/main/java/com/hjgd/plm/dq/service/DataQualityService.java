package com.hjgd.plm.dq.service;

import com.hjgd.plm.dq.dto.DqRunResult;
import com.hjgd.plm.material.entity.Material;

public interface DataQualityService {

    DqRunResult runForPart(Material material, String trigger);

    void assertNoBlock(DqRunResult result);

    /**
     * 全量夜检：扫描全部未删除料号执行 DQ，刷新 plm_dq_object_score，
     * 并对 WARN/INFO 级别问题开 plm_dq_debt 债务（幂等：已 OPEN 同规则不重复开）。
     *
     * @param trigger 触发类型，如 NIGHTLY / MANUAL
     * @return 扫描对象数量
     */
    int runFullScan(String trigger);
}
