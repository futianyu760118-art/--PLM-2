package com.hjgd.plm.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员重置口令载荷。
 * 由 {@code @RequestParam} 改为请求体承载，避免口令进入 URL 查询串与访问日志（R3 / AC11.2）。
 */
@Data
public class ResetPasswordDTO {

    @NotBlank(message = "密码不能为空")
    private String password;
}
