package com.hjgd.plm.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import lombok.Data;

@Data
@TableName("sys_role")
public class SysRole extends BaseEntity {

    private String roleCode;
    private String roleName;
    private Integer roleLevel;
    private Integer dataScope;
    private Integer builtin;
    private Integer status;
    private String remark;
}
