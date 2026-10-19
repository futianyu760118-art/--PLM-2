-- 51: 知识库(对接腾讯 ima 导出资料, 供智能体问答检索)
CREATE TABLE IF NOT EXISTS plm_knowledge (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    content TEXT,
    file_id BIGINT,
    source VARCHAR(100),
    created_by VARCHAR(100),
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_plm_knowledge_deleted ON plm_knowledge(deleted);
