package com.hjgd.plm.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hjgd.plm.common.BaseEntity;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;
    /** 口令哈希，禁止出参（AC9.2/AC11） */
    @JsonIgnore
    @ToString.Exclude
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

    /** EBMS 迁移遗留口令(scrypt 串)，仅迁移期存在，透明重哈希后清空；禁止出参 */
    @JsonIgnore
    @ToString.Exclude
    private String legacyPassword;
    /** 1=下次登录强制改密 */
    private Integer mustChangePassword;
    /** 账号来源：null=PLM2 原生，EBMS=迁入 */
    private String source;
    /** EBMS 迁入批次号，用于精确回滚 */
    private String migrationRunId;

    @TableField(exist = false)
    private String deptName;

    @TableField(exist = false)
    private List<SysRole> roles;

    @TableField(exist = false)
    private List<String> permissions;
}
