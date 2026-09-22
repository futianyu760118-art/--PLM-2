-- Analytics 种子：10 个数据集 + 10 个图表配置(对应设计 §9.1 D 层)
-- 表结构已在 10_v1_1_lean_agent_kpi.sql 建好

INSERT INTO plm_analytics_dataset (dataset_code, name, sql_text, cache_ttl_sec, enabled) VALUES
('DS_DQ_SCORE_DAILY',   '主数据质量均分趋势', 'SELECT grain_time::date AS t, value_calc AS score FROM plm_metric_value WHERE metric_code=''M_DQ_SCORE'' ORDER BY grain_time DESC LIMIT 90', 600, true),
('DS_ECN_CYCLE',         'ECN闭环小时趋势', 'SELECT grain_time::date AS t, value_calc AS hours FROM plm_metric_value WHERE metric_code=''M_ECN_CYCLE_H'' ORDER BY grain_time DESC LIMIT 90', 600, true),
('DS_WIP_DRAFT',         '超龄草稿数', 'SELECT grain_time::date AS t, value_calc AS count FROM plm_metric_value WHERE metric_code=''M_WIP_DRAFT'' ORDER BY grain_time DESC LIMIT 90', 600, true),
('DS_CODE_AUTO_RATE',    '自动编号率趋势', 'SELECT grain_time::date AS t, value_calc AS pct FROM plm_metric_value WHERE metric_code=''M_CODE_AUTO_RATE'' ORDER BY grain_time DESC LIMIT 90', 600, true),
('DS_KPI_RED_GREEN',     'KPI红绿灯一览', 'SELECT kpi_code, name, actual_value, target_value, status FROM plm_kpi_value v JOIN plm_kpi_def d ON v.kpi_code=d.kpi_code WHERE v.period_key=TO_CHAR(NOW(),''YYYY-MM'') ORDER BY v.kpi_code', 300, true),
('DS_BOM_REWORK',        'BOM回退计数', 'SELECT grain_time::date AS t, value_calc AS count FROM plm_metric_value WHERE metric_code=''M_BOM_REWORK_7D'' ORDER BY grain_time DESC LIMIT 30', 600, true),
('DS_AGENT_ADOPT',       '智能体采纳率', 'SELECT grain_time::date AS t, value_calc AS pct FROM plm_metric_value WHERE metric_code=''M_AGENT_ADOPT'' ORDER BY grain_time DESC LIMIT 90', 600, true),
('DS_PART_TYPE_DIST',    '物料类型分布', 'SELECT material_type AS type, COUNT(1) AS count FROM plm_material WHERE deleted=0 GROUP BY material_type', 1800, true),
('DS_PART_STATUS_DIST',  '物料状态分布', 'SELECT status::text AS status, COUNT(1) AS count FROM plm_material WHERE deleted=0 GROUP BY status ORDER BY status', 1800, true),
('DS_INSIGHTS_OPEN',     '开放洞察', 'SELECT insight_no, title, severity, category, created_at FROM plm_analytics_insight WHERE status=''NEW'' ORDER BY severity, created_at DESC LIMIT 30', 300, true)
ON CONFLICT (dataset_code) DO NOTHING;

INSERT INTO plm_analytics_chart (chart_code, title, dataset_code, chart_type, encode_json, default_filters, enabled) VALUES
('CH_DQ_SCORE',        '主数据质量均分', 'DS_DQ_SCORE_DAILY', 'LINE',
 '{"xField":"t","yField":"score","smooth":true,"color":"#67c23a"}', NULL, true),
('CH_ECN_CYCLE',       'ECN平均闭环小时', 'DS_ECN_CYCLE', 'LINE',
 '{"xField":"t","yField":"hours","smooth":true,"color":"#409eff"}', NULL, true),
('CH_WIP',             '超龄草稿数', 'DS_WIP_DRAFT', 'BAR',
 '{"xField":"t","yField":"count","color":"#e6a23c"}', NULL, true),
('CH_CODE_AUTO',       '自动编号率', 'DS_CODE_AUTO_RATE', 'LINE',
 '{"xField":"t","yField":"pct","max":100,"color":"#909399"}', NULL, true),
('CH_KPI_GRID',        'KPI红绿灯', 'DS_KPI_RED_GREEN', 'TABLE',
 '{"columns":[{"label":"KPI","field":"kpi_code"},{"label":"名称","field":"name"},{"label":"实际","field":"actual_value"},{"label":"目标","field":"target_value"},{"label":"状态","field":"status","tagMap":{"GREEN":"success","YELLOW":"warning","RED":"danger"}}]}', NULL, true),
('CH_BOM_REWORK',      'BOM回退计数', 'DS_BOM_REWORK', 'BAR',
 '{"xField":"t","yField":"count","color":"#f56c6c"}', NULL, true),
('CH_AGENT_ADOPT',     '智能体采纳率', 'DS_AGENT_ADOPT', 'LINE',
 '{"xField":"t","yField":"pct","max":100,"color":"#27ae60"}', NULL, true),
('CH_PART_TYPE',       '物料类型分布', 'DS_PART_TYPE_DIST', 'PIE',
 '{"nameField":"type","valueField":"count"}', NULL, true),
('CH_PART_STATUS',     '物料状态分布', 'DS_PART_STATUS_DIST', 'PIE',
 '{"nameField":"status","valueField":"count"}', NULL, true),
('CH_INSIGHTS',        '开放洞察', 'DS_INSIGHTS_OPEN', 'TABLE',
 '{"columns":[{"label":"编号","field":"insight_no"},{"label":"标题","field":"title"},{"label":"严重度","field":"severity","tagMap":{"HIGH":"danger","MEDIUM":"warning","LOW":"info"}},{"label":"分类","field":"category"},{"label":"时间","field":"created_at"}]}', NULL, true)
ON CONFLICT (chart_code) DO NOTHING;
