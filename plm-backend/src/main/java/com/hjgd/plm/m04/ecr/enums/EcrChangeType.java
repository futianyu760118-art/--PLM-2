package com.hjgd.plm.m04.ecr.enums;

/**
 * ECR 变更类型。注意与 ECN 的 EcnChangeType 不是一一对应：
 * ECN 只有 STRUCTURE/MOLD/PROCESS/BOM/DIMENSION，ECR 多了 MATERIAL/DOCUMENT。
 * 转换时由 {@code EcrServiceImpl.mapToEcnType} 统一映射。
 */
public enum EcrChangeType {
    MATERIAL,    // 物料变更
    STRUCTURE,   // 结构变更
    PROCESS,     // 工艺变更
    DOCUMENT,    // 文档变更
    BOM          // BOM 结构变更
}
