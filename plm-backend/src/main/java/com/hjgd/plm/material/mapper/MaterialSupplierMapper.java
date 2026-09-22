package com.hjgd.plm.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hjgd.plm.material.entity.MaterialSupplier;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MaterialSupplierMapper extends BaseMapper<MaterialSupplier> {
    @Select("SELECT * FROM plm_material_supplier WHERE material_id=#{materialId} ORDER BY tier_rank, id")
    List<MaterialSupplier> listByMaterialId(Long materialId);

    @Delete("DELETE FROM plm_material_supplier WHERE material_id=#{materialId}")
    int deleteByMaterialId(Long materialId);
}