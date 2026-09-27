package com.hjgd.plm.sync.service;

import com.hjgd.plm.sync.dto.InboundRequest;
import com.hjgd.plm.sync.entity.SyncConflict;

import java.util.List;
import java.util.Map;

/** M04 同步中枢服务 (双主双向同步) */
public interface SyncService {

    /** 接收对端变更 */
    Map<String, Object> inbound(InboundRequest req);

    /** 冲突列表 */
    List<SyncConflict> listConflicts(String resolution);

    /** 人工裁决冲突 */
    void resolveConflict(Long id, String resolution, String note, String resolvedBy);

    /** 出站待推送事件 (复用 plm_domain_event) */
    List<Map<String, Object>> outboundPending(int limit);

    /** 对端确认消费 */
    void ackOutbound(List<String> eventIds);

    /** 触发对账 (按对象类型, null 表示全部) */
    Map<String, Object> reconcile(String objectType);

    /** 概览统计 */
    Map<String, Object> stats();
}
