package com.hjgd.plm.archive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plm_archive_tree")
public class ArchiveTreeNode {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String partNo;
    private String nodeCode;
    private String nodeName;
    private Long parentId;
    private Integer levelNo;
    private Integer sortOrder;
    private Integer isLeaf;
    private LocalDateTime createdAt;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    @lombok.experimental.Accessors(chain = true)
    private java.util.List<ArchiveTreeNode> children;
}
