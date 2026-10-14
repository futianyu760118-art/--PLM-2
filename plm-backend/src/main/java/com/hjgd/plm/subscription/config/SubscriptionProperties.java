package com.hjgd.plm.subscription.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 资料分类订阅与报告推送（能力线 B）边界值配置。
 *
 * <p>默认值即 PAND-198 的 AC 取值，全部可配置化（架构 §8）：
 * 上限类阈值集中在服务层做计数/校验，避免散落在各 controller。
 */
@Data
@Component
@ConfigurationProperties(prefix = "plm.subscription")
public class SubscriptionProperties {

    /** 维度名称长度上限（AC-B1-1：≤ 20 字） */
    private int dimNameMax = 20;

    /** 分类值长度上限（AC-B1-1：每项 ≤ 30 字） */
    private int valueNameMax = 30;

    /** 订阅名称长度上限（列表展示用） */
    private int subNameMax = 64;

    /** 单部门分类维度上限（AC-B1-2） */
    private int maxDimsPerDept = 10;

    /** 单个维度下分类值上限（AC-B1-3） */
    private int maxValuesPerDim = 50;

    /** 周期订阅数量上限/人（AC-B4-9） */
    private int maxPeriodPerUser = 20;

    /** 报告保留期数/人（AC-B5-7） */
    private int reportRetain = 12;

    /** 报告生成失败自动重试次数（AC-B5-5） */
    private int reportRetry = 3;

    /** 默认推送时点，日/周/月订阅的初始值（AC-B5-1） */
    private String defaultPushTime = "08:00";

    /** 报告正文链接的前端基地址（AC-B5-2 指向资料汇总处详情；留空则输出相对路径） */
    private String linkBaseUrl = "";

    /** 周期报告生成是否启用（关闭后 ReportScheduleJob 不生成，便于测试/灰度） */
    private boolean scheduleEnabled = true;

    /** 邮件推送是否启用（AC-B5 渠道：站内 + 邮件）；未配置 SMTP 时保持关闭, 仅站内通知 */
    private boolean mailEnabled = false;

    /** 邮件正文/图标所示的系统名，用于邮件主题前缀 */
    private String mailFromName = "恒剑光电 PLM";
}
