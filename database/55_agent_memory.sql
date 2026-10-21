-- 55: 智能体自学习记忆(历史问答 + 多维度检索)
CREATE TABLE IF NOT EXISTS plm_agent_memory (
    id BIGSERIAL PRIMARY KEY,
    question TEXT NOT NULL,
    answer TEXT,
    tags VARCHAR(500),
    source VARCHAR(50) DEFAULT 'chat',
    score INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_plm_agent_memory_deleted ON plm_agent_memory(deleted);
CREATE INDEX IF NOT EXISTS idx_plm_agent_memory_question ON plm_agent_memory(question);
