package com.hjgd.plm.material.service;

import com.hjgd.plm.material.dto.MaterialSupplierDTO;
import com.hjgd.plm.material.entity.MaterialSupplier;
import com.hjgd.plm.material.mapper.MaterialSupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialSupplierService {

    private final MaterialSupplierMapper mapper;

    @Transactional
    public List<MaterialSupplierDTO> replaceSuppliers(Long materialId, List<MaterialSupplierDTO> dtos) {
        mapper.deleteByMaterialId(materialId);
        List<MaterialSupplierDTO> out = new ArrayList<>();
        if (dtos == null) return out;
        for (MaterialSupplierDTO d : dtos) {
            if (d.getSupplierCode() == null || d.getSupplierCode().isBlank()) continue;
            MaterialSupplier e = new MaterialSupplier();
            e.setMaterialId(materialId);
            e.setSupplierCode(d.getSupplierCode());
            e.setSupplierName(d.getSupplierName());
            e.setTierRank(d.getTierRank() == null ? 1 : d.getTierRank());
            e.setPrice(d.getPrice());
            e.setCurrency(d.getCurrency() == null ? "CNY" : d.getCurrency());
            e.setSharePct(d.getSharePct());
            e.setLeadTimeDays(d.getLeadTimeDays());
            e.setMoq(d.getMoq());
            e.setRemark(d.getRemark());
            e.setEnabled(d.getEnabled() == null ? Boolean.TRUE : d.getEnabled());
            mapper.insert(e);
            d.setId(e.getId());
            d.setMaterialId(materialId);
            out.add(d);
        }
        return out;
    }

    public List<MaterialSupplierDTO> listByMaterialId(Long materialId) {
        List<MaterialSupplier> es = mapper.listByMaterialId(materialId);
        List<MaterialSupplierDTO> out = new ArrayList<>();
        for (MaterialSupplier e : es) {
            MaterialSupplierDTO d = new MaterialSupplierDTO();
            d.setId(e.getId());
            d.setMaterialId(e.getMaterialId());
            d.setSupplierCode(e.getSupplierCode());
            d.setSupplierName(e.getSupplierName());
            d.setTierRank(e.getTierRank());
            d.setPrice(e.getPrice());
            d.setCurrency(e.getCurrency());
            d.setSharePct(e.getSharePct());
            d.setLeadTimeDays(e.getLeadTimeDays());
            d.setMoq(e.getMoq());
            d.setRemark(e.getRemark());
            d.setEnabled(e.getEnabled());
            out.add(d);
        }
        return out;
    }
}