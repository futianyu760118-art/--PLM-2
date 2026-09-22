-- 模块K: 外网分享链接
CREATE TABLE plm_share_link (
    id BIGSERIAL PRIMARY KEY,
    share_token VARCHAR(64) UNIQUE NOT NULL,        -- 分享令牌
    part_no VARCHAR(64),                             -- 料号
    model3d_id BIGINT,                               -- 3D模型ID
    title VARCHAR(200),                              -- 分享标题
    creator VARCHAR(64) NOT NULL,                    -- 创建人
    expire_at TIMESTAMP NOT NULL,                    -- 过期时间
    max_views INT,                                   -- 最大访问次数(空=不限)
    view_count INT DEFAULT 0,                        -- 已访问次数
    status SMALLINT DEFAULT 1,                       -- 1有效 0失效
    allow_download SMALLINT DEFAULT 0,               -- 是否允许下载(外网默认禁止)
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_share_token ON plm_share_link(share_token);
COMMENT ON TABLE plm_share_link IS '外网临时分享链接(脱敏3D外观预览)';

-- 分享访问日志
CREATE TABLE plm_share_access_log (
    id BIGSERIAL PRIMARY KEY,
    share_id BIGINT NOT NULL REFERENCES plm_share_link(id) ON DELETE CASCADE,
    visitor_ip VARCHAR(64),
    user_agent VARCHAR(500),
    referer VARCHAR(500),
    accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE plm_share_access_log IS '分享链接访问日志';
