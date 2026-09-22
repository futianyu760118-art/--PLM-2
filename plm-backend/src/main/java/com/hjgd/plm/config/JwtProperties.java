package com.hjgd.plm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "plm.jwt")
public class JwtProperties {

    private String secret;
    private Long expire;
    private String header = "Authorization";
    private String prefix = "Bearer ";
}
