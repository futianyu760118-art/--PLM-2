package com.hjgd.plm.file.watermark;

import lombok.Data;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 水印配置 (显性水印 + 隐形溯源水印)
 */
@Data
public class WatermarkConfig {

    /** 企业名称 */
    private String companyName = "宁波恒剑光电科技有限公司";
    /** 外协单位 */
    private String outsourceCompany;
    /** 操作人账号 (隐形溯源) */
    private String operatorAccount;
    /** IP地址 (隐形溯源) */
    private String ip;
    /** 日期 */
    private String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    /** 旋转角度 */
    private float angle = 45f;
    /** 不透明度 (0-1) */
    private float opacity = 0.25f;
    /** 字体大小 */
    private float fontSize = 28f;
    /** 有效期天数 */
    private Integer validityDays;
    /** 失效日期 */
    private String expireDate;

    public static WatermarkConfig forOutsource(String outsourceCompany, String operator, String ip, int validityDays) {
        WatermarkConfig c = new WatermarkConfig();
        c.setOutsourceCompany(outsourceCompany);
        c.setOperatorAccount(operator);
        c.setIp(ip);
        c.setValidityDays(validityDays);
        c.setExpireDate(LocalDate.now().plusDays(validityDays).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        return c;
    }

    public String visibleText() {
        return companyName + " | " + outsourceCompany + " | " + date
                + (expireDate != null ? " | 有效期至 " + expireDate : "");
    }

    public String invisibleText() {
        return "UID:" + operatorAccount + "|IP:" + ip + "|" + date;
    }
}
