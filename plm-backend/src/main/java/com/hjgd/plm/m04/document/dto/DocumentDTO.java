package com.hjgd.plm.m04.document.dto;

import com.hjgd.plm.m04.document.enums.DocType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentDTO {

    private Long id;

    /** 留空自动生成；手工录入需 ADMIN 或 document:override-code */
    @Size(max = 64, message = "文档编号最长 64 位")
    private String docNo;

    @NotBlank(message = "文档标题不能为空")
    @Size(max = 300, message = "标题最长 300 字")
    private String title;

    private DocType docType;

    private String partNo;

    private String projectNo;

    private Long archiveNodeId;

    /** PUBLIC / INTERNAL / SECRET，默认 INTERNAL */
    private String secretLevel;

    private String owner;

    private String remark;
}
