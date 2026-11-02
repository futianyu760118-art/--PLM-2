package com.hjgd.plm.m04.document.service;

import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.m04.document.dto.DocumentAclDTO;
import com.hjgd.plm.m04.document.dto.DocumentDTO;
import com.hjgd.plm.m04.document.dto.DocumentQueryDTO;
import com.hjgd.plm.m04.document.dto.DocumentVersionDTO;
import com.hjgd.plm.m04.document.entity.PlmDocument;
import com.hjgd.plm.m04.document.entity.PlmDocumentAcl;
import com.hjgd.plm.m04.document.entity.PlmDocumentVersion;

import java.util.List;
import java.util.Map;

/**
 * 文档中心（T05/T06/T07）。
 *
 * <p>核心不变量：
 * <ol>
 *   <li>同一文档同一时刻只有一个 is_current 版本</li>
 *   <li>被检出（checked_out_by 非空）的文档只有锁定人可写</li>
 *   <li>只读区（发布/归档/报废）禁止改元数据，需先退回工作区</li>
 *   <li>版本记录只增不改，回退通过"复制为新版本"实现</li>
 * </ol>
 */
public interface DocumentService {

    PageResult<PlmDocument> page(DocumentQueryDTO query);

    PlmDocument getById(Long id);

    PlmDocument create(DocumentDTO dto);

    PlmDocument update(DocumentDTO dto);

    void delete(Long id);

    /** 五区流转，非法流转直接报错并列出可去状态 */
    PlmDocument changeState(Long id, String targetState, String remark);

    List<PlmDocumentVersion> listVersions(Long docId);

    PlmDocumentVersion currentVersion(Long docId);

    PlmDocumentVersion addVersion(Long docId, DocumentVersionDTO dto);

    PlmDocument checkout(Long id);

    /** 检入：上传新文件 + 解锁，等价于 addVersion + 清锁 */
    PlmDocument checkin(Long id, DocumentVersionDTO dto);

    PlmDocument cancelCheckout(Long id);

    /** 把历史版本复制为新的当前版本（不改历史行） */
    PlmDocumentVersion restoreVersion(Long docId, Long versionId, String changeNote);

    List<PlmDocumentAcl> listAcl(Long docId);

    void grantAcl(Long docId, List<DocumentAclDTO> acls);

    void revokeAcl(Long docId, Long aclId);

    List<PlmDocument> listByPart(String partNo);

    List<PlmDocument> listByProject(String projectNo);

    /** 五区数量统计，给前端做首页卡片 */
    Map<String, Object> stats();
}
