package com.hjgd.plm.improve.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标准作业库：改善验证有效后固化为标准作业，防止问题复发。
 */
@Data
@TableName("plm_standard_work")
public class StandardWork {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String swNo;
    private String title;
    private String objectType;
    private String content;
    private String versionNo;
    private Long fromIssueId;
    private String status;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
