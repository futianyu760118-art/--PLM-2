-- =====================================================================
-- EBMS → PLM2 用户体系数据迁移：schema 扩展
-- 依据：PAND-116 阶段2 架构交付 ADR-1/ADR-2/第5节
-- 幂等：全部 IF NOT EXISTS，可重复执行
-- 说明：本脚本只做结构扩展，不写入任何账号/口令数据
-- =====================================================================

-- ---------- 1. sys_user 迁移支撑列 ----------
-- legacy_password: EBMS scrypt 口令串暂存位，登录命中后透明重哈希为 BCrypt 并清空（ADR-2）
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS legacy_password VARCHAR(256);
-- must_change_password: 1=下次登录强制改密（空口令 / 强制重置分支）
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS must_change_password SMALLINT DEFAULT 0;
-- source: 账号溯源标记，NULL=PLM2 原生；'EBMS'=EBMS 迁入
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS source VARCHAR(32);
-- migration_run_id: 迁入批次号，用于按批精确回滚（第5节 回滚策略）
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS migration_run_id VARCHAR(64);

COMMENT ON COLUMN sys_user.legacy_password IS 'EBMS 迁移遗留口令(scrypt串)；登录校验命中后透明重哈希为 BCrypt 并清空';
COMMENT ON COLUMN sys_user.must_change_password IS '1=下次登录强制改密';
COMMENT ON COLUMN sys_user.source IS '账号来源：NULL=PLM2原生，EBMS=EBMS迁入';
COMMENT ON COLUMN sys_user.migration_run_id IS 'EBMS 迁入批次号(run_id)，用于精确回滚';

-- 回滚/幂等识别走 source + migration_run_id
CREATE INDEX IF NOT EXISTS idx_sys_user_source ON sys_user (source);
CREATE INDEX IF NOT EXISTS idx_sys_user_migration_run ON sys_user (migration_run_id);

-- sys_role 溯源标记：新增角色(builtin=0)需可识别、可回滚（ADR-3）
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS source VARCHAR(32);
COMMENT ON COLUMN sys_role.source IS '角色来源：NULL=PLM2原生，EBMS=迁移新增';

-- 角色映射表落库备查（ADR-3 授权清单可追溯）
CREATE TABLE IF NOT EXISTS sys_role_mapping (
    id          BIGSERIAL PRIMARY KEY,
    source_system VARCHAR(32) NOT NULL DEFAULT 'EBMS',
    source_role   VARCHAR(64) NOT NULL,
    target_role   VARCHAR(64) NOT NULL,
    note          VARCHAR(255),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (source_system, source_role)
);
COMMENT ON TABLE sys_role_mapping IS 'EBMS→PLM2 角色映射表（ADR-3）';

-- ---------- 2. 迁移批次审计表 ----------
CREATE TABLE IF NOT EXISTS migration_run (
    run_id          VARCHAR(64) PRIMARY KEY,
    source_system   VARCHAR(32) NOT NULL DEFAULT 'EBMS',
    mode            VARCHAR(32),            -- hash-once | force-reset
    dry_run         SMALLINT DEFAULT 0,
    phase           VARCHAR(32) NOT NULL,   -- users | roles | user_roles | done
    source_count    INT DEFAULT 0,
    target_count    INT DEFAULT 0,
    conflict_count  INT DEFAULT 0,
    skipped_count   INT DEFAULT 0,
    detail          TEXT,
    started_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at     TIMESTAMP,
    status          VARCHAR(32),            -- RUNNING | SUCCESS | FAILED | ROLLED_BACK
    rolled_back_at  TIMESTAMP
);
COMMENT ON TABLE migration_run IS 'EBMS→PLM2 迁移批次审计（幂等/对账/回滚依据）';

CREATE INDEX IF NOT EXISTS idx_migration_run_status ON migration_run (status);
