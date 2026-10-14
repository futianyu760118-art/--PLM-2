package com.hjgd.plm.subscription.dto;

import lombok.Data;

import java.util.List;

/** 报告详情：报告头 + 资料明细 + 渠道投递记录（AC-B5-6） */
@Data
public class ReportDetailVO {

    private ReportVO report;
    private List<RepositoryItemVO> items;
    private List<DeliveryLogVO> deliveries;

    @Data
    public static class DeliveryLogVO {
        private String channel;
        private String result;
        private String target;
        private String error;
        private java.time.LocalDateTime deliveredAt;
    }
}
