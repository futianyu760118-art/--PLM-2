package com.hjgd.plm.m04.document.dto;

import lombok.Data;

@Data
public class DocumentVersionDTO {

    /** 主版本号，留空自动 +1（V1.0 -> V2.0） */
    private String version;

    /** 小版本，可空 */
    private String revision;

    /** 必填（检入/新增版本必须有文件） */
    private Long fileId;

    /** 变更说明 */
    private String changeNote;

    /** 上传后是否直接置为 RELEASED，默认 WORKING */
    private Boolean release;

    /** ADMIN 强制跳过"先检出"校验 */
    private Boolean force;
}
