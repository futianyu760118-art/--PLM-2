-- =============================================================================
-- HJ-PLM V1.1 增量 23: 研发项目 - 立项申请书
-- 来源: sales 系统「研发中心-项目管理-立项申请」
-- 六大区块: 一、基本信息 二、客户信息 三、产品规格对比 四、可实现性评估
--           五、销售预测 六、特殊要求 (+ 研发目标与内容、审批信息)
-- 五阶段审批流: apply(立项发起) → dept(部门审核) → review(研发/财务审核)
--               → gm(总经理批准) → execute(项目经理执行)
-- 批准后可一键转为研发项目(plm_project)
-- =============================================================================

CREATE TABLE IF NOT EXISTS plm_project_initiation (
    id              BIGSERIAL PRIMARY KEY,
    init_no         VARCHAR(64) NOT NULL UNIQUE,           -- 立项申请编号 LX+日期+序号
    project_id      BIGINT,                                -- 批准后关联的研发项目
    -- 一、基本信息
    project_no      VARCHAR(64),
    project_name    VARCHAR(256) NOT NULL,
    project_type    VARCHAR(64),
    start_date      VARCHAR(32),
    department      VARCHAR(64),
    owner           VARCHAR(64),
    cooperators     VARCHAR(256),
    other_info      TEXT,
    -- 二、客户信息
    customer_no     VARCHAR(64),
    customer_type   VARCHAR(64),
    customer_level  VARCHAR(64),
    customer_win_rate VARCHAR(16),
    market_status   VARCHAR(128),
    customer_pain   TEXT,
    key_success     TEXT,
    has_competitor  VARCHAR(64),
    purchase_cycle  VARCHAR(32),
    dev_type        VARCHAR(64),
    -- 三~六、子表(JSONB)
    product_specs   JSONB,
    feasibility     JSONB,
    approval_signs  JSONB,
    sales_forecast  JSONB,
    special_reqs    JSONB,
    -- 研发目标与内容
    background      TEXT,
    necessity       TEXT,
    market_analysis TEXT,
    rd_objectives   TEXT,
    rd_content      TEXT,
    key_innovation  TEXT,
    tech_solution   TEXT,
    tech_route      TEXT,
    plan_summary    TEXT,
    milestones      TEXT,
    expected_outcome TEXT,
    economic_benefit TEXT,
    target_market   VARCHAR(128),
    budget_total    NUMERIC(14,2) DEFAULT 0,
    budget_detail   TEXT,
    team_requirement VARCHAR(256),
    risk_analysis   TEXT,
    risk_measures   TEXT,
    -- 审批信息
    applicant       VARCHAR(64),
    apply_date      VARCHAR(32),
    approval_status VARCHAR(16) NOT NULL DEFAULT 'draft',  -- draft/submitted/approved/rejected
    approver        VARCHAR(64),
    approval_date   VARCHAR(32),
    approval_opinion TEXT,
    -- 五阶段审批流
    workflow_stage  VARCHAR(16) NOT NULL DEFAULT 'apply',  -- apply/dept/review/gm/execute/rejected
    step1_apply_date VARCHAR(32),
    step1_applicant  VARCHAR(64),
    step2_approver   VARCHAR(64),
    step2_date       VARCHAR(32),
    step2_opinion    TEXT,
    step2_result     VARCHAR(16),
    step3_rd_reviewer VARCHAR(64),
    step3_rd_opinion  TEXT,
    step3_rd_date     VARCHAR(32),
    step3_finance_reviewer VARCHAR(64),
    step3_finance_opinion  TEXT,
    step3_finance_date     VARCHAR(32),
    step4_approver   VARCHAR(64),
    step4_date       VARCHAR(32),
    step4_opinion    TEXT,
    step5_owner      VARCHAR(64),
    step5_start_date VARCHAR(32),
    remarks         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_init_status ON plm_project_initiation(approval_status, workflow_stage);
CREATE INDEX IF NOT EXISTS idx_init_project ON plm_project_initiation(project_id);
COMMENT ON TABLE plm_project_initiation IS '研发项目立项申请书(五阶段审批流)';

-- 编号序列
INSERT INTO sys_sequence (seq_key, prefix, date_pattern, length, current_val)
VALUES ('INITIATION_NO', 'LX', 'yyyyMMdd', 4, 0)
ON CONFLICT (seq_key) DO NOTHING;
