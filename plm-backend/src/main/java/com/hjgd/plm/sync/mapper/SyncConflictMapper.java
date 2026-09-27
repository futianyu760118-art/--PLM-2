package com.hjgd.plm.sync.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hjgd.plm.sync.entity.SyncConflict;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SyncConflictMapper extends BaseMapper<SyncConflict> {
}
