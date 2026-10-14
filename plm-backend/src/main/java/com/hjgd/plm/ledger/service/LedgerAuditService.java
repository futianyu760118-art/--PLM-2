package com.hjgd.plm.ledger.service;

import java.util.List;
import java.util.Map;

/**
 * 台账操作留痕（AC-A6-2 ~ AC-A6-7）。
 *
 * <p>不直接用 {@code @OperationLog} 注解：AC-A6-4 要求日志含「修改前值 / 修改后值」，
 * 注解在方法进入前取 SpEL，拿不到写库后的结果，因此这里在事务内显式写
 * {@code sys_operation_log}（操作人/时间/类型/对象/前后值/IP），
 * 同时把逐字段差异写 {@code plm_entity_history}（object_type=LEDGER），
 * 支撑 AC-A6-7「按证据编号查看完整变更轨迹」。
 */
public interface LedgerAuditService {

    /**
     * 记一次台账写操作。
     *
     * @param operation 操作类型：CREATE / UPDATE / DELETE / DOWNLOAD / LINK / UNLINK
     * @param ledgerNo  证据编号，写入 part_no 供按编号检索
     * @param before    修改前值（Map 或 null），落 params.before
     * @param after     修改后值（Map 或 null），落 params.after
     */
    void log(String operation, String ledgerNo, Object before, Object after);

    /** 记一次被拒绝的越权尝试（AC-A6-2/A6-3） */
    void logDenied(String operation, String ledgerNo, String reason);

    /** 记一次失败操作（AC-A3-8 同步失败日志） */
    void logFailure(String operation, String ledgerNo, String errorMsg);

    /** 按证据编号取完整变更轨迹：{history: [...], logs: [...]}（AC-A6-7） */
    Map<String, Object> trail(String ledgerNo);

    /** 历史变更的原始列表，供内部与测试使用 */
    List<Map<String, Object>> history(String ledgerNo);
}
