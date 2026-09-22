package com.hjgd.plm.material.enums;

public enum MaterialStatus {
    DRAFT,          // 草稿
    IN_REVIEW,      // 评审中 (v5 §8.3: 旧 REVIEWING 迁移至此)
    RELEASED,       // 正式发布
    IN_PRODUCTION,  // 量产在用
    CHANGING,       // 变更中
    OBSOLETE,       // 作废
    SEALED          // 停产封存
}
