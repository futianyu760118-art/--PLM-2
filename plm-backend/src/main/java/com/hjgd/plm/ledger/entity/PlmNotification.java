package com.hjgd.plm.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hjgd.plm.common.BaseEntity;
import lombok.Data;

/**
 * 站内通知（AC-A4-7 ~ A4-11）。
 *
 * <p>{@code aggKey} = 用户 + 类型 + 对象 + 窗口起点，同一窗口内多次变更合并为一行，
 * 明细追加进 {@code contentJson}（AC-A4-10）。
 */
@Data
@TableName("plm_notification")
public class PlmNotification extends BaseEntity {

    private Long userId;
    /** LEDGER_CREATED / LEDGER_CHANGED / LEDGER_DELETED / LEDGER_LINK / OBJ_INVALID / SUB_ITEM / SUB_REPORT */
    private String notifyType;
    private String title;
    /** JSON：{objectNo, objectName, operator, time, link, changes:[{field,before,after}]} */
    private String contentJson;
    private String objectType;
    private String objectId;
    /** 能力 B 报告推送预留 */
    private Long reportId;
    /** 0 未读 1 已读 */
    private Integer readStatus;
    private String aggKey;
}
