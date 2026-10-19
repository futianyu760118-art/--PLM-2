-- =============================================================================
-- HJ-PLM 增量 53: EBMS 研发项目数据收尾
-- 1) 给所有项目补全 19 个进度节点(缺失的标 NOT_SET, 已导入的有值节点保留)
-- 2) EBMS 中文阶段名 → G0-G8 阶段门映射
-- =============================================================================

-- 1. 补全 19 节点(ON CONFLICT 保留已导入的有值节点)
INSERT INTO plm_project_node (project_id, project_no, node_code, seq, node_name, is_key, status, evidence_count, edit_count, source_system)
SELECT p.id, p.project_no, n.code, n.seq, n.name, n.key, 'NOT_SET', 0, 0, 'EBMS'
FROM plm_project p
CROSS JOIN (VALUES
    ('PLAN',1,'计划表',0), ('BOM',2,'BOM',0), ('SPEC',3,'规格书',0),
    ('CONFIG',4,'配置表',0), ('MOLD_DRAWING',5,'模具图纸',0),
    ('MOLD_REVIEW',6,'开模评审',1), ('HAND_SAMPLE',7,'手样',0),
    ('MOLD',8,'模具',1), ('MOLD_SAMPLE',9,'模样',0),
    ('PACKAGING',10,'包装设计',0), ('ELEC_TRIAL',11,'电试',0),
    ('RD_TRIAL',12,'研试',1), ('ENG_TRIAL',13,'工试',1),
    ('PROD_TRIAL',14,'生试',1), ('TEST_REPORT',15,'测试报告',1),
    ('TECH_TRANSFER',16,'技转',1), ('SHIPMENT',17,'出货',0),
    ('REVIEW',18,'复盘',0), ('OTHER',19,'其他',0)
) AS n(code, seq, name, key)
ON CONFLICT (project_id, node_code) DO NOTHING;

-- 2. EBMS 中文阶段名 → G0-G8 门
UPDATE plm_project SET current_gate='G0' WHERE current_gate='预项目';
UPDATE plm_project SET current_gate='G1' WHERE current_gate='方案';
UPDATE plm_project SET current_gate='G2' WHERE current_gate='手样';
UPDATE plm_project SET current_gate='G3' WHERE current_gate='模具样';
UPDATE plm_project SET current_gate='G5' WHERE current_gate='工试';
UPDATE plm_project SET current_gate='G8' WHERE current_gate='完成';
-- 暂停: 无对应门, 保留; gate_status 标 BLOCKED 表示停滞
UPDATE plm_project SET gate_status='BLOCKED' WHERE current_gate='暂停';
