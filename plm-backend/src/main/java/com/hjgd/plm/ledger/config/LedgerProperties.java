package com.hjgd.plm.ledger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 证据台账（能力线 A）边界值配置。
 *
 * <p>默认值即 PAND-198 的 AC 取值，全部可配置化（架构 §8）：
 * 上限类阈值集中在服务层做计数/校验，避免散落在各 controller。
 */
@Data
@Component
@ConfigurationProperties(prefix = "plm.ledger")
public class LedgerProperties {

    /** 单条证据附件数上限（AC-A1-4）；批量上传单次可选文件数上限同此值（AC-A5-6） */
    private int maxAttachments = 20;

    /** 单文件大小上限 MB（AC-A1-5，等于上限可通过） */
    private long maxFileSizeMb = 200;

    /** 附件格式白名单（AC-A1-6），小写含点，如 .pdf */
    private List<String> allowExts = List.of(".pdf", ".doc", ".docx", ".xls", ".xlsx",
            ".ppt", ".pptx", ".jpg", ".jpeg", ".png", ".zip", ".rar", ".dwg", ".dxf");

    /** 单条证据关联对象数上限（AC-A4-1/A4-4） */
    private int maxLinks = 20;

    /** 批量关联单次可选证据条数上限（AC-A5-7） */
    private int batchLinkMax = 100;

    /** 证据名称最大长度（AC-A2-3） */
    private int nameMax = 100;

    /** 备注最大长度（AC-A2-3） */
    private int remarkMax = 500;

    /** 版本最大长度（AC-A2-3） */
    private int versionMax = 20;

    /** 变更通知聚合窗口（分钟，AC-A4-10） */
    private int notifyAggregateMinutes = 5;

    /** 操作日志保留天数（AC-A6-6，默认 3 年） */
    private int logRetainDays = 1095;
}
