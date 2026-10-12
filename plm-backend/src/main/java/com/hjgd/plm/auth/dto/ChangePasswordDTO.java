package com.hjgd.plm.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 自助改密：口令一律走请求体，避免出现在 URL 查询串/访问日志（AC11.2） */
@Data
public class ChangePasswordDTO {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度需为 6-64 位")
    private String newPassword;
}
