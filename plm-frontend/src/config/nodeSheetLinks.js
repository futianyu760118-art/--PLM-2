/**
 * 项目明细表节点 → 工作表 一一对应关系。
 * 数据流: 项目明细表节点 -> 打开对应工作表填写 -> 汇总回节点/项目跟踪。
 * 未在此表列出的节点 = 系统暂无可填写工作表(见 docs)。
 */
export const NODE_SHEET_LINKS = {
  PLAN:          { sheet: 'plan',     label: '计划表(甘特)' },
  BOM:           { sheet: 'basebom',  label: '基础BOM(5)' },
  SPEC:          { sheet: 'spec',     label: '规格书' },
  CONFIG:        { sheet: 'config',   label: '配置表' },
  MOLD_REVIEW:   { sheet: 'review',   label: '评审单', preset: { review_type: '开模评审' } },
  APPEARANCE:    { sheet: 'review',   label: '评审单', preset: { review_type: '外观评审' } },
  STRUCTURE:     { sheet: 'review',   label: '评审单', preset: { review_type: '结构评审' } },
  ELECTRONICS:   { sheet: 'review',   label: '评审单', preset: { review_type: '电子评审' } },
  HAND_SAMPLE:   { sheet: 'review',   label: '评审单', preset: { review_type: '样机评审' } },
  MOLD_SAMPLE:   { sheet: 'review',   label: '评审单', preset: { review_type: '样品评审' } },
  TECH_TRANSFER: { sheet: 'review',   label: '评审单', preset: { review_type: '技转评审' } },
  ELEC_TRIAL:    { sheet: 'trial',    label: '试产报告', preset: { trial_type: '电子试产' } },
  RD_TRIAL:      { sheet: 'trial',    label: '试产报告', preset: { trial_type: '研发试产' } },
  ENG_TRIAL:     { sheet: 'trial',    label: '试产报告', preset: { trial_type: '工程试产' } },
  PROD_TRIAL:    { sheet: 'trial',    label: '试产报告', preset: { trial_type: '生产试产' } },
  TEST_REPORT:   { sheet: 'test',     label: '送检/测试报告' },
  SHIPMENT:      { sheet: 'shipment', label: '出货/验货' }
}

/** 未对应工作表的节点(系统暂未建表) */
export const UNMAPPED_NODES = {
  MOLD_DRAWING: '图纸/档案(未建工作表)',
  MOLD: '对应 模具模块(plm_mold)', PACKAGING: '包装图纸(未建工作表)',
  REVIEW: '对应 项目复盘模块', OTHER: '其他(自定义)'
}

export function nodeSheet(code) { return NODE_SHEET_LINKS[code] || null }
