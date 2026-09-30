-- =====================================================================
-- 登录审计日志（D2 / F15 / AC15.1）
-- 依据：PAND-118 阶段4 测试报告缺陷 D2 —— 登录成功/失败均无留痕
-- 幂等：IF NOT EXISTS，可重复执行
-- 说明：本表只记录登录事件结果，绝不写入口令或口令派生材料
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_login_log (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(64),                 -- 尝试登录的用户名（失败时同样记录）
    user_id     BIGINT,                      -- 认证通过后的用户主键；失败为 NULL
    result      SMALLINT NOT NULL DEFAULT 1, -- 1成功 0失败
    ip          VARCHAR(64),                 -- 客户端 IP
    device      VARCHAR(255),                -- User-Agent
    message     VARCHAR(255),                -- 失败原因/结果描述（不含口令）
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_login_log IS '登录审计日志（成功/失败各一条，用于 AC15.1）';
COMMENT ON COLUMN sys_login_log.result IS '1=登录成功 0=登录失败';

CREATE INDEX IF NOT EXISTS idx_login_log_username ON sys_login_log(username);
CREATE INDEX IF NOT EXISTS idx_login_log_created ON sys_login_log(created_at);
CREATE INDEX IF NOT EXISTS idx_login_log_result ON sys_login_log(result);
