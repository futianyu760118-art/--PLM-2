package com.hjgd.plm.ledger.service;

import java.util.List;
import java.util.Map;

/**
 * 「信息资料」双向同步（AC-A3）。
 *
 * <p>台账 → 各模块方向：模块/表单侧不落第二份数据，直接按 {@code plm_ledger_link}
 * 引用查询台账行（{@code GET /ledger/by-object}），因此任何台账侧写入对模块侧即时可见，
 * 「≤1 分钟一致」由结构保证，不需要定时任务（AC-A3-1/A3-2/A3-5/A3-9）。
 *
 * <p>模块 → 台账方向：模块侧编辑回写本接口，按字段合并进台账行，并遵守
 * 后写优先（LWW）与「通知被覆盖方」（AC-A3-7）。
 *
 * <p>同步失败按 {@code MAX_RETRY} 次重试，最终失败落 {@code sys_operation_log}
 * 失败记录并抛错，调用方可据返回值/异常决定是否再次提交（AC-A3-8）。
 */
public interface LedgerSyncService {

    /**
     * 模块/表单侧回写台账字段。
     *
     * @param objectType   目标对象类型（PROJECT/…）
     * @param objectId     目标业务键
     * @param changes      字段名 → 新值；仅接受台账字段（name/type/ownerId/occurDate/version/deptId/status/remark）
     * @param sourceSystem 来源系统标识，写入变更来源便于溯源
     */
    SyncResult applyFromModule(String objectType, String objectId,
                               Map<String, Object> changes, String sourceSystem);

    /**
     * @param ledgerId 命中的台账 ID，未命中为 null
     * @param ledgerNo 命中的证据编号，未命中为 null
     * @param applied  实际改动的字段数
     * @param fields   实际改动的字段名
     * @param conflict 是否覆盖了另一方（台账侧）刚写入的值
     */
    record SyncResult(Long ledgerId, String ledgerNo, int applied, List<String> fields, boolean conflict) {
        public static SyncResult none() {
            return new SyncResult(null, null, 0, List.of(), false);
        }
    }
}
