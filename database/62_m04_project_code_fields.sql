-- =============================================================================
-- 62_m04_project_code_fields.sql
-- 项目号编码所需字段落库 + 存量回填
--
-- 依据：61_m04_project_code_rule.sql 定义的项目号规则 HJ.AAXXX.Y1.ZZZ
--   Y1 = 产品分类(V/R/M) + 分类版本号(1-9)
--   ZZZ = 客户编码(3~4 位数字，000=自研)
--
-- 现存问题：plm_project 只有 project_type（取值混乱：NEW/SELF/客制），
--           无法表达 V/R/M 与版本号；customer_code 大量为空。
--
-- 本脚本：
--   1) 增列 project_class / class_version
--   2) 从存量 project_no 反解回填（155 条合规编号可 100% 回填）
--   3) customer_code 为空时按编号回填
--   4) 建唯一性防护索引（同 年+序号 只允许一个主号）
--
-- 幂等：可重复执行。
-- =============================================================================

-- ---------- 1. 增列 ----------
ALTER TABLE plm_project ADD COLUMN IF NOT EXISTS project_class VARCHAR(2);
ALTER TABLE plm_project ADD COLUMN IF NOT EXISTS class_version  SMALLINT;
ALTER TABLE plm_project ADD COLUMN IF NOT EXISTS code_suffix    VARCHAR(16);   -- 存量后缀 -N / JY / YL，仅记录不参与新编号

COMMENT ON COLUMN plm_project.project_class IS '产品分类 V=全新产品 R=功能性能拓展 M=客户定制变化（项目号第3段首字母）';
COMMENT ON COLUMN plm_project.class_version IS '分类版本号 1-9（项目号第3段数字）';
COMMENT ON COLUMN plm_project.code_suffix   IS '存量非标准后缀，迁移用；新项目号不使用';

-- ---------- 2. 从存量编号反解回填 ----------
--   HJ.26044.R1.1008      -> class=R ver=1 cust=1008 suffix=NULL
--   HJ.26031.M1.585-1     -> class=M ver=1 cust=585  suffix=-1
--   HJ.25014.V1.256JY     -> class=V ver=1 cust=256  suffix=JY
UPDATE plm_project
SET project_class = substring(project_no from '\.([VRM])[0-9]\.'),
    class_version = substring(project_no from '\.[VRM]([0-9])\.')::smallint,
    code_suffix   = NULLIF(substring(project_no from '\.[0-9]{3,4}(.*)$'), '')
WHERE project_no ~ '^HJ\.[0-9]{5}\.[VRM][0-9]\.[0-9]{3,4}'
  AND (project_class IS NULL OR class_version IS NULL);

-- ---------- 3. customer_code 回填（仅空值）----------
UPDATE plm_project
SET customer_code = substring(project_no from '\.([0-9]{3,4})(?:-[0-9]+)?$')
WHERE (customer_code IS NULL OR btrim(customer_code) = '')
  AND project_no ~ '^HJ\.[0-9]{5}\.[VRM][0-9]\.[0-9]{3,4}';

-- ---------- 4. 索引 ----------
CREATE INDEX IF NOT EXISTS idx_project_class_ver ON plm_project(project_class, class_version);

-- ---------- 5. 回填结果核对（执行后手工查看）----------
-- SELECT project_class, class_version, count(*) FROM plm_project GROUP BY 1,2 ORDER BY 1,2;
-- SELECT count(*) FILTER (WHERE project_class IS NULL) AS 未回填, count(*) AS 总数 FROM plm_project;

-- ---------- 6. 年度序号占用情况（新号码必须从这里往上续）----------
CREATE OR REPLACE VIEW v_m04_project_seq_used AS
SELECT
    substring(project_no from 3 for 2) AS yy,
    count(*)                            AS used_count,
    max(substring(project_no from 3 for 3)::int) AS max_seq
FROM plm_project
WHERE project_no ~ '^HJ\.[0-9]{5}\.'
GROUP BY 1
ORDER BY 1;

COMMENT ON VIEW v_m04_project_seq_used IS '各年度已用项目序号，新项目号须从 max_seq+1 续号，上限 999';
