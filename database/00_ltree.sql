-- 恒剑光电 PLM V4.0 一键初始化 (Docker 部署用)
-- 先创建 ltree 扩展,否则 plm_bom_item 建表会失败

CREATE EXTENSION IF NOT EXISTS ltree;
