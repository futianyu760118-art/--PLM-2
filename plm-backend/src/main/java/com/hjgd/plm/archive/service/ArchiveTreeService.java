package com.hjgd.plm.archive.service;

import com.hjgd.plm.archive.entity.ArchiveFile;
import com.hjgd.plm.archive.entity.ArchiveTreeNode;

import java.util.List;

public interface ArchiveTreeService {

    void generateForPart(String partNo);

    /** 按品类/灯具品类/材质解析模板生成档案树 */
    void generateForPart(String partNo, String partCategory, String productType, String materialType);

    boolean existsForPart(String partNo);

    List<ArchiveTreeNode> getTree(String partNo);

    void attachFile(String partNo, String nodeCode, Long fileId);

    List<ArchiveFile> listFiles(Long nodeId);

    void removeFile(Long archiveFileId);
}
