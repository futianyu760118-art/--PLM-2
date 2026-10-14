package com.hjgd.plm.ledger.spi;

/**
 * 台账变更监听扩展点（能力线 B 通过实现本接口接入 A 的写路径）。
 *
 * <p>为什么用事件而不是轮询：附件追加/移除不触碰 {@code plm_ledger} 行，
 * 只扫 {@code updated_at} 的轮询永远发现不了「附件更新」这类变更（AC-B3-2），
 * 也拿不到「变更前后值」（AC-B3-3）——前后值只在写下去的当刻存在。
 *
 * <p>约定：实现方必须自行吞掉异常并记日志，不得让异常冒泡——
 * 监听失败不能反过来把台账写入一起回滚。
 */
public interface LedgerChangeListener {

    void onLedgerChange(LedgerChangeEvent event);
}
