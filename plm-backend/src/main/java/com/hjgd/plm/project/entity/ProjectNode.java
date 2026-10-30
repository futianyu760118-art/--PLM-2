package com.hjgd.plm.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 研发项目进度节点(22 节点/项目 + 可追加子项节点)。
 */
@Data
@TableName("plm_project_node")
public class ProjectNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String projectNo;
    private String nodeCode;
    /** 追加子项时挂靠的父节点编码(如 MOLD 重开子项 parent_code=MOLD)；标准22节点为空 */
    private String parentCode;
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
    private String sourceSystem;
    private Integer revision;
    private String syncStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 请求标志(不落库)：true 表示显式清空 actual_date */
    @TableField(exist = false)
    private Boolean clearActual;
    /** 请求标志(不落库)：true 表示显式清空 plan_date */
    @TableField(exist = false)
    private Boolean clearPlan;
}
