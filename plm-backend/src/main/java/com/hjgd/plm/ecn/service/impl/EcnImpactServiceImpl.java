package com.hjgd.plm.ecn.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.ecn.entity.EcnImpact;
import com.hjgd.plm.ecn.mapper.EcnImpactMapper;
import com.hjgd.plm.ecn.service.EcnImpactService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EcnImpactServiceImpl implements EcnImpactService {

    private final EcnImpactMapper ecnImpactMapper;

    @Override
    public List<EcnImpact> listByEcn(Long ecnId) {
        return ecnImpactMapper.selectList(
                new LambdaQueryWrapper<EcnImpact>().eq(EcnImpact::getEcnId, ecnId));
    }

    @Override
    @Transactional
    public List<EcnImpact> saveImpacts(Long ecnId, String ecnNo, List<String> impactTypes) {
        // 已存在的影响类型跳过(幂等：避免 effect 与预填重复)
        Set<String> existing = new HashSet<>();
        for (EcnImpact old : listByEcn(ecnId)) {
            if (old.getImpactType() != null) {
                existing.add(old.getImpactType());
            }
        }
        List<EcnImpact> saved = new ArrayList<>();
        for (String type : impactTypes) {
            if (existing.contains(type)) {
                continue;
            }
            EcnImpact imp = new EcnImpact();
            imp.setEcnId(ecnId);
            imp.setEcnNo(ecnNo);
            imp.setImpactType(type);
            imp.setTargetType(mapTargetType(type));
            imp.setActionCode(mapAction(type));
            imp.setStatus("PENDING");
            imp.setCreatedAt(LocalDateTime.now());
            ecnImpactMapper.insert(imp);
            saved.add(imp);
            existing.add(type);
        }
        return saved;
    }

    @Override
    public void markHandled(Long impactId, String result) {
        EcnImpact imp = ecnImpactMapper.selectById(impactId);
        if (imp != null) {
            imp.setStatus("DONE");
            imp.setHandledAt(LocalDateTime.now());
            imp.setResultJson(result);
            ecnImpactMapper.updateById(imp);
        }
    }

    private String mapTargetType(String impactType) {
        return switch (impactType) {
            case "PART", "BOM" -> "BOM";
            case "FILE" -> "FILE";
            case "MOLD" -> "MOLD";
            case "SOP" -> "SOP";
            case "TRADE" -> "TRADE_DOC";
            default -> "OTHER";
        };
    }

    private String mapAction(String impactType) {
        return switch (impactType) {
            case "PART" -> "bump_version";
            case "BOM" -> "rebuild_bom";
            case "FILE" -> "obsolete_old";
            case "MOLD" -> "notify_mold";
            case "SOP" -> "flag_revise";
            case "TRADE" -> "refresh_docs";
            default -> "notify";
        };
    }
}
