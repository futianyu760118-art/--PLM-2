package com.hjgd.plm.sync.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hjgd.plm.sync.entity.SyncCheckpoint;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SyncCheckpointMapper extends BaseMapper<SyncCheckpoint> {
}
