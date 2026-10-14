package com.hjgd.plm.ledger.service;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.ledger.dto.BatchResultVO;
import com.hjgd.plm.ledger.dto.LedgerDetailVO;
import com.hjgd.plm.ledger.dto.LedgerQuery;
import com.hjgd.plm.ledger.dto.LedgerRefVO;
import com.hjgd.plm.ledger.dto.LedgerRequest;
import com.hjgd.plm.ledger.dto.LinkRequest;
import com.hjgd.plm.ledger.entity.PlmLedger;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 证据台账服务（能力线 A）。
 *
 * <p>「证据台账」与「资料汇总处」共用本服务的查询与写入：两侧是同一行的两个视图，
 * 不做数据复制，因此 AC-A3-3「逐字段恒一致」由结构保证。
 */
public interface LedgerService {

    // ---------- 查询 ----------

    /** 台账/汇总处 组合筛选分页（AC-A5-1 ~ A5-5） */
    PageResult<PlmLedger> page(LedgerQuery query);

    /** 证据详情（附件 + 关联 + 跳转路径） */
    LedgerDetailVO detail(Long id);

    /** 模块/表单侧证据区：按关联对象取引用条目（AC-A3-1/A3-5/A3-6） */
    List<LedgerRefVO> byObject(String objectType, String objectId);

    /** 录入表单的下拉项：证据类型/状态/对象类型/关联对象候选来源 */
    Map<String, Object> formOptions();

    // ---------- 录入（AC-A2，单界面） ----------

    LedgerDetailVO create(LedgerRequest request);

    LedgerDetailVO update(Long id, LedgerRequest request);

    /** 批量上传：N 个文件生成 1 条台账（AC-A5-6） */
    BatchResultVO batchUpload(LedgerRequest request, MultipartFile[] files);

    void delete(Long id);

    // ---------- 附件 ----------

    LedgerDetailVO addAttachments(Long id, List<Long> fileIds);

    void removeAttachment(Long id, Long attachmentId);

    // ---------- 关联 ----------

    LedgerDetailVO addLink(Long id, LinkRequest request);

    /** 解除关联：仅解除引用，不删除台账（AC-A4-6） */
    void removeLink(Long id, Long linkId);

    /** 批量关联：N 条台账关联到 1 个对象（AC-A5-7） */
    BatchResultVO batchLink(List<Long> ledgerIds, LinkRequest request);

    /** 关联对象检索（前端下拉） */
    List<LedgerObjectResolver.ObjectRef> searchObjects(String objectType, String keyword, int limit);

    /**
     * 责任人候选（AC-A2-2 责任人必填需可下拉选择）。
     *
     * <p>独立于 ADMIN 专属的 {@code /system/user/page}：录入人需要选责任人但无用户管理权限，
     * 故只暴露 id/姓名/部门，不含账号安全字段。
     */
    List<Map<String, Object>> searchOwners(String keyword, int limit);

    /** 按证据编号取完整变更轨迹（AC-A6-7） */
    Map<String, Object> trail(String ledgerNo);

    /** 校验当前登录用户是否有权读取该证据（供下载/预览做二次校验） */
    void requireReadable(Long id);

    /**
     * 校验当前登录用户是否有权修改该证据。
     *
     * <p>给「对资料做附属写操作」的调用方使用（如能力线 B 给资料打分类标签）：
     * 这类写不改台账字段，但同样属于对证据的修改，必须走同一套写权限判定，
     * 否则会出现「改不了证据却改得了证据的标签」的越权口子。
     */
    void requireWritable(Long id);
}
