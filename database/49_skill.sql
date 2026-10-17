-- =============================================================================
-- HJ-PLM 增量 49: 技能模块（做过一次→下次调用；经验总结；智能体自动化制作）
-- =============================================================================
CREATE TABLE IF NOT EXISTS plm_skill (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,             -- 技能名称
    description TEXT,                               -- 一句话说明
    content     TEXT,                               -- 经验/步骤(可复用流程)
    tags        VARCHAR(256),                       -- 逗号分隔标签/触发关键词
    source      VARCHAR(16) NOT NULL DEFAULT 'MANUAL', -- MANUAL=手工, AGENT=智能体生成
    usage_count INT NOT NULL DEFAULT 0,             -- 调用次数
    created_by  VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted     SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE plm_skill IS '技能库: 可复用经验/流程, 支持智能体自动生成';
CREATE INDEX IF NOT EXISTS idx_skill_name ON plm_skill (name);
CREATE INDEX IF NOT EXISTS idx_skill_created ON plm_skill (created_at);
