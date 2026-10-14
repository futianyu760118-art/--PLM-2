package com.hjgd.plm.subscription.spi;

import com.hjgd.plm.subscription.config.SubscriptionProperties;
import com.hjgd.plm.subscription.dto.RepositoryItemVO;
import com.hjgd.plm.subscription.entity.PlmReport;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 邮件渠道（AC-B5 渠道之一）。
 *
 * <p>用 {@link ObjectProvider} 拿 {@link JavaMailSender}：没有配置 {@code spring.mail.host} 时
 * Spring Boot 不装配这个 bean，此时报告降级为仅站内通知，并把该渠道记为 {@code SKIPPED}——
 * 环境没配 SMTP 是部署现状，不是报告生成失败，不该让整份报告标红（架构 §9 的降级约定）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailReportSender implements ReportChannelSender {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final SubscriptionProperties properties;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public String channel() {
        return CHANNEL_EMAIL;
    }

    @Override
    public Delivery send(PlmReport report, List<RepositoryItemVO> items, byte[] excel) {
        if (!properties.isMailEnabled()) {
            return Delivery.skipped("邮件", "邮件推送未启用（plm.subscription.mail-enabled=false）");
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            return Delivery.skipped("邮件", "未配置 spring.mail.host，无可用邮件发送器");
        }
        String address = emailOf(report.getUserId());
        if (!StringUtils.hasText(address)) {
            return Delivery.skipped("邮件", "订阅人未维护邮箱");
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.getMailFromName() + " <" + fromAddress(sender) + ">");
            helper.setTo(address);
            helper.setSubject("【" + nz(report.getSubName()) + "】资料更新报告 " + nz(report.getReportNo()));
            helper.setText(nz(report.getHtmlBody()), true);
            if (excel != null && excel.length > 0) {
                helper.addAttachment(report.getReportNo() + ".xlsx",
                        new org.springframework.core.io.ByteArrayResource(excel));
            }
            sender.send(message);
            return Delivery.success(address);
        } catch (Exception e) {
            log.warn("报告邮件推送失败 reportNo={} to={}: {}", report.getReportNo(), address, e.getMessage());
            return Delivery.failed(address, e.getMessage());
        }
    }

    /** 发件人地址优先用 spring.mail.username，未配置时退化为占位发件人 */
    private String fromAddress(JavaMailSender sender) {
        try {
            if (sender instanceof org.springframework.mail.javamail.JavaMailSenderImpl impl) {
                String username = impl.getUsername();
                if (StringUtils.hasText(username)) {
                    return username;
                }
            }
        } catch (Exception ignored) {
            // 取不到就退化为占位发件人，SMTP 服务器接手时会报出真实原因
        }
        return "no-reply@hjgd.local";
    }

    private String emailOf(Long userId) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT email FROM sys_user WHERE id = ?", String.class, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
