package com.hjgd.plm.auth.dto;

import lombok.Data;

import java.util.List;

@Data
public class LoginVO {

    private String token;
    private String tokenHeader;
    private String tokenPrefix;
    private Long userId;
    private String username;
    private String realName;
    private String avatar;
    private List<String> roles;
    private List<String> permissions;
}
