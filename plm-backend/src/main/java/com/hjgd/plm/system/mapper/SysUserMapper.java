package com.hjgd.plm.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hjgd.plm.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    SysUser selectByUsername(@Param("username") String username);

    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    List<String> selectPermissionsByUserId(@Param("userId") Long userId);
}
