-- =====================================================================
-- Step2: CBOM 成本展开支撑 —— 物料标准成本
-- CBOM = 正式EBOM结构 × 物料标准成本(只读滚算)，供 EBMS 算订单毛利。
-- PLM 维护参考成本；EBMS 可用自有物料库单价覆盖(KPI_CBOM_SYNC 监控差异)。
-- 幂等：可重复执行。
-- =====================================================================

ALTER TABLE plm_material ADD COLUMN IF NOT EXISTS standard_cost DECIMAL(14,4);
ALTER TABLE plm_material ADD COLUMN IF NOT EXISTS cost_currency VARCHAR(8) DEFAULT 'CNY';

COMMENT ON COLUMN plm_material.standard_cost IS '标准/参考成本(单件)，CBOM 滚算用';
COMMENT ON COLUMN plm_material.cost_currency IS '成本币种 CNY/USD';
