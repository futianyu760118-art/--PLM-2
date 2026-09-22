package com.hjgd.plm.material.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 通用实体变更历史(差异比对 + 回滚)。
 * changed_fields JSONB 形如:
 *  [{"field":"unit","before":"","after":"PCS","changedBy":"admin","changedAt":"..."},
 *   {"field":"versionNo","before":"V1.0","after":"V1.1",...}]
 */
@Data
@TableName("plm_entity_history")
public class EntityHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String objectType;
    private String objectId;
    private Integer version;
    /** JSON 字符串(JSONB),前端按行渲染 */
    private String changedFields;
    /** JSON 字符串(JSONB),变更后完整快照,支持回滚 */
    private String snapshotAfter;
    private String changeType;
    private String changeSource;
    private String changeRef;
    private String changedBy;
    private String remark;
    private OffsetDateTime changedAt;
}