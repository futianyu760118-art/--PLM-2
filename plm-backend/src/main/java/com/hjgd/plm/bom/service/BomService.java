package com.hjgd.plm.bom.service;

import com.hjgd.plm.bom.dto.BomDTO;
import com.hjgd.plm.bom.dto.BomItemDTO;
import com.hjgd.plm.bom.entity.Bom;
import com.hjgd.plm.bom.entity.BomItem;
import com.hjgd.plm.common.PageResult;

import java.util.List;
import java.util.Map;

public interface BomService {

    PageResult<Bom> page(Integer pageNum, Integer pageSize, String rootPartNo, String status);

    Bom getById(Long id);

    Bom getByPartNo(String rootPartNo);

    Bom create(BomDTO dto);

    BomItem addItem(BomItemDTO dto);

    BomItem updateItem(BomItemDTO dto);

    void deleteItem(Long itemId);

    List<BomItem> getTree(Long bomId);

    List<BomItem> getFlatList(Long bomId);

    void release(Long bomId);

    void archiveVersion(Long bomId, String ecnNo, String reason);

    Bom buildMbom(Long ebomId);

    Bom buildSbom(Long sourceBomId);

    List<Map<String, Object>> whereUsed(String partNo);

    List<Map<String, Object>> cbomExpand(String rootPartNo);

    /** ECN生效：归档当前已发布EBOM版本快照并升版(标记需重新发布)，返回旧版本号(无BOM返回null) */
    String bumpVersionForEcn(String rootPartNo, String ecnNo, String newVersionNo);

    /**
     * 一键从 BOM 模板(结构件/紧固件/包装/电子)套用创建 BOM。
     * @param rootPartNo 顶级成品料号
     * @param templateCodes 应用多个模板时,子件合并, sort_order 按模板顺序递增
     * @param includeOptional 是否包含可选项
     * @return 创建好的 BOM
     */
    Bom createFromTemplates(String rootPartNo, List<String> templateCodes, boolean includeOptional);

    /** 拖拽排序: 将 [itemId, ...] 按顺序写入 sort_order */
    void reorderItems(Long bomId, List<Long> itemIdsInOrder);
}
