package com.hjgd.plm.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;
    private String password;
    private String realName;
    private String employeeNo;
    private String email;
    private String phone;
    private String avatar;
    private Long deptId;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private String remark;

    @TableField(exist = false)
    private String deptName;

    @TableField(exist = false)
    private List<SysRole> roles;

    @TableField(exist = false)
    private List<String> permissions;
}
