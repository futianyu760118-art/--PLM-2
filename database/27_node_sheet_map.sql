-- =============================================================================
-- HJ-PLM 增量 27: 研发自治中心 - 节点 ↔ 模板工作表 映射
-- 依据: docs/project-node-worksheet-map.md
--   数据流: 填写模板工作表 -> 统计到 项目明细表(22节点) -> 汇总到 研发项目跟踪
-- =============================================================================

CREATE TABLE IF NOT EXISTS plm_node_sheet_map (
    id             BIGSERIAL PRIMARY KEY,
    node_code      VARCHAR(32) NOT NULL,
    seq            INT NOT NULL,
    node_name      VARCHAR(64) NOT NULL,
    is_key         SMALLINT NOT NULL DEFAULT 0,
    worksheet_keys VARCHAR(512),        -- 对应模板工作表
    source_tables  VARCHAR(512),        -- PLM-2 载体表
    done_rule      VARCHAR(512),        -- 完成判定规则
    summary_col    VARCHAR(64),         -- 汇总到研发项目跟踪的列
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (node_code)
);
COMMENT ON TABLE plm_node_sheet_map IS '项目明细表节点 ↔ 模板工作表 对照';

INSERT INTO plm_node_sheet_map (node_code, seq, node_name, is_key, worksheet_keys, source_tables, done_rule, summary_col) VALUES
('PLAN',           1,  '计划表',   0, '8甘特图',                              'plm_project_node/plm_process_route', '甘特图有计划且经确认',        'PLAN'),
('BOM',            2,  'BOM',      0, '5基础BOM;24客户BOM;31成本BOM',          'plm_bom;plm_bom_item',               '基础BOM已发布',               'BOM'),
('SPEC',           3,  '规格书',   0, '18规格书登记;3方案',                    'plm_spec_sheet',                     '规格书状态=已批准',           'SPEC'),
('CONFIG',         4,  '配置表',   0, '4配置表;23设计输入清单',                'plm_config_sheet',                   '配置表已签认',               'CONFIG'),
('MOLD_DRAWING',   5,  '模具图纸', 0, '13模具管理(图纸);附件;8甘特图',          'plm_file',                           '二维图纸已发布',             'MOLD_DRAWING'),
('MOLD_REVIEW',    6,  '开模评审', 1, '27评审单(开模评审);13模具管理(申请)',    'plm_review_sheet',                   '评审结论=通过',              'MOLD_REVIEW'),
('HAND_SAMPLE',    7,  '手样',     0, '21样品单;27评审单(样机评审)',           'plm_sample;plm_review_sheet',        '样机评审通过',               'HAND_SAMPLE'),
('APPEARANCE',     8,  '外观',     0, '3方案(外观);27评审单(外观评审)',         'plm_review_sheet',                   '外观评审通过',               'APPEARANCE'),
('STRUCTURE',      9,  '结构',     0, '3方案(结构方案);27评审单(结构评审)',     'plm_review_sheet',                   '结构评审通过',               'STRUCTURE'),
('ELECTRONICS',   10,  '电子',     0, '3方案(电子方案);10异常',                'plm_review_sheet',                   '电子样品确认',               'ELECTRONICS'),
('MOLD',          11,  '模具',     1, '13模具管理',                           'plm_mold;plm_mold_trial_log',        '模具验收/T0完成',           'MOLD'),
('MOLD_SAMPLE',   12,  '模样',     0, '21样品单(模具样品);27评审单(样品评审)',   'plm_sample;plm_review_sheet',        '样品确认单签署',             'MOLD_SAMPLE'),
('PACKAGING',     13,  '包装设计', 0, '附件:包装平面图纸',                     'plm_file',                           '包装确认',                   'PACKAGING'),
('ELEC_TRIAL',    14,  '电试',     0, '26测试报告(电);29试产报告(电子试产)',     'plm_test_report;plm_trial_report',   '报告结论=合格',              'ELEC_TRIAL'),
('RD_TRIAL',      15,  '研试',     1, '29试产报告(研发试产)',                  'plm_trial_report',                   '报告结论=通过',              'RD_TRIAL'),
('TECH_TRANSFER', 16,  '技转',     1, '28技转报告',                           'plm_tech_doc;plm_tech_flow',         '技转报告状态=完成',          'TECH_TRANSFER'),
('ENG_TRIAL',     17,  '工试',     1, '29试产报告(工程试产)',                  'plm_trial_report',                   '报告结论=通过',              'ENG_TRIAL'),
('PROD_TRIAL',    18,  '生试',     1, '29试产报告(生产试产)',                  'plm_trial_report',                   '报告结论=通过',              'PROD_TRIAL'),
('TEST_REPORT',   19,  '测试报告', 1, '25送检单;26测试报告',                   'plm_test_report',                    '判定=合格',                  'TEST_REPORT'),
('SHIPMENT',      20,  '出货',     0, '22订单登记;30验货对比报告',             'plm_shipment',                       '客户签收/验货合格',          'SHIPMENT'),
('REVIEW',        21,  '复盘',     0, '11复盘',                               'plm_project_review',                 '复盘完成并归档',             'REVIEW'),
('OTHER',         22,  '其他',     0, '10异常;16技术货架引用;19培训记录;20内审与管理评审', '各自模块',                '自定义',                     'OTHER')
ON CONFLICT (node_code) DO UPDATE SET
    seq = EXCLUDED.seq,
    node_name = EXCLUDED.node_name,
    is_key = EXCLUDED.is_key,
    worksheet_keys = EXCLUDED.worksheet_keys,
    source_tables = EXCLUDED.source_tables,
    done_rule = EXCLUDED.done_rule,
    summary_col = EXCLUDED.summary_col,
    updated_at = NOW();

-- =============================================================================
-- Done.
-- =============================================================================
