// =============================================================================
// EBMS 接收端参考实现: POST /api/sync/events
// 放到 EBMS 后端 backend/routes/ 下, 并在 app.js/index.js 挂载:
//   app.use('/api', require('./routes/sync-events'));
// PLM 侧通过 OutboxPublisherJob 把领域事件 POST 到这里(带 X-Webhook-Signature 可选校验)。
// =============================================================================
const express = require('express');
const router = express.Router();
const { getTable, now } = require('../db');

// 事件 aggregateType -> EBMS 表名
const TYPE_TABLE = {
  'PROJECT': 'projects',
  'MATERIAL': 'materials',
  'INITIATION': 'rd_project_initiation',
  'PROJECT_NODE': 'rd_project_progress',
  'ECN': 'ecn',
  'BOM': 'bom',
  'MOLD': 'mold',
  'QUALITY': 'quality',
  'FEEDBACK': 'feedback'
};

// PLM 事件 payload 字段 -> EBMS 表字段(按 EBMS 实际表结构增补)
const FIELD_MAP = {
  'PROJECT': {
    projectNo: 'project_no', projectName: 'project_name', customerName: 'customer_name',
    projectType: 'project_type', projectLevel: 'project_level', urgency: 'urgency',
    owner: 'owner', department: 'department', startDate: 'start_date',
    targetDate: 'target_date', closeDate: 'close_date', currentGate: 'current_stage',
    projectAmount: 'project_amount', orderAmount: 'order_amount', investAmount: 'invest_amount',
    annualOrder: 'annual_order', marketDate: 'market_date', status: 'status', remarks: 'remarks'
  },
  'MATERIAL': {
    partNo: 'part_no', materialName: 'material_name', materialType: 'material_type',
    status: 'status', versionNo: 'version_no', unit: 'unit', projectNo: 'project_no'
  }
};

function mapFields(type, data) {
  const m = FIELD_MAP[type] || {};
  const out = {};
  for (const [k, v] of Object.entries(data || {})) {
    if (m[k]) out[m[k]] = v;
  }
  return out;
}

router.post('/sync/events', (req, res) => {
  try {
    const { eventType, aggregateType, aggregateId, data } = req.body || {};
    const table = TYPE_TABLE[aggregateType];
    if (!table || !data) return res.json({ ok: true, skipped: true });

    const t = getTable(table);
    t._invalidate();
    const keyField = { PROJECT: 'project_no', MATERIAL: 'part_no' }[aggregateType] || 'project_no';
    const mapped = mapFields(aggregateType, data);
    const keyVal = aggregateId || mapped[keyField];
    const existing = t.all().find(r => String(r[keyField]) === String(keyVal));

    if (existing) {
      t.update(existing.id, Object.assign({}, mapped, { updated_at: now() }));
    } else {
      t.insert(Object.assign({}, mapped, { created_at: now(), updated_at: now() }));
    }
    t._invalidate();
    res.json({ ok: true, upserted: !existing });
  } catch (e) {
    res.status(500).json({ ok: false, error: String(e && e.message || e) });
  }
});

module.exports = router;
