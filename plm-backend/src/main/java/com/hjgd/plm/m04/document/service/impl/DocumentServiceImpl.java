package com.hjgd.plm.m04.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjgd.plm.archive.entity.ArchiveTreeNode;
import com.hjgd.plm.archive.mapper.ArchiveTreeNodeMapper;
import com.hjgd.plm.archive.service.ArchiveTreeService;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.m04.codegen.service.CodeGenService;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.m04.document.dto.DocumentAclDTO;
import com.hjgd.plm.m04.document.dto.DocumentDTO;
import com.hjgd.plm.m04.document.dto.DocumentQueryDTO;
import com.hjgd.plm.m04.document.dto.DocumentVersionDTO;
import com.hjgd.plm.m04.document.entity.PlmDocument;
import com.hjgd.plm.m04.document.entity.PlmDocumentAcl;
import com.hjgd.plm.m04.document.entity.PlmDocumentVersion;
import com.hjgd.plm.m04.document.enums.DocLifecycleState;
import com.hjgd.plm.m04.document.enums.DocType;
import com.hjgd.plm.m04.document.mapper.PlmDocumentAclMapper;
import com.hjgd.plm.m04.document.mapper.PlmDocumentMapper;
import com.hjgd.plm.m04.document.mapper.PlmDocumentVersionMapper;
import com.hjgd.plm.m04.document.service.DocumentService;
import com.hjgd.plm.event.service.DomainEventService;
import com.hjgd.plm.file.entity.PlmFile;
import com.hjgd.plm.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final PlmDocumentMapper documentMapper;
    private final PlmDocumentVersionMapper versionMapper;
    private final PlmDocumentAclMapper aclMapper;
    private final CodeGenService codeGenService;
    private final FileService fileService;
    private final DomainEventService domainEventService;
    private final ArchiveTreeService archiveTreeService;
    private final ArchiveTreeNodeMapper archiveTreeNodeMapper;

    // ------------------------------------------------------------------ 查询

    @Override
    public PageResult<PlmDocument> page(DocumentQueryDTO query) {
        LambdaQueryWrapper<PlmDocument> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(PlmDocument::getDocNo, kw).or().like(PlmDocument::getTitle, kw));
        }
        wrapper.eq(query.getDocType() != null, PlmDocument::getDocType, query.getDocType())
                .eq(query.getLifecycleState() != null, PlmDocument::getLifecycleState, query.getLifecycleState())
                .eq(StringUtils.hasText(query.getPartNo()), PlmDocument::getPartNo, query.getPartNo())
                .eq(StringUtils.hasText(query.getProjectNo()), PlmDocument::getProjectNo, query.getProjectNo())
                .eq(StringUtils.hasText(query.getOwner()), PlmDocument::getOwner, query.getOwner())
                .isNotNull(Boolean.TRUE.equals(query.getLocked()), PlmDocument::getCheckedOutBy)
                .orderByDesc(PlmDocument::getCreatedAt);
        Page<PlmDocument> page = documentMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        page.getRecords().forEach(this::assertReadable);
        return PageResult.of(page);
    }

    @Override
    public PlmDocument getById(Long id) {
        PlmDocument doc = loadDoc(id);
        assertReadable(doc);
        return doc;
    }

    @Override
    public List<PlmDocument> listByPart(String partNo) {
        List<PlmDocument> list = documentMapper.selectList(new LambdaQueryWrapper<PlmDocument>()
                .eq(PlmDocument::getPartNo, partNo)
                .orderByDesc(PlmDocument::getCreatedAt));
        return filterReadable(list);
    }

    @Override
    public List<PlmDocument> listByProject(String projectNo) {
        List<PlmDocument> list = documentMapper.selectList(new LambdaQueryWrapper<PlmDocument>()
                .eq(PlmDocument::getProjectNo, projectNo)
                .orderByDesc(PlmDocument::getCreatedAt));
        return filterReadable(list);
    }

    @Override
    public Map<String, Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        long total = 0;
        for (DocLifecycleState st : DocLifecycleState.values()) {
            long c = documentMapper.selectCount(new LambdaQueryWrapper<PlmDocument>()
                    .eq(PlmDocument::getLifecycleState, st));
            out.put(st.name(), c);
            total += c;
        }
        long locked = documentMapper.selectCount(new LambdaQueryWrapper<PlmDocument>()
                .isNotNull(PlmDocument::getCheckedOutBy));
        out.put("TOTAL", total);
        out.put("LOCKED", locked);
        return out;
    }

    // ------------------------------------------------------------------ 写

    @Override
    @Transactional
    public PlmDocument create(DocumentDTO dto) {
        String docNo = dto.getDocNo();
        if (StringUtils.hasText(docNo)) {
            if (!SecurityUtils.hasRole("ADMIN") && !SecurityUtils.hasPermission("document:override-code")) {
                throw new BusinessException("禁止手工指定文档编号，请留空由系统自动生成");
            }
            Long dup = documentMapper.selectCount(new LambdaQueryWrapper<PlmDocument>()
                    .eq(PlmDocument::getDocNo, docNo.trim()));
            if (dup != null && dup > 0) {
                throw new BusinessException("文档编号已存在: " + docNo);
            }
            docNo = docNo.trim();
        } else {
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("docType", dto.getDocType() == null ? DocType.OTHER.name() : dto.getDocType().name());
            ctx.put("partNo", dto.getPartNo());
            docNo = codeGenService.allocate("DOC", ctx, null, "UI");
        }

        PlmDocument doc = new PlmDocument();
        BeanUtils.copyProperties(dto, doc);
        doc.setId(null);
        doc.setDocNo(docNo);
        doc.setDocType(dto.getDocType() == null ? DocType.OTHER : dto.getDocType());
        doc.setLifecycleState(DocLifecycleState.WORKING);
        doc.setCurrentVersion("V1.0");
        doc.setSecretLevel(normalizeSecretLevel(dto.getSecretLevel()));
        doc.setOwner(StringUtils.hasText(dto.getOwner()) ? dto.getOwner().trim() : SecurityUtils.getCurrentUsername());
        doc.setCreatedBy(SecurityUtils.getCurrentRealName());
        documentMapper.insert(doc);

        publish("document.created", doc, Map.<String, Object>of("title", nullSafe(doc.getTitle())));
        return doc;
    }

    @Override
    @Transactional
    public PlmDocument update(DocumentDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "缺少文档 id");
        }
        PlmDocument exist = loadDoc(dto.getId());
        assertWritable(exist, false);

        if (exist.getLifecycleState().isReadOnly()) {
            boolean onlyRemark = !StringUtils.hasText(dto.getTitle())
                    || dto.getTitle().equals(exist.getTitle());
            if (!onlyRemark) {
                throw new BusinessException("文档处于 " + exist.getLifecycleState()
                        + " 只读区，元数据禁止直接修改，请先退回工作区");
            }
        }

        if (StringUtils.hasText(dto.getTitle())) {
            exist.setTitle(dto.getTitle().trim());
        }
        if (dto.getDocType() != null) {
            exist.setDocType(dto.getDocType());
        }
        if (dto.getPartNo() != null) {
            exist.setPartNo(dto.getPartNo().trim());
        }
        if (dto.getProjectNo() != null) {
            exist.setProjectNo(dto.getProjectNo().trim());
        }
        if (dto.getArchiveNodeId() != null) {
            exist.setArchiveNodeId(dto.getArchiveNodeId());
        }
        if (StringUtils.hasText(dto.getSecretLevel())) {
            exist.setSecretLevel(normalizeSecretLevel(dto.getSecretLevel()));
        }
        if (StringUtils.hasText(dto.getOwner())) {
            exist.setOwner(dto.getOwner().trim());
        }
        if (dto.getRemark() != null) {
            exist.setRemark(dto.getRemark());
        }
        documentMapper.updateById(exist);
        publish("document.updated", exist, Map.<String, Object>of("title", nullSafe(exist.getTitle())));
        return exist;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        PlmDocument doc = loadDoc(id);
        if (!SecurityUtils.hasRole("ADMIN") && !SecurityUtils.hasPermission("document:delete")) {
            throw new BusinessException("无删除文档权限");
        }
        if (doc.getLifecycleState() != DocLifecycleState.WORKING
                && doc.getLifecycleState() != DocLifecycleState.RECYCLE) {
            throw new BusinessException("仅工作区或回收区的文档可删除，当前状态: " + doc.getLifecycleState());
        }
        if (StringUtils.hasText(doc.getCheckedOutBy())) {
            throw new BusinessException("文档被 " + doc.getCheckedOutBy() + " 锁定中，无法删除");
        }
        documentMapper.deleteById(id);
        publish("document.deleted", doc, Map.of());
    }

    @Override
    @Transactional
    public PlmDocument changeState(Long id, String targetState, String remark) {
        PlmDocument doc = loadDoc(id);
        DocLifecycleState target = DocLifecycleState.parse(targetState);
        if (target == null) {
            throw new BusinessException("未知的文档状态: " + targetState);
        }
        DocLifecycleState current = doc.getLifecycleState();
        if (!current.canMoveTo(target)) {
            throw new BusinessException("不允许从 " + current + " 流转到 " + target
                    + "，当前可去状态: " + current.nextStates());
        }
        if (StringUtils.hasText(doc.getCheckedOutBy())) {
            throw new BusinessException("文档被 " + doc.getCheckedOutBy() + " 锁定中，请先检入或取消检出");
        }
        if (target == DocLifecycleState.RELEASED) {
            Long cnt = versionMapper.selectCount(new LambdaQueryWrapper<PlmDocumentVersion>()
                    .eq(PlmDocumentVersion::getDocId, id));
            if (cnt == null || cnt == 0) {
                throw new BusinessException("文档没有任何版本，不能发布");
            }
            versionMapper.update(null, new LambdaUpdateWrapper<PlmDocumentVersion>()
                    .eq(PlmDocumentVersion::getDocId, id)
                    .eq(PlmDocumentVersion::getIsCurrent, 1)
                    .set(PlmDocumentVersion::getState, DocLifecycleState.RELEASED.name())
                    .set(PlmDocumentVersion::getApprovedBy, SecurityUtils.getCurrentRealName())
                    .set(PlmDocumentVersion::getApprovedAt, LocalDateTime.now()));
        }
        doc.setLifecycleState(target);
        if (StringUtils.hasText(remark)) {
            doc.setRemark(remark);
        }
        documentMapper.updateById(doc);
        publish("document.state.changed", doc,
                Map.<String, Object>of("from", current.name(), "to", target.name()));
        return doc;
    }

    // ------------------------------------------------------------------ 版本

    @Override
    public List<PlmDocumentVersion> listVersions(Long docId) {
        PlmDocument doc = loadDoc(docId);
        assertReadable(doc);
        return versionMapper.selectList(new LambdaQueryWrapper<PlmDocumentVersion>()
                .eq(PlmDocumentVersion::getDocId, docId)
                .orderByDesc(PlmDocumentVersion::getCreatedAt)
                .orderByDesc(PlmDocumentVersion::getId));
    }

    @Override
    public PlmDocumentVersion currentVersion(Long docId) {
        PlmDocument doc = loadDoc(docId);
        assertReadable(doc);
        return versionMapper.selectOne(new LambdaQueryWrapper<PlmDocumentVersion>()
                .eq(PlmDocumentVersion::getDocId, docId)
                .eq(PlmDocumentVersion::getIsCurrent, 1)
                .orderByDesc(PlmDocumentVersion::getId)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional
    public PlmDocumentVersion addVersion(Long docId, DocumentVersionDTO dto) {
        PlmDocument doc = loadDoc(docId);
        assertWritable(doc, Boolean.TRUE.equals(dto.getForce()));
        return doAddVersion(doc, dto);
    }

    @Override
    @Transactional
    public PlmDocument checkout(Long id) {
        PlmDocument doc = loadDoc(id);
        if (doc.getLifecycleState().isReadOnly()) {
            throw new BusinessException("文档处于 " + doc.getLifecycleState()
                    + " 只读区，不能检出，请先退回工作区");
        }
        String me = SecurityUtils.getCurrentUsername();
        if (StringUtils.hasText(doc.getCheckedOutBy())) {
            if (doc.getCheckedOutBy().equals(me)) {
                throw new BusinessException("你已经持有该文档的锁");
            }
            throw new BusinessException("文档已被 " + doc.getCheckedOutBy() + " 检出（"
                    + doc.getCheckedOutAt() + "），请等待其检入或取消检出");
        }
        assertWritable(doc, true);
        doc.setCheckedOutBy(me);
        doc.setCheckedOutAt(LocalDateTime.now());
        documentMapper.updateById(doc);
        return doc;
    }

    @Override
    @Transactional
    public PlmDocument checkin(Long id, DocumentVersionDTO dto) {
        if (dto.getFileId() == null) {
            throw new BusinessException("检入必须上传新版本文件（fileId 为空）");
        }
        PlmDocument doc = loadDoc(id);
        if (!StringUtils.hasText(doc.getCheckedOutBy())) {
            throw new BusinessException("文档未检出，请先检出再检入");
        }
        if (!isSelf(doc.getCheckedOutBy()) && !SecurityUtils.hasRole("ADMIN")) {
            throw new BusinessException("文档被 " + doc.getCheckedOutBy() + " 锁定，你无法检入");
        }
        doAddVersion(doc, dto);
        doc.setCheckedOutBy(null);
        doc.setCheckedOutAt(null);
        documentMapper.updateById(doc);
        return doc;
    }

    @Override
    @Transactional
    public PlmDocument cancelCheckout(Long id) {
        PlmDocument doc = loadDoc(id);
        if (!StringUtils.hasText(doc.getCheckedOutBy())) {
            return doc;
        }
        if (!isSelf(doc.getCheckedOutBy()) && !SecurityUtils.hasRole("ADMIN")) {
            throw new BusinessException("文档被 " + doc.getCheckedOutBy() + " 锁定，只有本人或管理员可取消");
        }
        doc.setCheckedOutBy(null);
        doc.setCheckedOutAt(null);
        documentMapper.updateById(doc);
        publish("document.checkout.cancelled", doc, Map.of());
        return doc;
    }

    @Override
    @Transactional
    public PlmDocumentVersion restoreVersion(Long docId, Long versionId, String changeNote) {
        PlmDocument doc = loadDoc(docId);
        assertWritable(doc, false);
        PlmDocumentVersion src = versionMapper.selectById(versionId);
        if (src == null || !docId.equals(src.getDocId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "版本记录不存在或不属于该文档");
        }
        DocumentVersionDTO dto = new DocumentVersionDTO();
        dto.setFileId(src.getFileId());
        dto.setChangeNote(StringUtils.hasText(changeNote)
                ? changeNote
                : "回退自 " + src.getVersion() + (src.getRevision() == null ? "" : "." + src.getRevision()));
        dto.setForce(Boolean.TRUE);
        return doAddVersion(doc, dto);
    }

    // ------------------------------------------------------------------ ACL

    @Override
    public List<PlmDocumentAcl> listAcl(Long docId) {
        loadDoc(docId);
        if (!SecurityUtils.hasRole("ADMIN") && !SecurityUtils.hasPermission("document:acl")) {
            throw new BusinessException("无查看文档权限配置的权限");
        }
        return aclMapper.selectList(new LambdaQueryWrapper<PlmDocumentAcl>()
                .eq(PlmDocumentAcl::getDocId, docId)
                .orderByAsc(PlmDocumentAcl::getId));
    }

    @Override
    @Transactional
    public void grantAcl(Long docId, List<DocumentAclDTO> acls) {
        PlmDocument doc = loadDoc(docId);
        if (!SecurityUtils.hasRole("ADMIN") && !isSelf(doc.getOwner())
                && !SecurityUtils.hasPermission("document:acl")) {
            throw new BusinessException("只有文档责任人或有 document:acl 权限者可配置权限");
        }
        if (acls == null || acls.isEmpty()) {
            return;
        }
        for (DocumentAclDTO d : acls) {
            String type = d.getSubjectType().trim().toUpperCase();
            PlmDocumentAcl exist = aclMapper.selectOne(new LambdaQueryWrapper<PlmDocumentAcl>()
                    .eq(PlmDocumentAcl::getDocId, docId)
                    .eq(PlmDocumentAcl::getSubjectType, type)
                    .eq(PlmDocumentAcl::getSubjectId, d.getSubjectId().trim())
                    .last("LIMIT 1"));
            PlmDocumentAcl row = exist == null ? new PlmDocumentAcl() : exist;
            row.setDocId(docId);
            row.setSubjectType(type);
            row.setSubjectId(d.getSubjectId().trim());
            row.setCanRead(flag(d.getCanRead(), 1));
            row.setCanWrite(flag(d.getCanWrite(), 0));
            row.setCanCheckout(flag(d.getCanCheckout(), 0));
            row.setCanDelete(flag(d.getCanDelete(), 0));
            row.setGrantedBy(SecurityUtils.getCurrentUsername());
            if (exist == null) {
                row.setCreatedAt(LocalDateTime.now());
                aclMapper.insert(row);
            } else {
                aclMapper.updateById(row);
            }
        }
    }

    @Override
    @Transactional
    public void revokeAcl(Long docId, Long aclId) {
        PlmDocument doc = loadDoc(docId);
        if (!SecurityUtils.hasRole("ADMIN") && !isSelf(doc.getOwner())
                && !SecurityUtils.hasPermission("document:acl")) {
            throw new BusinessException("只有文档责任人或有 document:acl 权限者可配置权限");
        }
        PlmDocumentAcl row = aclMapper.selectById(aclId);
        if (row == null || !docId.equals(row.getDocId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "权限记录不存在");
        }
        aclMapper.deleteById(aclId);
    }

    // ------------------------------------------------------------------ 内部

    private PlmDocument loadDoc(Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文档 id 不能为空");
        }
        PlmDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文档不存在: " + id);
        }
        return doc;
    }

    /**
     * 权限门：ADMIN / 责任人 / 创建人放行；有 ACL 时按 ACL；
     * 无 ACL 时 SECRET 只对前三者开放，其余内部文档对所有登录用户开放。
     */
    private void assertReadable(PlmDocument doc) {
        if (doc == null) {
            return;
        }
        if (SecurityUtils.hasRole("ADMIN") || isSelf(doc.getOwner()) || isSelf(doc.getCreatedBy())) {
            return;
        }
        List<PlmDocumentAcl> acls = aclMapper.selectList(new LambdaQueryWrapper<PlmDocumentAcl>()
                .eq(PlmDocumentAcl::getDocId, doc.getId()));
        if (acls.isEmpty()) {
            if ("SECRET".equalsIgnoreCase(doc.getSecretLevel())) {
                throw new BusinessException("文档为保密级，未授权访问");
            }
            return;
        }
        boolean read = acls.stream().anyMatch(a -> matched(a) && flag(a.getCanRead(), 0) == 1);
        if (!read) {
            throw new BusinessException("无该文档的查看权限: " + doc.getDocNo());
        }
    }

    /**
     * 写权限门。
     *
     * @param requireLock 为 true 时额外要求"有写权限"（checkout 场景），
     *                    addVersion 场景由调用方另行判断锁定人
     */
    private void assertWritable(PlmDocument doc, boolean requireLock) {
        if (doc.getLifecycleState().isReadOnly() && !SecurityUtils.hasRole("ADMIN")) {
            throw new BusinessException("文档处于 " + doc.getLifecycleState() + " 只读区，禁止修改");
        }
        if (StringUtils.hasText(doc.getCheckedOutBy()) && !isSelf(doc.getCheckedOutBy())
                && !SecurityUtils.hasRole("ADMIN")) {
            throw new BusinessException("文档被 " + doc.getCheckedOutBy() + " 锁定，你无法修改");
        }
        if (SecurityUtils.hasRole("ADMIN") || isSelf(doc.getOwner()) || isSelf(doc.getCreatedBy())) {
            return;
        }
        List<PlmDocumentAcl> acls = aclMapper.selectList(new LambdaQueryWrapper<PlmDocumentAcl>()
                .eq(PlmDocumentAcl::getDocId, doc.getId()));
        if (acls.isEmpty()) {
            if ("SECRET".equalsIgnoreCase(doc.getSecretLevel())) {
                throw new BusinessException("文档为保密级，未授权修改");
            }
            return;
        }
        boolean writable = acls.stream().anyMatch(a -> matched(a) && flag(a.getCanWrite(), 0) == 1);
        if (!writable) {
            throw new BusinessException("无该文档的修改权限: " + doc.getDocNo());
        }
        if (requireLock) {
            log.debug("document write granted: doc={} user={}", doc.getDocNo(), SecurityUtils.getCurrentUsername());
        }
    }

    private boolean matched(PlmDocumentAcl acl) {
        String type = acl.getSubjectType() == null ? "" : acl.getSubjectType().toUpperCase();
        String subject = acl.getSubjectId();
        switch (type) {
            case "USER":
                return isSelf(subject);
            case "ROLE":
                return SecurityUtils.hasRole(subject);
            default:
                // DEPT / PROJECT_ROLE 需要部门/项目角色上下文，当前会话未注入，按不匹配处理
                return false;
        }
    }

    private boolean isSelf(String who) {
        if (!StringUtils.hasText(who)) {
            return false;
        }
        String me = SecurityUtils.getCurrentUsername();
        String real = SecurityUtils.getCurrentRealName();
        return who.equals(me) || (real != null && who.equals(real));
    }

    /**
     * 真正的版本落库逻辑：算版本号 → 校验唯一 → 切 is_current → 写文件元数据 → 挂档案树。
     */
    private PlmDocumentVersion doAddVersion(PlmDocument doc, DocumentVersionDTO dto) {
        PlmFile file = fileService.getById(dto.getFileId());
        if (file == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文件不存在: " + dto.getFileId());
        }

        String version = StringUtils.hasText(dto.getVersion())
                ? dto.getVersion().trim()
                : nextMajorVersion(doc.getCurrentVersion());
        String revision = StringUtils.hasText(dto.getRevision()) ? dto.getRevision().trim() : null;

        LambdaQueryWrapper<PlmDocumentVersion> dupWrap = new LambdaQueryWrapper<PlmDocumentVersion>()
                .eq(PlmDocumentVersion::getDocId, doc.getId())
                .eq(PlmDocumentVersion::getVersion, version);
        if (revision == null) {
            dupWrap.isNull(PlmDocumentVersion::getRevision);
        } else {
            dupWrap.eq(PlmDocumentVersion::getRevision, revision);
        }
        Long dup = versionMapper.selectCount(dupWrap);
        if (dup != null && dup > 0) {
            throw new BusinessException("该文档已存在版本 " + version
                    + (revision == null ? "" : "." + revision) + "，请换版本号");
        }

        boolean release = Boolean.TRUE.equals(dto.getRelease());
        PlmDocumentVersion ver = new PlmDocumentVersion();
        ver.setDocId(doc.getId());
        ver.setVersion(version);
        ver.setRevision(revision);
        ver.setFileId(file.getId());
        ver.setFileName(file.getFileName());
        ver.setFileSize(file.getFileSize());
        ver.setChecksum(sha256OrFallback(file));
        ver.setChangeNote(dto.getChangeNote());
        ver.setState(release ? DocLifecycleState.RELEASED.name() : DocLifecycleState.WORKING.name());
        if (release) {
            ver.setApprovedBy(SecurityUtils.getCurrentRealName());
            ver.setApprovedAt(LocalDateTime.now());
        }
        ver.setIsCurrent(1);
        ver.setCreatedBy(SecurityUtils.getCurrentRealName());
        ver.setCreatedAt(LocalDateTime.now());

        versionMapper.update(null, new LambdaUpdateWrapper<PlmDocumentVersion>()
                .eq(PlmDocumentVersion::getDocId, doc.getId())
                .eq(PlmDocumentVersion::getIsCurrent, 1)
                .set(PlmDocumentVersion::getIsCurrent, 0));
        versionMapper.insert(ver);

        doc.setCurrentVersion(version);
        documentMapper.updateById(doc);

        attachToArchive(doc, file.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("docNo", doc.getDocNo());
        payload.put("version", version);
        payload.put("revision", revision == null ? "" : revision);
        payload.put("fileId", file.getId());
        payload.put("checksum", ver.getChecksum());
        publish("document.version.created", doc, payload);
        return ver;
    }

    /** 上传到档案节点时，同时挂到 plm_archive_file，老档案树页面能看到同一份文件 */
    private void attachToArchive(PlmDocument doc, Long fileId) {
        if (doc.getArchiveNodeId() == null || !StringUtils.hasText(doc.getPartNo())) {
            return;
        }
        try {
            ArchiveTreeNode node = archiveTreeNodeMapper.selectById(doc.getArchiveNodeId());
            if (node == null || !StringUtils.hasText(node.getNodeCode())) {
                log.warn("档案节点不存在，跳过挂接: doc={} nodeId={}", doc.getDocNo(), doc.getArchiveNodeId());
                return;
            }
            archiveTreeService.attachFile(doc.getPartNo(), node.getNodeCode(), fileId);
        } catch (Exception e) {
            log.warn("档案挂接失败（不影响文档版本）: doc={} err={}", doc.getDocNo(), e.getMessage());
        }
    }

    /** V1.0 -> V2.0；非标准格式则回落 V1.0 */
    private String nextMajorVersion(String current) {
        if (StringUtils.hasText(current) && current.startsWith("V")) {
            String body = current.substring(1);
            int dot = body.indexOf('.');
            String major = dot > 0 ? body.substring(0, dot) : body;
            try {
                return "V" + (Integer.parseInt(major) + 1) + ".0";
            } catch (NumberFormatException ignore) {
                // 落到默认
            }
        }
        return "V1.0";
    }

    private String sha256OrFallback(PlmFile file) {
        if (StringUtils.hasText(file.getFilePath())) {
            try {
                Path p = Paths.get(file.getFilePath());
                if (Files.isReadable(p)) {
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    try (InputStream in = Files.newInputStream(p)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            md.update(buf, 0, n);
                        }
                    }
                    StringBuilder sb = new StringBuilder(64);
                    for (byte b : md.digest()) {
                        sb.append(String.format("%02x", b));
                    }
                    return sb.toString();
                }
            } catch (Exception e) {
                log.warn("计算 SHA-256 失败，回落 md5: file={} err={}", file.getId(), e.getMessage());
            }
        }
        return file.getMd5Hash();
    }

    private String normalizeSecretLevel(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "INTERNAL";
        }
        String s = raw.trim().toUpperCase();
        if (!"PUBLIC".equals(s) && !"INTERNAL".equals(s) && !"SECRET".equals(s)) {
            throw new BusinessException("密级只能是 PUBLIC / INTERNAL / SECRET");
        }
        return s;
    }

    private Integer flag(Boolean v, int dft) {
        if (v == null) {
            return dft;
        }
        return v ? 1 : 0;
    }

    private List<PlmDocument> filterReadable(List<PlmDocument> list) {
        List<PlmDocument> out = new ArrayList<>();
        for (PlmDocument d : list) {
            try {
                assertReadable(d);
                out.add(d);
            } catch (BusinessException ignore) {
                // 无权限的直接剔除，不在列表里泄露存在性
            }
        }
        return out;
    }

    private void publish(String eventType, PlmDocument doc, Map<String, Object> payload) {
        try {
            Map<String, Object> p = new HashMap<>(payload);
            p.put("docNo", doc.getDocNo());
            p.put("docType", doc.getDocType() == null ? null : doc.getDocType().name());
            p.put("state", doc.getLifecycleState() == null ? null : doc.getLifecycleState().name());
            domainEventService.publish(eventType, "DOCUMENT", doc.getDocNo(), p);
        } catch (Exception e) {
            log.warn("文档事件发布失败（不影响主流程）: {} {}", eventType, e.getMessage());
        }
    }

    private String nullSafe(String v) {
        return v == null ? "" : v;
    }
}
