package com.hjgd.plm.ecn.service;

import com.hjgd.plm.ecn.entity.EcnImpact;

import java.util.List;

public interface EcnImpactService {

    List<EcnImpact> listByEcn(Long ecnId);

    List<EcnImpact> saveImpacts(Long ecnId, String ecnNo, List<String> impactTypes);

    void markHandled(Long impactId, String result);
}
