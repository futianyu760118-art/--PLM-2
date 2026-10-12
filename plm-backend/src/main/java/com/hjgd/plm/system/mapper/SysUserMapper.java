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

    /** 登录成功记录时间与来源 IP（R5 / AC6.6） */
    int updateLoginInfo(@Param("id") Long id, @Param("ip") String ip);

    /**
     * 落新口令并清空遗留态（R6 透明重哈希 / 自助改密 / 管理员重置共用）。
     * legacy_password 置 NULL 表示该账号此后只认 BCrypt。
     */
    int updatePasswordClearLegacy(@Param("id") Long id, @Param("password") String password);
}
