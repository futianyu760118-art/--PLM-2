package com.hjgd.plm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.hjgd.plm.**.mapper")
@EnableScheduling
public class PlmApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlmApplication.class, args);
    }
}
