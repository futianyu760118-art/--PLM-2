-- =============================================================================
-- HJ-PLM V1.1 增量 22: 工序自动化流转
-- 工序路线绑定业务对象(PROJECT/MOLD/MATERIAL...), 有序工序步骤;
-- 上一道工序完成 → 自动激活下一道工序并向其负责人推送待办(plm_work_item),
-- 直至全部完成自动闭环。
-- =============================================================================

-- ---------- 1. 工序路线 ----------
CREATE TABLE IF NOT EXISTS plm_process_route (
    id              BIGSERIAL PRIMARY KEY,
    route_no        VARCHAR(64) NOT NULL UNIQUE,
    route_name      VARCHAR(256) NOT NULL,
    ref_type        VARCHAR(32),                          -- 关联业务类型: PROJECT/MOLD/MATERIAL...
    ref_id          VARCHAR(64),                          -- 关联业务主键(料号/项目号等)
    status          VARCHAR(16) NOT NULL DEFAULT 'RUNNING', -- RUNNING/COMPLETED/CANCELLED
    current_step_id BIGINT,
    created_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_process_route_status ON plm_process_route(status, created_at);
CREATE INDEX IF NOT EXISTS idx_process_route_ref ON plm_process_route(ref_type, ref_id);
COMMENT ON TABLE plm_process_route IS '工序路线(自动化流转载体)';
COMMENT ON COLUMN plm_process_route.current_step_id IS '当前进行中的工序步骤';

-- ---------- 2. 工序步骤 ----------
CREATE TABLE IF NOT EXISTS plm_process_step (
    id            BIGSERIAL PRIMARY KEY,
    route_id      BIGINT NOT NULL REFERENCES plm_process_route(id),
    step_order    INT NOT NULL,                            -- 工序顺序 1..N
    name          VARCHAR(128) NOT NULL,                   -- 工序名称
    owner_id      BIGINT,                                  -- 负责人(待办推送目标)
    sla_hours     INT,                                     -- 完成时限(小时)
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',  -- PENDING/ACTIVE/DONE/SKIPPED
    work_item_id  BIGINT,                                  -- 当前工序对应的待办
    started_at    TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ,
    remark        VARCHAR(512),                            -- 完成备注(留痕)
    UNIQUE (route_id, step_order)
);
CREATE INDEX IF NOT EXISTS idx_process_step_route ON plm_process_step(route_id, step_order);
CREATE INDEX IF NOT EXISTS idx_process_step_owner ON plm_process_step(owner_id, status);
COMMENT ON TABLE plm_process_step IS '工序步骤(完成后自动推送下一道)';
COMMENT ON COLUMN plm_process_step.work_item_id IS '本道工序生成的待办ID(refType=PROCESS_STEP)';

-- ---------- 3. 编号序列 ----------
INSERT INTO sys_sequence (seq_key, prefix, date_pattern, length, current_val)
VALUES ('PROCESS_NO', 'PR', 'yyyyMMdd', 4, 0)
ON CONFLICT (seq_key) DO NOTHING;

-- ---------- 4. 自愈补列(幂等, 兼容部分初始化的库) ----------
ALTER TABLE plm_process_step ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ;
ALTER TABLE plm_process_route ADD COLUMN IF NOT EXISTS current_step_id BIGINT;
ALTER TABLE plm_process_route ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;
