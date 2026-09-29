package com.hjgd.plm.lifecycle.service;

import com.hjgd.plm.material.enums.MaterialStatus;

public interface LifecycleService {

    void assertTransition(String objectType, MaterialStatus from, String actionCode);

    /**
     * 带对象标识的迁移校验（F04）：非法跳转被拒时按 objectId 留痕（result=DENIED），
     * 使「连续 N 次非法跳转」在生命周期历史里可查、可计数。
     */
    void assertTransition(String objectType, String objectId, MaterialStatus from, String actionCode);

    MaterialStatus resolveToState(String objectType, MaterialStatus from, String actionCode);

    /** 带对象标识的状态解析；非法跳转按 objectId 留痕。 */
    MaterialStatus resolveToState(String objectType, String objectId, MaterialStatus from, String actionCode);

    void recordHistory(String objectType, String objectId, String from, String to,
                       String actionCode, String operator, String comment);
}
