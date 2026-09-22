-- =====================================================================
-- Phase 0 止血 (4/4): 统一 lifecycle_state / ECN EFFECTING-FAILED / BOM archive_version
-- 依据: docs/merged-architecture-v5.md §4.1 §4.2 §8.3 §11 Phase 0
--
-- 1) 废除 plm_material.status + lifecycle_status 双轨, 合并为单一 lifecycle_state
-- 2) 旧状态按 §8.3 映射表迁移 (REVIEWING -> IN_REVIEW, 其余同名)
-- 3) phase 作为正交维度保留, 不受本次迁移影响
-- 4) ECN 状态机补齐 EFFECTING / FAILED (v5 §4.2 专用流)
-- 5) BOM 归档版本号落库 + plm_bom_item.deleted 补齐 (18 号 DQ 规则引用了该列)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 新状态枚举 (v5 §4.2 Part/Bom/File/Sop 主状态集)
-- ---------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'lifecycle_state_enum') THEN
        CREATE TYPE lifecycle_state_enum AS ENUM (
            'DRAFT',         -- 草稿
            'IN_REVIEW',     -- 评审中 (原 REVIEWING)
            'RELEASED',      -- 正式发布
            'IN_PRODUCTION', -- 量产在用
            'CHANGING',      -- 变更中
            'OBSOLETE',      -- 作废
            'SEALED'         -- 停产封存
        );
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 2. 新增单一 lifecycle_state 列并回填
--    映射 (§8.3): DRAFT->DRAFT, REVIEWING->IN_REVIEW, RELEASED->RELEASED,
--                IN_PRODUCTION->IN_PRODUCTION, CHANGING->CHANGING,
--                OBSOLETE->OBSOLETE, SEALED->SEALED
--    取值优先级: status 为主, 仅当 status 为空时回退 lifecycle_status(历史脏数据保护)
-- ---------------------------------------------------------------------
ALTER TABLE plm_material ADD COLUMN IF NOT EXISTS lifecycle_state lifecycle_state_enum;

-- 回填包在 DO 块里: §3 会物理删除 status/lifecycle_status, 静态 SQL 会让本文件
-- 无法重跑(半途失败后无法恢复)。此处按实际存在的旧列动态拼 SQL, 两列都不存在
-- 即表示回填已完成, 直接跳过。
DO $$
DECLARE
    has_status boolean := EXISTS (SELECT 1 FROM information_schema.columns
                                  WHERE table_name = 'plm_material' AND column_name = 'status');
    has_lifecycle_status boolean := EXISTS (SELECT 1 FROM information_schema.columns
                                            WHERE table_name = 'plm_material' AND column_name = 'lifecycle_status');
    src_expr text;
BEGIN
    IF NOT (has_status OR has_lifecycle_status) THEN
        RAISE NOTICE 'plm_material 双轨旧列已废除, 跳过 §8.3 回填';
        RETURN;
    END IF;
    src_expr := CASE
        WHEN has_status AND has_lifecycle_status
            THEN 'COALESCE(status::text, lifecycle_status::text, ''DRAFT'')'
        WHEN has_status
            THEN 'COALESCE(status::text, ''DRAFT'')'
        ELSE 'COALESCE(lifecycle_status::text, ''DRAFT'')'
    END;
    EXECUTE format($sql$
        UPDATE plm_material SET lifecycle_state = (
            CASE %1$s WHEN 'REVIEWING' THEN 'IN_REVIEW' ELSE %1$s END
        )::lifecycle_state_enum
        WHERE lifecycle_state IS NULL
           OR lifecycle_state::text IS DISTINCT FROM (
                CASE %1$s WHEN 'REVIEWING' THEN 'IN_REVIEW' ELSE %1$s END
              )
    $sql$, src_expr);
END $$;

ALTER TABLE plm_material ALTER COLUMN lifecycle_state SET DEFAULT 'DRAFT';
ALTER TABLE plm_material ALTER COLUMN lifecycle_state SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_material_lifecycle_state ON plm_material(lifecycle_state);

COMMENT ON COLUMN plm_material.lifecycle_state IS 'V5 唯一生命周期状态 (DRAFT/IN_REVIEW/RELEASED/IN_PRODUCTION/CHANGING/OBSOLETE/SEALED)';
COMMENT ON COLUMN plm_material.phase IS 'NPI阶段 CONCEPT/STRUCTURE/MOLD_DEV/TRIAL/MASS_PRODUCTION/EOL (正交维度, 与 lifecycle_state 无关)';

-- ---------------------------------------------------------------------
-- 3. 废弃双轨: 写进列注释后物理删除, 杜绝再次双写
-- ---------------------------------------------------------------------
ALTER TABLE plm_material DROP COLUMN IF EXISTS lifecycle_status;
ALTER TABLE plm_material DROP COLUMN IF EXISTS status;
DROP INDEX IF EXISTS idx_material_status;
DROP TYPE IF EXISTS material_status_enum;

-- ---------------------------------------------------------------------
-- 4. 转换矩阵: REVIEWING -> IN_REVIEW, 并补齐 BOM 三态流转
--    (v5 §4.1 所有受控对象共用引擎; §4.3 转换表示例)
-- ---------------------------------------------------------------------
UPDATE plm_lifecycle_transition SET from_state = 'IN_REVIEW' WHERE object_type = 'PART' AND from_state = 'REVIEWING';
UPDATE plm_lifecycle_transition SET to_state   = 'IN_REVIEW' WHERE object_type = 'PART' AND to_state   = 'REVIEWING';

INSERT INTO plm_lifecycle_transition (object_type, from_state, to_state, action_code, roles, require_dq) VALUES
('BOM', 'DRAFT',     'RELEASED', 'release',       'ENGINEER,RD_LEAD,ADMIN', true),
('BOM', 'RELEASED',  'CHANGING', 'start_change',  'SYSTEM,ENGINEER,ADMIN',  false),
('BOM', 'CHANGING',  'RELEASED', 'finish_change', 'SYSTEM,ENGINEER,ADMIN',  true),
('BOM', 'RELEASED',  'OBSOLETE', 'obsolete',      'RD_LEAD,ADMIN',          false),
('BOM', 'CHANGING',  'OBSOLETE', 'obsolete',      'RD_LEAD,ADMIN',          false)
ON CONFLICT (object_type, from_state, action_code) DO UPDATE
    SET to_state = EXCLUDED.to_state, roles = EXCLUDED.roles, require_dq = EXCLUDED.require_dq;

-- ---------------------------------------------------------------------
-- 4.1 PART obsolete 不带 DQ 门禁
--    引擎改为按 require_dq 列判定门禁(原先由代码硬编码 release/to_production/
--    submit_review 三项), 若保留 obsolete 的 require_dq=true 会让「作废」新增一个
--    此前不存在的 DQ 阻断。v5 §4.3 中该动作的守卫是 no_active_where_used or force。
-- ---------------------------------------------------------------------
UPDATE plm_lifecycle_transition SET require_dq = false
WHERE object_type = 'PART' AND action_code = 'obsolete';

-- ---------------------------------------------------------------------
-- 5. ECN 专用流补齐 EFFECTING / FAILED (v5 §4.2)
--    PG 12+ 允许在事务块内 ADD VALUE, 但同一事务内不可使用该新值;
--    本文件后续语句不使用这两个新值, 故安全。
-- ---------------------------------------------------------------------
ALTER TYPE ecn_status_enum ADD VALUE IF NOT EXISTS 'EFFECTING' AFTER 'APPROVED';
ALTER TYPE ecn_status_enum ADD VALUE IF NOT EXISTS 'FAILED';
COMMENT ON TYPE ecn_status_enum IS 'ECN审批状态: DRAFT/PENDING_L1/PENDING_L2/APPROVED/EFFECTING/EFFECTIVE/FAILED/REJECTED/VOID';

-- ---------------------------------------------------------------------
-- 6. BOM: 归档版本号落库 + 明细软删除列补齐
--    plm_bom_item.deleted 被 18_dq_rules_strengthened.sql 的 5 条规则引用,
--    此前该列不存在, 这些 DQ 规则执行即报 column does not exist。
-- ---------------------------------------------------------------------
ALTER TABLE plm_bom ADD COLUMN IF NOT EXISTS archive_version_no VARCHAR(32);
COMMENT ON COLUMN plm_bom.archive_version_no IS '已归档(快照)的版本号, 发布/归档时写入, 与 plm_bom_version.version_no 对应';

ALTER TABLE plm_bom_item ADD COLUMN IF NOT EXISTS deleted SMALLINT DEFAULT 0;
COMMENT ON COLUMN plm_bom_item.deleted IS '软删除标记(0正常/1删除), 供 DQ 规则与 where-used 过滤';
CREATE INDEX IF NOT EXISTS idx_bom_item_deleted ON plm_bom_item(deleted);

-- ---------------------------------------------------------------------
-- 6.1 历史 BOM 明细物化路径回填
--     此前 rebuildPath 吞异常, 大量行 path 为空 -> where-used (path @>) 查不到。
--     递归深度上限 64 防止脏数据成环导致无限递归。
-- ---------------------------------------------------------------------
WITH RECURSIVE tree AS (
    SELECT id, id::text::ltree AS p, 1 AS depth
    FROM plm_bom_item
    WHERE COALESCE(parent_item_id, 0) = 0
    UNION ALL
    SELECT c.id, t.p || c.id::text::ltree, t.depth + 1
    FROM plm_bom_item c
    JOIN tree t ON c.parent_item_id = t.id
    WHERE t.depth < 64
)
UPDATE plm_bom_item bi
SET path = t.p
FROM tree t
WHERE bi.id = t.id
  AND (bi.path IS NULL OR bi.path::text IS DISTINCT FROM t.p::text);

UPDATE plm_bom_item c
SET parent_part_no = p.part_no
FROM plm_bom_item p
WHERE c.parent_item_id = p.id
  AND p.bom_id = c.bom_id
  AND c.parent_part_no IS DISTINCT FROM p.part_no;

-- ---------------------------------------------------------------------
-- 7. 字典型数据同步: material_status -> lifecycle_state, REVIEWING -> IN_REVIEW
-- ---------------------------------------------------------------------
UPDATE sys_dict SET dict_type = 'lifecycle_state' WHERE dict_type = 'material_status';
UPDATE sys_dict SET dict_value = 'IN_REVIEW', dict_label = '评审中'
WHERE dict_type = 'lifecycle_state' AND dict_value = 'REVIEWING';
