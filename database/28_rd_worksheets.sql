-- =============================================================================
-- HJ-PLM 增量 28: 研发自治中心 - 项目工作表 (模板工作表落地)
-- 依据: docs/project-node-worksheet-map.md
--   规格书/配置表/样品单/评审单/送检测试报告/试产报告/出货验货
-- 数据流: 填写工作表 -> 统计到 项目明细表(22节点) -> 汇总到 研发项目跟踪
-- =============================================================================

-- 18规格书登记 / 3方案
CREATE TABLE IF NOT EXISTS plm_spec_sheet (
    id BIGSERIAL PRIMARY KEY,
    project_no VARCHAR(64), model VARCHAR(128), product_name VARCHAR(256),
    version VARCHAR(32), file_no VARCHAR(64),
    light_source VARCHAR(128), power VARCHAR(64), input_voltage VARCHAR(64),
    beam_angle VARCHAR(64), luminous_flux VARCHAR(64), cct VARCHAR(64), ra VARCHAR(64),
    ta VARCHAR(64), life_time VARCHAR(64), ip_rating VARCHAR(64),
    shell_material VARCHAR(128), reflector_material VARCHAR(128),
    battery_capacity VARCHAR(64), discharge_time VARCHAR(64), charging_time VARCHAR(64),
    switch_type VARCHAR(64), dimension VARCHAR(128), net_weight VARCHAR(64),
    status VARCHAR(32) DEFAULT '编制中', approved_by VARCHAR(64), approved_at DATE,
    remark TEXT, created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_spec_sheet_proj ON plm_spec_sheet(project_no);

-- 4配置表 / 23设计输入清单
CREATE TABLE IF NOT EXISTS plm_config_sheet (
    id BIGSERIAL PRIMARY KEY,
    project_no VARCHAR(64), model VARCHAR(128),
    structure_shell VARCHAR(128), structure_reflector VARCHAR(128), structure_bracket VARCHAR(128),
    structure_handle VARCHAR(128), structure_waterproof VARCHAR(64), structure_cable VARCHAR(64),
    structure_screw VARCHAR(64), structure_glass VARCHAR(64),
    elec_luminous VARCHAR(64), elec_efficiency VARCHAR(64), elec_param VARCHAR(128),
    elec_color_temp VARCHAR(64), elec_ra VARCHAR(64), elec_led_count VARCHAR(64),
    elec_rated_power VARCHAR(64), elec_chip VARCHAR(128), elec_board_model VARCHAR(128),
    elec_battery VARCHAR(128),
    pack_inner VARCHAR(128), pack_outer VARCHAR(128), pack_transport VARCHAR(128), pack_other VARCHAR(128),
    certificate_required VARCHAR(128), special_env VARCHAR(128), special_uv VARCHAR(128),
    special_salt VARCHAR(128), special_other VARCHAR(128),
    status VARCHAR(32) DEFAULT '编制中',
    remark TEXT, created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_config_sheet_proj ON plm_config_sheet(project_no);

-- 21样品单
CREATE TABLE IF NOT EXISTS plm_sample (
    id BIGSERIAL PRIMARY KEY,
    sample_no VARCHAR(64), project_no VARCHAR(64), sample_name VARCHAR(256), spec VARCHAR(256),
    quantity INT, purpose VARCHAR(128), require_date DATE,
    status VARCHAR(32) DEFAULT '待寄送', send_no VARCHAR(64), owner VARCHAR(64),
    remark TEXT, created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_sample_proj ON plm_sample(project_no);

-- 27评审单
CREATE TABLE IF NOT EXISTS plm_review_sheet (
    id BIGSERIAL PRIMARY KEY,
    review_no VARCHAR(64), project_no VARCHAR(64), review_type VARCHAR(64),
    review_date DATE, host VARCHAR(64), participants VARCHAR(256),
    conclusion VARCHAR(32), issues TEXT, tracker VARCHAR(64), complete_date DATE,
    remark TEXT, created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_review_sheet_proj ON plm_review_sheet(project_no);

-- 25送检单 / 26测试报告
CREATE TABLE IF NOT EXISTS plm_test_report (
    id BIGSERIAL PRIMARY KEY,
    report_no VARCHAR(64), send_no VARCHAR(64), project_no VARCHAR(64),
    test_type VARCHAR(64), test_item VARCHAR(256), standard_req VARCHAR(256), actual_value VARCHAR(256),
    verdict VARCHAR(32), test_date DATE, require_date DATE, lab VARCHAR(128), tester VARCHAR(64),
    status VARCHAR(32) DEFAULT '待检',
    remark TEXT, created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_test_report_proj ON plm_test_report(project_no);

-- 29试产报告 (trial_type: 电子试产/研发试产/工程试产/生产试产)
CREATE TABLE IF NOT EXISTS plm_trial_report (
    id BIGSERIAL PRIMARY KEY,
    report_no VARCHAR(64), project_no VARCHAR(64), trial_type VARCHAR(64),
    trial_batch VARCHAR(64), trial_qty INT, good_qty INT, defect_rate VARCHAR(32),
    main_issues TEXT, conclusion VARCHAR(32), trial_date DATE, owner VARCHAR(64),
    remark TEXT, created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_trial_report_proj ON plm_trial_report(project_no);

-- 22订单登记 / 30验货对比报告
CREATE TABLE IF NOT EXISTS plm_shipment (
    id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(64), project_no VARCHAR(64), customer VARCHAR(256),
    ship_date DATE, verify_date DATE, verify_item VARCHAR(256),
    standard_req VARCHAR(256), actual_result VARCHAR(256), verdict VARCHAR(32),
    rectify_req TEXT, status VARCHAR(32) DEFAULT '待出货',
    remark TEXT, created_at TIMESTAMPTZ DEFAULT NOW(), updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_shipment_proj ON plm_shipment(project_no);

-- =============================================================================
-- Done.
-- =============================================================================
