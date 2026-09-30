package com.hjgd.plm.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录审计日志（D2 / AC15.1）。仅记录登录事件结果，不含任何口令材料。
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog {

    /** 结果：登录成功 */
    public static final int RESULT_SUCCESS = 1;
    /** 结果：登录失败 */
    public static final int RESULT_FAILURE = 0;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private Long userId;
    private Integer result;
    private String ip;
    private String device;
    private String message;
    private LocalDateTime createdAt;
}
