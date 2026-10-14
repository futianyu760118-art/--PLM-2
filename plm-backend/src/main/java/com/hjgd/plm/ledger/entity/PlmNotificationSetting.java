package com.hjgd.plm.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import lombok.Data;

/**
 * 按通知类型的订阅开关（AC-A4-11）。未配置的类型视为开启。
 */
@Data
@TableName("plm_notification_setting")
public class PlmNotificationSetting extends BaseEntity {

    private Long userId;
    private String notifyType;
    /** 1 开启 0 关闭 */
    private Integer enabled;
}
