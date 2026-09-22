package com.hjgd.plm.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 研发项目进度节点(19 节点/项目)。
 */
@Data
@TableName("plm_project_node")
public class ProjectNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String projectNo;
    private String nodeCode;
    private Integer seq;
    private String nodeName;
    private Integer isKey;
    private String status;
    private LocalDate planDate;
    private LocalDate actualDate;
    private String owner;
    private String deliveryDesc;
    private String remark;
    private Integer evidenceCount;
    private Integer editCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
