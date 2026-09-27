package com.hjgd.plm.sync.applier;

import java.util.Map;

/**
 * 同步对象业务应用器。每个可同步对象提供一个实现, 负责把入站 payload 落到业务表。
 * 见 docs/m04-rd-autonomous-migration-sync-plan.md
 */
public interface SyncApplier {
    /** 支持的 object_type, 如 PROJECT_NODE */
    String objectType();

    /** 应用变更 (在事务内); 返回是否成功 */
    void apply(String externalKey, String operation, Map<String, Object> payload);
}
