package com.hjgd.plm.ledger.service;

import java.util.List;
import java.util.Set;

/**
 * 关联对象解析器（架构 §4.2）。
 *
 * <p>把 {@code object_type} 注册表映射到真实业务表，用于：
 * 关联时的对象有效性校验与前缀回填、对象被删/作废时把链接置为失效（AC-A4-5）、
 * 以及给前端提供对象选择项。未列入注册表的类型不参与同步（AC-A3）。
 */
public interface LedgerObjectResolver {

    /** 支持的对象类型（默认六类 + 评审） */
    Set<String> supportedTypes();

    /**
     * 解析单个对象。
     *
     * @return 对象不存在（或已软删）时 {@code valid=false}，原因为 {@code DELETED}
     */
    ObjectRef resolve(String objectType, String objectId);

    /** 按关键字检索候选对象，供录入界面选择（AC-A2-2 关联对象必填） */
    List<ObjectRef> search(String objectType, String keyword, int limit);

    /**
     * @param objectId      业务键（写入 plm_ledger_link.object_id）
     * @param objectNo      单据编号
     * @param objectName    展示名
     * @param valid         对象当前是否有效
     * @param invalidReason 失效原因 DELETED / OBSOLETE，valid=true 时为 null
     */
    record ObjectRef(String objectId, String objectNo, String objectName,
                     boolean valid, String invalidReason) {
    }
}
