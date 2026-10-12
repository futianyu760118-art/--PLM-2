-- =============================================================================
-- HJ-PLM 增量 33: 全局反馈按钮 —— 反馈唯一事实源
-- 依据: PAND-146 需求与 AC (F1-F16 / E1-E5) + PAND-147 架构方案
-- 内容: 反馈表 + 索引 + 字典种子 + 编号序列(separator 扩展) + 菜单权限种子
-- =============================================================================

-- ---------- 1. 反馈表(唯一反馈事实源,不建第二套身份/权限/事实体系) ----------
CREATE TABLE IF NOT EXISTS plm_feedback (
    id               BIGSERIAL PRIMARY KEY,
    feedback_no      VARCHAR(32)  NOT NULL UNIQUE,             -- 反馈编号 FB-YYYYMMDD-NNNN(服务端生成)
    type             VARCHAR(20)  NOT NULL,                    -- BUG/SUGGEST/REQUIREMENT(字典 feedback_type)
    content          TEXT         NOT NULL,                    -- 反馈内容,trim 后 10-2000 字符(应用层校验)
    expected_result  TEXT,                                     -- 期望结果,0-2000;type=REQUIREMENT 时必填
    page_path        VARCHAR(512) NOT NULL,                    -- 来源页面路由路径(前端只读带入)
    page_hash        CHAR(32)     NOT NULL,                    -- md5(page_path),去重索引用
    contact          VARCHAR(100),                             -- 可选联系方式(隐私字段,仅总控可见)
    content_hash     CHAR(32)     NOT NULL,                    -- 去重指纹 md5(trim(content))
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- 字典 feedback_status,PENDING/EVALUATING/INITIATED/RETURNED
    submitter_id     BIGINT       NOT NULL REFERENCES sys_user(id), -- 提交人(唯一身份源 sys_user)
    related_issue_no VARCHAR(64),                              -- 总控立项后回填的研发 issue 编号
    evaluate_note    TEXT,                                     -- 评估/分析结论 或 退回原因(条款+原因)
    evaluated_by     BIGINT,                                   -- 处理人(总控)
    evaluated_at     TIMESTAMP,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT   DEFAULT 0
);
COMMENT ON TABLE plm_feedback IS '用户反馈表(唯一反馈事实源)';

-- 去重: 同一提交人 + 同类型 + 同页面 + 同内容(AC F11)
CREATE INDEX IF NOT EXISTS idx_feedback_dedup     ON plm_feedback (submitter_id, type, page_hash, content_hash, created_at);
CREATE INDEX IF NOT EXISTS idx_feedback_status    ON plm_feedback (status);
CREATE INDEX IF NOT EXISTS idx_feedback_created   ON plm_feedback (created_at);
CREATE INDEX IF NOT EXISTS idx_feedback_submitter ON plm_feedback (submitter_id);

-- ---------- 2. 字典种子(复用 sys_dict,不建第二套字典) ----------
INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order) VALUES
('feedback_type',   '缺陷', 'BUG',         1),
('feedback_type',   '建议', 'SUGGEST',     2),
('feedback_type',   '需求', 'REQUIREMENT', 3);

INSERT INTO sys_dict (dict_type, dict_label, dict_value, sort_order) VALUES
('feedback_status', '待分析', 'PENDING',    1),
('feedback_status', '评估中', 'EVALUATING', 2),
('feedback_status', '已立项', 'INITIATED',  3),
('feedback_status', '已退回', 'RETURNED',   4);

-- ---------- 3. 编号序列: 复用 sys_sequence,仅扩展分隔符 ----------
-- 现有 nextNo 产出 prefix+yyyyMMdd+seq(无分隔符);反馈编号要求 FB-YYYYMMDD-NNNN。
-- 新增 separator 列并默认 '' ,既有 PART_NO/ECN_NO/BOM_NO 输出保持不变(零回归)。
ALTER TABLE sys_sequence ADD COLUMN IF NOT EXISTS separator VARCHAR(4) DEFAULT '';

INSERT INTO sys_sequence (seq_key, prefix, date_pattern, length, separator)
VALUES ('FEEDBACK_NO', 'FB-', 'yyyyMMdd', 4, '-')
ON CONFLICT (seq_key) DO NOTHING;

-- ---------- 4. 菜单权限种子(复用 sys_permission,不新建角色) ----------
-- 总控职责落到既有 ADMIN 角色;反馈管理挂在系统管理(107)下,id 段沿用 107x。
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order)
VALUES (1076, 107, 'feedback:manage', '反馈管理', 1, '/admin/feedback', 'feedback/manage', 'ChatDotRound', 6)
ON CONFLICT (id) DO NOTHING;

-- ADMIN 全量权限补授: 02 号种子的 "SELECT ... ADMIN" 是初始化时的一次性快照,
-- 之后新增的权限不在其中, 故此处按 21_enum_fix 的既有做法重新授予当前全部权限。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p WHERE r.role_code = 'ADMIN'
ON CONFLICT DO NOTHING;
