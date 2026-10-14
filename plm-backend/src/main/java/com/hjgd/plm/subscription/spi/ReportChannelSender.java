package com.hjgd.plm.subscription.spi;

import com.hjgd.plm.subscription.dto.RepositoryItemVO;
import com.hjgd.plm.subscription.entity.PlmReport;

import java.util.List;

/**
 * 报告投递渠道（AC-B5：站内通知 + 邮件）。
 *
 * <p>用返回值而不是异常表达投递结果：三种结果（成功/失败/跳过）都要落投递日志
 * （AC-B5-6），异常只适合表达「失败」，而「渠道没配置所以跳过」不是失败，
 * 用异常表示会逼着调用方用异常类型去区分结果。
 */
public interface ReportChannelSender {

    String CHANNEL_INBOX = "INBOX";
    String CHANNEL_EMAIL = "EMAIL";

    String RESULT_SUCCESS = "SUCCESS";
    String RESULT_FAILED = "FAILED";
    String RESULT_SKIPPED = "SKIPPED";

    /** 渠道标识，落 plm_report_delivery_log.channel */
    String channel();

    Delivery send(PlmReport report, List<RepositoryItemVO> items, byte[] excel);

    record Delivery(String result, String target, String error) {

        public static Delivery success(String target) {
            return new Delivery(RESULT_SUCCESS, target, null);
        }

        public static Delivery failed(String target, String error) {
            return new Delivery(RESULT_FAILED, target, error);
        }

        /** 渠道未启用/未配置：不是失败，但必须留痕，否则「报告发出去了吗」无从回答 */
        public static Delivery skipped(String target, String reason) {
            return new Delivery(RESULT_SKIPPED, target, reason);
        }
    }
}
