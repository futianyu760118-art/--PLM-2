package com.hjgd.plm.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hjgd.plm.material.entity.EntityHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EntityHistoryMapper extends BaseMapper<EntityHistory> {

    @Select("SELECT * FROM plm_entity_history WHERE object_type=#{objectType} AND object_id=#{objectId} ORDER BY version DESC LIMIT 200")
    List<EntityHistory> listByObject(@Param("objectType") String objectType, @Param("objectId") String objectId);

    @Select("SELECT COALESCE(MAX(version),0) FROM plm_entity_history WHERE object_type=#{objectType} AND object_id=#{objectId}")
    Integer maxVersion(@Param("objectType") String objectType, @Param("objectId") String objectId);
}