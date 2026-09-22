package com.hjgd.plm.material.service;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.dq.dto.DqRunResult;
import com.hjgd.plm.material.dto.MaterialDTO;
import com.hjgd.plm.material.dto.MaterialQueryDTO;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.entity.MaterialVersion;

import java.util.List;

public interface MaterialService {

    PageResult<Material> page(MaterialQueryDTO query);

    Material getById(Long id);

    Material getByPartNo(String partNo);

    Material create(MaterialDTO dto);

    Material update(MaterialDTO dto);

    /**
     * 内部/自动修复专用: 直接按字段写回实体,跳过 DQ 阻断校验和生命周期门禁。
     * 用于 DqAutoFixService 等已知意图的修复,不应被业务侧调用。
     */
    void updateQuietly(Material material);

    void delete(Long id);

    void submitReview(Long id);

    void release(Long id);

    void toProduction(Long id);

    void obsolete(Long id);

    void seal(Long id);

    void startChange(Long id);

    void applyEcnEffect(Long materialId, String versionAfter, String ecnNo, boolean stayInProduction);

    DqRunResult checkQuality(Long id);

    List<MaterialVersion> listVersions(Long id);

    boolean checkPartNoDuplicate(String partNo, Long excludeId);

    java.util.List<com.hjgd.plm.material.dto.MaterialSupplierDTO> listSuppliers(Long materialId);
}
