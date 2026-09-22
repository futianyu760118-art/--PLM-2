package com.hjgd.plm.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hjgd.plm.material.entity.MaterialParam;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MaterialParamMapper extends BaseMapper<MaterialParam> {

    @Insert("""
            INSERT INTO plm_material_param(part_no, param_key, param_value, updated_by, updated_at)
            VALUES(#{partNo}, #{paramKey}, #{paramValue}, #{updatedBy}, NOW())
            ON CONFLICT (part_no, param_key) DO UPDATE SET
              param_value = EXCLUDED.param_value,
              updated_by  = EXCLUDED.updated_by,
              updated_at  = NOW()
            """)
    int upsert(@Param("partNo") String partNo,
               @Param("paramKey") String paramKey,
               @Param("paramValue") String paramValue,
               @Param("updatedBy") String updatedBy);
}
