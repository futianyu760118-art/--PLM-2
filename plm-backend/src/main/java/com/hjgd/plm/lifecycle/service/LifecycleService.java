package com.hjgd.plm.lifecycle.service;

import com.hjgd.plm.material.enums.MaterialStatus;

public interface LifecycleService {

    void assertTransition(String objectType, MaterialStatus from, String actionCode);

    MaterialStatus resolveToState(String objectType, MaterialStatus from, String actionCode);

    void recordHistory(String objectType, String objectId, String from, String to,
                       String actionCode, String operator, String comment);
}
