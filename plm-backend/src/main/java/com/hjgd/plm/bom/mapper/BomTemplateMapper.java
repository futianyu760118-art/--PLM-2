package com.hjgd.plm.bom.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hjgd.plm.bom.entity.BomTemplate;
import com.hjgd.plm.bom.entity.BomTemplateItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BomTemplateMapper extends BaseMapper<BomTemplate> {

    @Select("SELECT * FROM plm_bom_template_item WHERE template_id=#{templateId} ORDER BY sort_order, id")
    List<BomTemplateItem> listItems(@Param("templateId") Long templateId);

    @Select("SELECT * FROM plm_bom_template WHERE enabled=true ORDER BY sort_order, id")
    List<BomTemplate> listEnabled();

    @Select("SELECT * FROM plm_bom_template WHERE template_code=#{code}")
    BomTemplate findByCode(@Param("code") String code);
}