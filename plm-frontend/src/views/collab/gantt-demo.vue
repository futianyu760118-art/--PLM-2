<template>
  <div class="page-container">
    <el-card>
      <div class="hd">
        <h3>研发项目甘特图 Demo 套装</h3>
        <div class="hd-sub">
          表格编号：HJ/ED/R-04-DEMO ｜ 项目编号 <b>XM2026-001</b> 星耀二代50W夹子灯（JWA21-B1WA50C-001）｜ 项目经理 张工
          ｜ 计划周期 2026-10-08 ~ 2026-12-25 ｜ 数据截止 2026-10-20（手板阶段·进行中）
        </div>
      </div>

      <el-tabs v-model="activeType">
        <el-tab-pane v-for="t in TYPES" :key="t.key" :label="t.label" :name="t.key" />
      </el-tabs>

      <div class="g-desc">{{ curType.title }}　·　{{ curType.desc }}</div>
      <div class="legend" v-html="curType.legend"></div>

      <div class="gantt">
        <div class="g-left">
          <div class="gl-head-row">
            <div v-for="(c, i) in curType.cols" :key="i" class="gl-hcell" :style="{ width: c.w + 'px' }">{{ c.label }}</div>
          </div>
          <div v-for="(r, i) in rows" :key="i" class="gl-row" :class="[r.cls || '', r.indent ? 'indent' : '']">
            <div v-for="(c, ci) in curType.cols" :key="ci" class="gl-cell" :style="{ width: c.w + 'px' }"
              :title="r.cells[ci] && String(r.cells[ci]).replace(/<[^>]*>/g, '')">{{ r.cells[ci] || '' }}</div>
          </div>
        </div>
        <div class="g-right">
          <div class="gr" :style="{ width: totalWidth + 'px' }">
            <div class="gr-axis">
              <div class="gr-month-row">
                <div v-for="m in months" :key="m.key" class="gr-month" :style="{ left: m.left + 'px', width: m.width + 'px' }">{{ m.label }}</div>
              </div>
              <div class="gr-day-row">
                <div v-for="d in days" :key="d.key" class="gr-day" :class="{ first: d.first }" :style="{ left: d.left + 'px' }">{{ d.label }}</div>
              </div>
            </div>
            <div class="gr-body" :style="{ height: rows.length * ROWH + 'px' }">
              <div v-for="(c, i) in weekendCols" :key="'w' + i" class="wk" :style="{ left: c.left + 'px', width: DAYW + 'px', height: rows.length * ROWH + 'px' }"></div>
              <div class="today" :style="{ left: todayLeft + 'px', height: rows.length * ROWH + 'px' }"><span>今天</span></div>
              <template v-for="(r, i) in rows" :key="'r' + i">
                <div v-for="(b, bi) in (r.bars || [])" :key="'b' + bi" class="bar" :class="b.cls" :style="barStyle(b, i)" :title="b.title">{{ b.text }}</div>
                <div v-for="(m, mi) in (r.ms || [])" :key="'m' + mi" class="ms" :class="m.cls" :style="msStyle(m, i)" :title="m.title"></div>
              </template>
            </div>
          </div>
        </div>
      </div>

      <div class="foot" v-html="curType.foot"></div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const ROWH = 34
const DAYW = 7
const DAY = 86400000
const D = (s) => Math.floor(new Date(s + 'T00:00:00Z').getTime() / DAY)
const RANGE_START = D('2026-09-28')
const RANGE_END = D('2027-01-03')
const TODAY = '2026-10-20'
const x = (date) => (D(date) - RANGE_START) * DAYW
const w = (s, e) => (D(e) - D(s) + 1) * DAYW
const dur = (s, e) => D(e) - D(s) + 1

const STAGES = {
  s1: { name: '手板阶段', color: '#4472C4', range: '10/08 ~ 11/08' },
  s2: { name: '模具阶段', color: '#ED7D31', range: '10/24 ~ 12/18' },
  s3: { name: '生产阶段', color: '#548235', range: '12/17 ~ 12/25' }
}

const TASKS = [
  { n: 1, code: '01', name: '立项', owner: '研发/业务', stage: 's1', s: '2026-10-08', e: '2026-10-10', ev: '《立项书》', confirm: '研发中心、销售中心、财务部、总经理' },
  { n: 2, code: '02', name: '设计输入', owner: '项目负责人', stage: 's1', s: '2026-10-10', e: '2026-10-15', ev: '《设计开发输入清单》/《配置表》', confirm: '业务部、采购部、工程部、品质部、研发中心' },
  { n: 3, code: '03', name: '外观手板', owner: '结构工程师', stage: 's1', s: '2026-10-13', e: '2026-10-20', ev: '《外观评审表》', confirm: '部门主管知悉，业务部确认' },
  { n: 4, code: '04', name: '结构评审', owner: '结构工程师', stage: 's1', s: '2026-10-21', e: '2026-10-23', ev: '《结构评审表》', confirm: '工程部、品质部、生产部、研发中心' },
  { n: 5, code: '05', name: '电子样品', owner: '电子工程师', stage: 's1', s: '2026-10-16', e: '2026-10-27', ev: '《成本BOM》', confirm: '电子工程师、结构工程师' },
  { n: 6, code: '06', name: '手板样品', owner: '结构工程师', stage: 's1', s: '2026-10-28', e: '2026-11-03', ev: '《样机评审表》《设计验证报告》', confirm: '业务部、采购部、工程部、品质部、生产部、研发中心' },
  { n: 7, code: '07', name: '产品资料', owner: '项目负责人', stage: 's1', s: '2026-11-05', e: '2026-11-08', ev: '《规格书》《配置表》《成本BOM》', confirm: '研发主管' },
  { n: 8, code: '08', name: '模具图纸(二维图)', owner: '结构工程师', stage: 's2', s: '2026-10-24', e: '2026-11-05', ev: '《开修模评审表》、2D图纸', confirm: '研发主管、模具师傅' },
  { n: 9, code: '09', name: '模具T0', owner: '结构工程师', stage: 's2', s: '2026-11-16', e: '2026-11-22', ev: '《问题清单》', confirm: '研发主管、模具师傅' },
  { n: 10, code: '10', name: '模具T1', owner: '结构工程师', stage: 's2', s: '2026-11-25', e: '2026-12-01', ev: '《问题清单》', confirm: '研发主管、模具师傅' },
  { n: 11, code: '11', name: '模具样品', owner: '结构工程师', stage: 's2', s: '2026-12-02', e: '2026-12-06', ev: '《样品评审表》《模具验收书》', confirm: '业务部、采购部、工程部、品质部、生产部、研发中心' },
  { n: 12, code: '12', name: '包装设计及确认', owner: '包装工程师', stage: 's2', s: '2026-11-09', e: '2026-11-18', ev: '《包装平面图纸》', confirm: '项目负责人' },
  { n: 13, code: '13', name: '测试报告', owner: '品质工程师', stage: 's2', s: '2026-12-07', e: '2026-12-11', ev: '《新产品测试报告》', confirm: '项目负责人' },
  { n: 14, code: '14', name: '电子试产', owner: '电子工程师', stage: 's2', s: '2026-12-07', e: '2026-12-10', ev: '《试产报告》《预测单》《基础BOM表》', confirm: '工程部、品质部、研发中心' },
  { n: 15, code: '15', name: '研发试产', owner: '结构工程师', stage: 's2', s: '2026-12-07', e: '2026-12-10', ev: '《试产报告》《基础BOM表》', confirm: '工程部、品质部、生产部、研发中心' },
  { n: 16, code: '16', name: '技转', owner: '结构工程师', stage: 's2', s: '2026-12-14', e: '2026-12-16', ev: '《技转评审报告》《新产品测试报告》《BOM表》', confirm: '业务部、工程部、品质部、生产部、研发中心' },
  { n: 17, code: '17', name: '产品宣传资料', owner: '包装工程师', stage: 's2', s: '2026-11-19', e: '2026-12-03', ev: 'PPT、宣传彩页', confirm: '项目负责人、研发主管' },
  { n: 18, code: '18', name: '产品发布', owner: '项目负责人', stage: 's2', s: '2026-12-17', e: '2026-12-18', ev: '/', confirm: '研发主管' },
  { n: 19, code: '19', name: '工程试产', owner: '工艺工程师', stage: 's3', s: '2026-12-17', e: '2026-12-21', ev: '《SOP》《客户BOM》《工程试产报告》', confirm: '项目负责人，生产主管' },
  { n: 20, code: '20', name: '生产试产', owner: '生产主管', stage: 's3', s: '2026-12-22', e: '2026-12-24', ev: '《生产试产报告》、样机10台', confirm: '项目负责人' },
  { n: 21, code: '21', name: '复盘', owner: '项目负责人', stage: 's3', s: '2026-12-25', e: '2026-12-25', ev: '《复盘报告》', confirm: '项目负责人、研发主管' }
]

const MILESTONES = [
  { code: 'M1', name: '立项批准', stage: 's1', d: '2026-10-10', st: '已达成', cls: 'done', owner: '研发/业务', ev: '《立项书》' },
  { code: 'M2', name: '设计输入冻结', stage: 's1', d: '2026-10-15', st: '已达成·延期1天', cls: 'delay', owner: '项目负责人', ev: '《设计开发输入清单》/《配置表》' },
  { code: 'M3', name: '外观手板确认', stage: 's1', d: '2026-10-20', st: '进行中', cls: 'ongoing', owner: '结构工程师', ev: '《外观评审表》' },
  { code: 'M4', name: '结构评审通过', stage: 's1', d: '2026-10-23', st: '未开始', cls: 'todo', owner: '结构工程师', ev: '《结构评审表》' },
  { code: 'M5', name: '手板样品确认', stage: 's1', d: '2026-11-03', st: '未开始', cls: 'todo', owner: '结构工程师', ev: '《样机评审表》《设计验证报告》' },
  { code: 'M6', name: '模具图纸冻结', stage: 's2', d: '2026-11-05', st: '未开始', cls: 'todo', owner: '结构工程师', ev: '《开修模评审表》、2D图纸' },
  { code: 'M7', name: '模具T1通过', stage: 's2', d: '2026-12-01', st: '未开始', cls: 'todo', owner: '结构工程师', ev: '《问题清单》《模具验收书》' },
  { code: 'M8', name: '模具样品确认', stage: 's2', d: '2026-12-06', st: '未开始', cls: 'todo', owner: '结构工程师', ev: '《样品评审表》《样品确认单》' },
  { code: 'M9', name: '测试报告通过', stage: 's2', d: '2026-12-11', st: '未开始', cls: 'todo', owner: '品质工程师', ev: '《新产品测试报告》' },
  { code: 'M10', name: '技转完成', stage: 's2', d: '2026-12-16', st: '未开始', cls: 'todo', owner: '结构工程师', ev: '《技转评审报告》《BOM表》' },
  { code: 'M11', name: '产品发布', stage: 's3', d: '2026-12-18', st: '未开始', cls: 'todo', owner: '项目负责人', ev: 'PPT、宣传彩页' },
  { code: 'M12', name: '生产试产通过+复盘', stage: 's3', d: '2026-12-25', st: '未开始', cls: 'todo', owner: '项目负责人', ev: '《生产试产报告》《复盘报告》' }
]

const SPRINTS = [
  { code: 'S1', r: '2026-10-08 ~ 10-21', s: '2026-10-08', e: '2026-10-21', goal: '需求与方案定型', tasks: [1, 2], st: '已完成' },
  { code: 'S2', r: '2026-10-22 ~ 11-04', s: '2026-10-22', e: '2026-11-04', goal: '结构与手板验证', tasks: [3, 4, 6], st: '进行中' },
  { code: 'S3', r: '2026-11-05 ~ 11-18', s: '2026-11-05', e: '2026-11-18', goal: '电子与工装', tasks: [5, 8, 12], st: '未开始' },
  { code: 'S4', r: '2026-11-19 ~ 12-02', s: '2026-11-19', e: '2026-12-02', goal: '模具验证', tasks: [9, 10, 11, 17], st: '未开始' },
  { code: 'S5', r: '2026-12-03 ~ 12-16', s: '2026-12-03', e: '2026-12-16', goal: '认证与技转', tasks: [13, 14, 15, 16], st: '未开始' },
  { code: 'S6', r: '2026-12-17 ~ 12-25', s: '2026-12-17', e: '2026-12-25', goal: '量产准备', tasks: [18, 19, 20, 21], st: '未开始' }
]

const ROLES = [
  { id: 'E002', name: '研发/业务', dept: '销售中心/研发中心' },
  { id: 'E002', name: '项目负责人', dept: '研发中心' },
  { id: 'E003', name: '结构工程师', dept: '研发中心' },
  { id: 'E004', name: '电子工程师', dept: '研发中心' },
  { id: 'E009', name: '包装工程师', dept: '研发中心' },
  { id: 'E010', name: '品质工程师', dept: '品质部' },
  { id: 'E011', name: '工艺工程师', dept: '工程部' },
  { id: 'E012', name: '生产主管', dept: '生产部' }
]

const DEPS = {
  1: { pre: '—', type: '—', float: 0, cp: true },
  2: { pre: '1(SS+2)', type: 'SS', float: 0, cp: true },
  3: { pre: '2(SS+3)', type: 'SS', float: 0, cp: true },
  4: { pre: '3(FS)', type: 'FS', float: 0, cp: true },
  5: { pre: '2(FS)', type: 'FS', float: 43, cp: false },
  6: { pre: '4(FS)、5(FS)', type: 'FS、FS', float: 47, cp: false },
  7: { pre: '6(SS+8)', type: 'SS', float: 47, cp: false },
  8: { pre: '4(FS)', type: 'FS', float: 0, cp: true },
  9: { pre: '8(FS+10)', type: 'FS', float: 0, cp: true },
  10: { pre: '9(FS+2)', type: 'FS', float: 0, cp: true },
  11: { pre: '10(FS)', type: 'FS', float: 0, cp: true },
  12: { pre: '2(SS+30)', type: 'SS', float: 20, cp: false },
  13: { pre: '11(FS)', type: 'FS', float: 0, cp: true },
  14: { pre: '5(FS)、11(FS)', type: 'FS、FS', float: 3, cp: false },
  15: { pre: '11(FS)', type: 'FS', float: 3, cp: false },
  16: { pre: '13(FS+2)、14(FS)、15(FS)', type: 'FS、FS、FS', float: 0, cp: true },
  17: { pre: '12(FS)', type: 'FS', float: 20, cp: false },
  18: { pre: '16(FS)、17(FS)', type: 'FS、FS', float: 7, cp: false },
  19: { pre: '16(FS)', type: 'FS', float: 0, cp: true },
  20: { pre: '19(FS)', type: 'FS', float: 0, cp: true },
  21: { pre: '20(FS)', type: 'FS', float: 0, cp: true }
}

const ACTUAL = { 1: 1, 2: 1, 3: 0.85, 4: 0, 5: 0.4, 6: 0, 7: 0, 8: 0, 9: 0, 10: 0, 11: 0, 12: 0, 13: 0, 14: 0, 15: 0, 16: 0, 17: 0, 18: 0, 19: 0, 20: 0, 21: 0 }
const ACT_START = { 1: '2026-10-08', 2: '2026-10-10', 3: '2026-10-14', 5: '2026-10-16' }
const ACT_END = { 1: '2026-10-10', 2: '2026-10-16', 3: null, 5: null }

const LG_STAGE = '<span><i class="lg" style="background:#4472C4"></i>手板阶段</span><span><i class="lg" style="background:#ED7D31"></i>模具阶段</span><span><i class="lg" style="background:#548235"></i>生产阶段</span>'
const FOOT = '<div class="foot">编制：研发中心　审核：项目负责人　批准：总经理　｜　注：本表为演示数据（demo），排期按 XM2026-001 项目主表计划周期推演。</div>'

/* ============ 各图（返回 rows，每行含 cells 左表 + bars/ms 甘特） ============ */
function buildRoadmap() {
  return MILESTONES.map(m => ({
    cells: [STAGES[m.stage].name, m.code, m.name, m.d, m.owner, m.ev, m.st],
    ms: [{ d: m.d, cls: m.cls, title: `${m.code} ${m.name} · ${m.d} · ${m.st}` }]
  }))
}
function buildWaterfall() {
  const rows = []
  let cur = ''
  TASKS.forEach(t => {
    if (t.stage !== cur) {
      rows.push({ cells: [STAGES[t.stage].name, '', '', '', '', '', '', ''], cls: 'stage', bars: [{ s: t.s, e: t.e, cls: 'dark', text: STAGES[t.stage].range }] })
      cur = t.stage
    }
    rows.push({ cells: ['', t.n, `XM2026-001-${t.code} ${t.name}`, t.owner, t.s, t.e, dur(t.s, t.e), t.confirm], bars: [{ s: t.s, e: t.e, cls: t.stage, text: `${t.s.slice(5)}~${t.e.slice(5)}`, title: `${t.name} · ${t.owner}\n${t.ev}` }] })
  })
  return rows
}
function buildSprint() {
  const rows = []
  SPRINTS.forEach(sp => {
    rows.push({ cells: [sp.code, sp.r, sp.goal, '', '', '', '', sp.st], cls: 'stage', bars: [{ s: sp.s, e: sp.e, cls: 'track', text: sp.code, title: `${sp.code} ${sp.r} ${sp.goal}` }] })
    sp.tasks.forEach(tn => {
      const t = TASKS.find(x => x.n === tn)
      rows.push({ cells: ['', '', '', `XM2026-001-${t.code} ${t.name}`, t.owner, t.s, t.e, ''], bars: [{ s: t.s, e: t.e, cls: t.stage, text: `${t.s.slice(5)}~${t.e.slice(5)}`, title: `${t.name} · ${t.owner}` }] })
    })
  })
  return rows
}
function buildHybrid() {
  const rows = []
  let cur = ''
  TASKS.forEach(t => {
    if (t.stage !== cur) {
      cur = t.stage
      rows.push({ cells: ['■ ' + STAGES[t.stage].name, '', '', '', '', '', ''], cls: 'stage', bars: [{ s: t.s, e: t.e, cls: 'dark', text: STAGES[t.stage].range }] })
    }
    const sp = SPRINTS.find(s => s.tasks.includes(t.n))
    rows.push({ cells: ['', sp ? sp.code : '', `XM2026-001-${t.code} ${t.name}`, t.owner, t.s, t.e, t.ev], bars: [{ s: t.s, e: t.e, cls: t.stage, text: `${t.s.slice(5)}~${t.e.slice(5)}`, title: `${sp ? sp.code : ''} ${t.name} · ${t.owner}\n${t.ev}` }] })
  })
  return rows
}
function buildBaseline() {
  const rows = []
  let cur = ''
  TASKS.forEach(t => {
    if (t.stage !== cur) {
      rows.push({ cells: [STAGES[t.stage].name, '', '', '', '', '', '', '', '', '', ''], cls: 'stage' })
      cur = t.stage
    }
    const pct = ACTUAL[t.n] || 0
    const as = ACT_START[t.n] || ''
    const ae = ACT_END[t.n] || ''
    const dev = ae ? (D(ae) - D(t.e)) : (pct > 0 && pct < 1 ? '—' : (pct === 0 ? '—' : '0'))
    const st = pct === 1 ? '按时完成' : pct > 0 ? '进行中' : '未开始'
    rows.push({ cells: ['', t.n, `XM2026-001-${t.code} ${t.name}`, t.owner, t.s, t.e, as, ae, Math.round(pct * 100) + '%', dev, st, t.ev], bars: [{ s: t.s, e: t.e, cls: 'dark', text: `基准 ${t.s.slice(5)}~${t.e.slice(5)}`, title: `基准 ${t.s}~${t.e}` }] })
    if (pct > 0) {
      const done = pct === 1
      rows.push({ cells: ['', '', '└ 实际执行', '', '', '', '', '', '', '', '', ''], indent: true, bars: [{ s: as || t.s, e: done ? (ae || t.e) : TODAY, cls: done ? 'done' : 'ongoing', text: `实际 ${Math.round(pct * 100)}%`, title: `实际执行 ${Math.round(pct * 100)}%` }] })
    } else {
      rows.push({ cells: ['', '', '└ 实际执行', '', '', '', '', '', '0%', '', '', ''], indent: true })
    }
  })
  return rows
}
function buildResource() {
  return ROLES.map(role => {
    const mine = TASKS.filter(t => t.owner === role.name)
    const days = mine.reduce((sum, t) => sum + dur(t.s, t.e), 0)
    let peak = 0
    for (let d = RANGE_START; d <= RANGE_END; d++) {
      const cnt = mine.filter(t => D(t.s) <= d && d <= D(t.e)).length
      if (cnt > peak) peak = cnt
    }
    const rate = Math.round(days / 79 * 100) + '%'
    const bars = []
    for (let d = RANGE_START; d <= RANGE_END; d++) {
      const iso = new Date(d * DAY).toISOString().slice(0, 10)
      const cnt = mine.filter(t => D(t.s) <= d && d <= D(t.e)).length
      if (cnt > 0) bars.push({ s: iso, e: iso, cls: 'h' + (cnt >= 3 ? 3 : cnt === 2 ? 2 : 1), text: cnt > 1 ? String(cnt) : '', title: `${role.name} ${iso} 并行 ${cnt} 项` })
    }
    return { cells: [role.id, role.name, role.dept, days + '天', peak + '项', rate, peak >= 2 ? '需错峰/加人' : ''], bars }
  })
}
function buildCritical() {
  return TASKS.map(t => {
    const dep = DEPS[t.n]
    const cp = dep.cp
    return { cells: [STAGES[t.stage].name, t.n, `XM2026-001-${t.code} ${t.name}`, t.owner, dep.pre, dep.type, t.s, t.e, dur(t.s, t.e), dep.float, cp ? '★' : ''], bars: [{ s: t.s, e: t.e, cls: cp ? 'cp' : t.stage, text: cp ? '★' : `${t.s.slice(5)}~${t.e.slice(5)}`, title: `${t.name} · 前置 ${dep.pre} · 浮动 ${dep.float}天${cp ? ' · 关键路径' : ''}` }] }
  })
}

const TYPES = [
  { key: 'roadmap', label: '①里程碑', title: '① 里程碑路标甘特图', desc: '一页纸对齐管理层/跨团队；只看 12 个 Gate，不看任务细节。', cols: [{ l: '阶段', w: 70 }, { l: '里程碑', w: 50 }, { l: '里程碑节点', w: 150 }, { l: '计划日期', w: 88 }, { l: '责任人', w: 80 }, { l: '判定依据/交付表单', w: 170 }, { l: '状态', w: 90 }], build: buildRoadmap, legend: '<span><i class="lg" style="background:#00B050"></i>已达成</span><span><i class="lg" style="background:#FFC000"></i>延期达成</span><span><i class="lg" style="background:#FFE699"></i>进行中</span><span><i class="lg" style="background:#D9D9D9"></i>未开始</span>' + LG_STAGE },
  { key: 'waterfall', label: '②瀑布', title: '② 瀑布/阶段式甘特图', desc: '项目组排期主表；阶段串行、每阶段有评审关卡。', cols: [{ l: '阶段', w: 70 }, { l: '序号', w: 38 }, { l: '工作项目', w: 160 }, { l: '责任人', w: 80 }, { l: '计划开始', w: 88 }, { l: '计划结束', w: 88 }, { l: '工期(天)', w: 52 }, { l: '确认人/会签', w: 170 }], build: buildWaterfall, legend: LG_STAGE + '<span><i class="lg" style="background:#A6A6A6"></i>阶段总条</span>' },
  { key: 'sprint', label: '③迭代', title: '③ 迭代式甘特图（Sprint/双周迭代）', desc: '需求多变时用；每迭代内是「设计-打样-测试」小循环。', cols: [{ l: '迭代', w: 40 }, { l: '迭代周期', w: 120 }, { l: '迭代目标', w: 150 }, { l: '工作项目', w: 170 }, { l: '责任人', w: 80 }, { l: '计划开始', w: 88 }, { l: '计划结束', w: 88 }, { l: '状态', w: 70 }], build: buildSprint, legend: '<span><i class="lg" style="background:#2E75B6"></i>迭代区间</span>' + LG_STAGE },
  { key: 'hybrid', label: '④混合', title: '④ 混合式甘特图', desc: '外层管阶段关卡，内层用迭代吸收需求变更。', cols: [{ l: '阶段', w: 90 }, { l: '迭代', w: 40 }, { l: '工作项目', w: 180 }, { l: '责任人', w: 80 }, { l: '计划开始', w: 88 }, { l: '计划结束', w: 88 }, { l: '交付表单/证据', w: 160 }], build: buildHybrid, legend: '<span><i class="lg" style="background:#A6A6A6"></i>阶段总条</span>' + LG_STAGE },
  { key: 'baseline', label: '⑤基准vs跟踪', title: '⑤ 基准计划 vs 进度跟踪甘特图', desc: '周会跟进度，一眼看出谁延期、偏差几天、证据是否齐。', cols: [{ l: '阶段', w: 70 }, { l: '序号', w: 38 }, { l: '工作项目', w: 150 }, { l: '责任人', w: 78 }, { l: '基准开始', w: 86 }, { l: '基准结束', w: 86 }, { l: '实际开始', w: 86 }, { l: '实际结束', w: 86 }, { l: '完成%', w: 52 }, { l: '偏差', w: 44 }, { l: '状态', w: 64 }, { l: '交付表单', w: 150 }], build: buildBaseline, legend: '<span><i class="lg" style="background:#A6A6A6"></i>基准计划条</span><span><i class="lg" style="background:#00B050"></i>实际·按时完成</span><span><i class="lg" style="background:#F4B183"></i>实际·进行中/预警</span>' },
  { key: 'resource', label: '⑥资源负荷', title: '⑥ 资源/负荷甘特图', desc: '看谁被塞满、哪周撞车；数字=该角色当日并行任务数。', cols: [{ l: '工号', w: 56 }, { l: '负责人/角色', w: 90 }, { l: '所属部门', w: 110 }, { l: '占用天数', w: 70 }, { l: '峰值并行', w: 70 }, { l: '负荷率', w: 60 }, { l: '过载提示', w: 100 }], build: buildResource, legend: '<span><i class="lg" style="background:#C6EFCE"></i>并行1项</span><span><i class="lg" style="background:#FFEB9C"></i>并行2项(饱和)</span><span><i class="lg" style="background:#FFC7CE"></i>并行≥3项(过载)</span>' },
  { key: 'critical', label: '⑦依赖关键路径', title: '⑦ 依赖关系/关键路径甘特图', desc: '识别「谁卡谁」；红/★条=关键路径，动一天全项目动一天。', cols: [{ l: '阶段', w: 70 }, { l: '序号', w: 38 }, { l: '工作项目', w: 150 }, { l: '责任人', w: 78 }, { l: '前置任务', w: 96 }, { l: '依赖类型', w: 64 }, { l: '计划开始', w: 86 }, { l: '计划结束', w: 86 }, { l: '工期', w: 44 }, { l: '总浮动', w: 52 }, { l: '关键路径', w: 52 }], build: buildCritical, legend: '<span><i class="lg" style="background:#C00000"></i>关键路径</span>' + LG_STAGE }
].map(t => ({ ...t, cols: t.cols.map(c => ({ label: c.l, w: c.w })) }))

const activeType = ref('roadmap')
const curType = computed(() => TYPES.find(t => t.key === activeType.value))
const rows = computed(() => curType.value.build())
const totalWidth = (RANGE_END - RANGE_START) * DAYW
const todayLeft = x(TODAY)

const months = computed(() => {
  const arr = []
  let m = new Date(Date.UTC(2026, 9, 1))
  while (D(m.toISOString().slice(0, 10)) <= RANGE_END) {
    const next = new Date(Date.UTC(m.getUTCFullYear(), m.getUTCMonth() + 1, 1))
    const s = Math.max(D(m.toISOString().slice(0, 10)), RANGE_START)
    const e = Math.min(D(next.toISOString().slice(0, 10)) - 1, RANGE_END)
    arr.push({ key: m.toISOString().slice(0, 7), label: `${m.getUTCFullYear()}-${String(m.getUTCMonth() + 1).padStart(2, '0')}`, left: (s - RANGE_START) * DAYW, width: (e - s + 1) * DAYW })
    m = next
  }
  return arr
})
const days = computed(() => {
  const arr = []
  for (let d = RANGE_START; d <= RANGE_END; d++) {
    const dayNum = new Date(d * DAY).getUTCDate()
    arr.push({ key: d, label: dayNum === 1 ? '1' : String(dayNum), left: (d - RANGE_START) * DAYW, first: dayNum === 1 })
  }
  return arr
})
const weekendCols = computed(() => {
  const arr = []
  for (let d = RANGE_START; d <= RANGE_END; d++) {
    const dt = new Date(d * DAY)
    if (dt.getUTCDay() === 0 || dt.getUTCDay() === 6) arr.push({ left: (d - RANGE_START) * DAYW })
  }
  return arr
})
function barStyle(b, i) { return { left: x(b.s) + 'px', width: Math.max(w(b.s, b.e), 2) + 'px', top: i * ROWH + 7 + 'px' } }
function msStyle(m, i) { return { left: x(m.d) - 6 + 'px', top: i * ROWH + 11 + 'px' } }
</script>

<style scoped lang="scss">
.hd { margin-bottom: 8px; }
.hd h3 { margin: 0 0 6px; font-size: 16px; }
.hd-sub { font-size: 12px; color: #909399; line-height: 1.8; }
.g-desc { font-size: 12px; color: #606266; margin: 8px 0; }
.legend { display: flex; gap: 14px; flex-wrap: wrap; font-size: 12px; margin-bottom: 10px; }
.legend span { display: inline-flex; align-items: center; gap: 5px; }
.lg { width: 12px; height: 12px; border-radius: 3px; display: inline-block; }
.gantt { display: flex; border: 1px solid #e4e7ed; border-radius: 8px; overflow: hidden; }
.g-left { flex: 0 0 auto; border-right: 1px solid #e4e7ed; background: #fafbfc; overflow: hidden; }
.gl-head-row { display: flex; height: 50px; border-bottom: 1px solid #e4e7ed; background: #f5f7fa; }
.gl-hcell { height: 50px; line-height: 50px; padding: 0 6px; font-weight: 600; font-size: 12px; border-right: 1px solid #e4e7ed; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; box-sizing: border-box; }
.gl-row { display: flex; height: 34px; border-bottom: 1px solid #f0f2f5; }
.gl-row.stage { font-weight: 700; background: #f5f7fa; }
.gl-cell { height: 34px; line-height: 34px; padding: 0 6px; font-size: 12px; border-right: 1px solid #f0f2f5; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; box-sizing: border-box; }
.g-right { flex: 1; overflow-x: auto; }
.gr { position: relative; }
.gr-axis { position: sticky; top: 0; z-index: 5; background: #fff; border-bottom: 1px solid #e4e7ed; }
.gr-month-row { position: relative; height: 25px; border-bottom: 1px solid #e4e7ed; }
.gr-month { position: absolute; top: 0; height: 25px; line-height: 25px; font-size: 11px; border-left: 1px solid #e4e7ed; padding-left: 5px; color: #606266; white-space: nowrap; overflow: hidden; }
.gr-day-row { position: relative; height: 25px; }
.gr-day { position: absolute; top: 0; height: 25px; line-height: 25px; font-size: 10px; color: #999; border-left: 1px solid #f0f2f5; padding-left: 3px; }
.gr-day.first { border-left: 1px solid #e4e7ed; color: #333; font-weight: 600; }
.gr-body { position: relative; }
.wk { position: absolute; top: 0; background: #DDEBF7; z-index: 0; }
.today { position: absolute; top: 0; width: 2px; background: #f56c6c; z-index: 4; }
.today span { position: absolute; top: -2px; left: 2px; background: #f56c6c; color: #fff; font-size: 10px; padding: 0 4px; border-radius: 2px; }
.bar { position: absolute; height: 20px; border-radius: 4px; color: #fff; font-size: 10px; line-height: 20px; padding: 0 5px; overflow: hidden; white-space: nowrap; cursor: pointer; z-index: 1; }
.bar.s1 { background: #4472C4; }
.bar.s2 { background: #ED7D31; }
.bar.s3 { background: #548235; }
.bar.dark { background: #A6A6A6; }
.bar.done { background: #00B050; }
.bar.delay { background: #FFC000; }
.bar.ongoing { background: #F4B183; }
.bar.track { background: #2E75B6; }
.bar.cp { background: #C00000; }
.bar.h1 { background: #C6EFCE; color: #333; }
.bar.h2 { background: #FFEB9C; color: #333; }
.bar.h3 { background: #FFC7CE; color: #333; }
.ms { position: absolute; width: 12px; height: 12px; transform: rotate(45deg); background: #C00000; border-radius: 2px; cursor: pointer; z-index: 3; }
.ms.done { background: #00B050; }
.ms.delay { background: #FFC000; }
.ms.ongoing { background: #FFE699; }
.ms.todo { background: #D9D9D9; }
.foot { font-size: 12px; color: #909399; margin-top: 10px; }
@media (max-width: 768px) {
  .gl-cell, .gl-hcell { font-size: 11px; padding: 0 4px; }
  .hd-sub { font-size: 11px; }
}
</style>
