-- =============================================================================
-- HJ-PLM 增量 57: 研发项目新增「系统自动编号」列 auto_no
-- 需求: 新建项目时系统自动编号单独成一列(auto_no)，现有 project_no 列保留人工输入。
-- 规则: auto_no 始终由序列 PROJECT_NO 生成(HJ+日期+序号)；
--       project_no 人工输入优先，留空时回填为 auto_no(保持非空，兼容下游引用)。
-- =============================================================================

ALTER TABLE IF EXISTS plm_project ADD COLUMN IF NOT EXISTS auto_no VARCHAR(64);

-- 存量回填: 历史 project_no 作为初始系统编号(project_no 本身 NOT NULL)
UPDATE plm_project SET auto_no = project_no WHERE auto_no IS NULL OR auto_no = '';

-- 唯一索引(部分索引，兼容未来可能的空值)
CREATE UNIQUE INDEX IF NOT EXISTS idx_project_auto_no ON plm_project (auto_no) WHERE auto_no IS NOT NULL;
