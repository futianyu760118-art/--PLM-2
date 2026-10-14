package com.hjgd.plm.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.ledger.dto.LedgerQuery;
import com.hjgd.plm.ledger.entity.PlmLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PlmLedgerMapper extends BaseMapper<PlmLedger> {

    /**
     * 台账 / 资料汇总处 组合筛选分页（AC-A5-1~A5-5、AC-B6-1/6-8）。
     * 台账与汇总处同源，同一查询供两个视图使用。
     */
    IPage<PlmLedger> pageLedger(@Param("page") Page<PlmLedger> page, @Param("q") LedgerQuery q);

    /** 模块/表单侧证据区：按关联对象取引用条目（AC-A3-1/A3-5/A3-6、AC-A4-6） */
    @Select("SELECT l.* FROM plm_ledger l JOIN plm_ledger_link k ON k.ledger_id = l.id "
            + "WHERE l.deleted = 0 AND k.deleted = 0 AND k.link_status = 'ACTIVE' "
            + "AND k.object_type = #{objectType} AND k.object_id = #{objectId} "
            + "ORDER BY l.id DESC")
    List<PlmLedger> selectByObject(@Param("objectType") String objectType,
                                   @Param("objectId") String objectId);
}
