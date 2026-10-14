package com.hjgd.plm.subscription.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订阅报告（AC-B5）。
 *
 * <p>{@code contentJson} 存去重后的资料条目清单，{@code htmlBody} 为可阅读的 HTML 清单，
 * {@code excelFileId} 指向 {@code plm_file} 中的 xlsx 明细（只存引用，不复制文件本体）。
 */
@Data
@TableName("plm_report")
public class PlmReport extends BaseEntity {

    private String reportNo;
    private Long subscriptionId;
    private Long userId;
    /** ITEM / PERIOD */
    private String type;
    /** 订阅名称快照：订阅被删除后报告仍可回溯（AC-B4-8） */
    private String subName;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    /** 报告内资料条数（去重后，AC-B4-4） */
    private Integer itemCount;
    /** JSON 资料条目清单，每条含指向资料汇总处的链接（AC-B5-2） */
    private String contentJson;
    private String htmlBody;
    private Long excelFileId;
    /** PENDING / GENERATED / PUSHED / FAILED */
    private String status;
    private Integer retryCount;
    private String error;
    private LocalDateTime generatedAt;
    private LocalDateTime pushedAt;
}
