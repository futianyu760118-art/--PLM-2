-- =============================================================================
-- HJ-PLM 增量 53b: 修正为 22 节点标准(D1: +外观/结构/电子)
-- 1) 补 3 个缺失节点 APPEARANCE/STRUCTURE/ELECTRONICS
-- 2) 修正后续节点 seq(插入 3 节点后 +3)
-- 3) BOM 节点名统一为「基础BOM」
-- =============================================================================

-- 1. 补 3 节点
INSERT INTO plm_project_node (project_id, project_no, node_code, seq, node_name, is_key, status, evidence_count, edit_count, source_system)
SELECT p.id, p.project_no, n.code, n.seq, n.name, n.key, 'NOT_SET', 0, 0, 'EBMS'
FROM plm_project p
CROSS JOIN (VALUES ('APPEARANCE',8,'外观',0), ('STRUCTURE',9,'结构',0), ('ELECTRONICS',10,'电子',0)) AS n(code, seq, name, key)
ON CONFLICT (project_id, node_code) DO NOTHING;

-- 2. 修正 seq(22 节点标准)
UPDATE plm_project_node SET seq=11 WHERE node_code='MOLD';
UPDATE plm_project_node SET seq=12 WHERE node_code='MOLD_SAMPLE';
UPDATE plm_project_node SET seq=13 WHERE node_code='PACKAGING';
UPDATE plm_project_node SET seq=14 WHERE node_code='ELEC_TRIAL';
UPDATE plm_project_node SET seq=15 WHERE node_code='RD_TRIAL';
UPDATE plm_project_node SET seq=16 WHERE node_code='ENG_TRIAL';
UPDATE plm_project_node SET seq=17 WHERE node_code='PROD_TRIAL';
UPDATE plm_project_node SET seq=18 WHERE node_code='TEST_REPORT';
UPDATE plm_project_node SET seq=19 WHERE node_code='TECH_TRANSFER';
UPDATE plm_project_node SET seq=20 WHERE node_code='SHIPMENT';
UPDATE plm_project_node SET seq=21 WHERE node_code='REVIEW';
UPDATE plm_project_node SET seq=22 WHERE node_code='OTHER';

-- 3. BOM 名称统一
UPDATE plm_project_node SET node_name='基础BOM' WHERE node_code='BOM';
