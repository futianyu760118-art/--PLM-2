package com.hjgd.plm.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_project_node_evidence")
public class ProjectNodeEvidence {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long nodeId;
    private Long projectId;
    private String nodeCode;
    private Long fileId;
    private String fileName;
    private String docType;
    private String note;
    private String content;
    private String source;
    private String mimeType;
    private String versionNo;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
}
