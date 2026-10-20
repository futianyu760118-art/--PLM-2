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

      <div class="g-desc">{{ curType.desc }}</div>
      <div class="legend" v-html="curType.legend"></div>

      <div class="gantt">
        <div class="g-left">
          <div class="gl-head">{{ curType.leftHead }}</div>
          <div v-for="(r, i) in rows" :key="i" class="gl-row" :class="[r.cls || '', r.indent ? 'indent' : '']">
            <span v-html="r.label"></span>
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

const STAGES = {
  s1: { name: '手板阶段', color: '#409eff', range: '10/08 ~ 11/08' },
  s2: { name: '模具阶段', color: '#e6a23c', range: '10/24 ~ 12/18' },
  s3: { name: '生产阶段', color: '#67c23a', range: '12/17 ~ 12/25' }
}

const TASKS = [
  { n: 1, code: '01', name: '立项', owner: '研发/业务', stage: 's1', s: '2026-10-08', e: '2026-10-10', ev: '《立项书》' },
  { n: 2, code: '02', name: '设计输入', owner: '项目负责人', stage: 's1', s: '2026-10-10', e: '2026-10-15', ev: '《设计开发输入清单》/《配置表》' },
  { n: 3, code: '03', name: '外观手板', owner: '结构工程师', stage: 's1', s: '2026-10-13', e: '2026-10-20', ev: '《外观评审表》' },
  { n: 4, code: '04', name: '结构评审', owner: '结构工程师', stage: 's1', s: '2026-10-21', e: '2026-10-23', ev: '《结构评审表》' },
  { n: 5, code: '05', name: '电子样品', owner: '电子工程师', stage: 's1', s: '2026-10-16', e: '2026-10-27', ev: '《成本BOM》' },
  { n: 6, code: '06', name: '手板样品', owner: '结构工程师', stage: 's1', s: '2026-10-28', e: '2026-11-03', ev: '《样机评审表》《设计验证报告》' },
  { n: 7, code: '07', name: '产品资料', owner: '项目负责人', stage: 's1', s: '2026-11-05', e: '2026-11-08', ev: '《规格书》《配置表》《成本BOM》' },
  { n: 8, code: '08', name: '模具图纸(二维图)', owner: '结构工程师', stage: 's2', s: '2026-10-24', e: '2026-11-05', ev: '《开修模评审表》、2D图纸' },
  { n: 9, code: '09', name: '模具T0', owner: '结构工程师', stage: 's2', s: '2026-11-16', e: '2026-11-22', ev: '《问题清单》' },
  { n: 10, code: '10', name: '模具T1', owner: '结构工程师', stage: 's2', s: '2026-11-25', e: '2026-12-01', ev: '《问题清单》' },
  { n: 11, code: '11', name: '模具样品', owner: '结构工程师', stage: 's2', s: '2026-12-02', e: '2026-12-06', ev: '《样品评审表》《模具验收书》' },
  { n: 12, code: '12', name: '包装设计及确认', owner: '包装工程师', stage: 's2', s: '2026-11-09', e: '2026-11-18', ev: '《包装平面图纸》' },
  { n: 13, code: '13', name: '测试报告', owner: '品质工程师', stage: 's2', s: '2026-12-07', e: '2026-12-11', ev: '《新产品测试报告》' },
  { n: 14, code: '14', name: '电子试产', owner: '电子工程师', stage: 's2', s: '2026-12-07', e: '2026-12-10', ev: '《试产报告》《预测单》《基础BOM表》' },
  { n: 15, code: '15', name: '研发试产', owner: '结构工程师', stage: 's2', s: '2026-12-07', e: '2026-12-10', ev: '《试产报告》《基础BOM表》' },
  { n: 16, code: '16', name: '技转', owner: '结构工程师', stage: 's2', s: '2026-12-14', e: '2026-12-16', ev: '《技转评审报告》《新产品测试报告》《BOM表》' },
  { n: 17, code: '17', name: '产品宣传资料', owner: '包装工程师', stage: 's2', s: '2026-11-19', e: '2026-12-03', ev: 'PPT、宣传彩页' },
  { n: 18, code: '18', name: '产品发布', owner: '项目负责人', stage: 's2', s: '2026-12-17', e: '2026-12-18', ev: '/' },
  { n: 19, code: '19', name: '工程试产', owner: '工艺工程师', stage: 's3', s: '2026-12-17', e: '2026-12-21', ev: '《SOP》《客户BOM》《工程试产报告》' },
  { n: 20, code: '20', name: '生产试产', owner: '生产主管', stage: 's3', s: '2026-12-22', e: '2026-12-24', ev: '《生产试产报告》、样机10台' },
  { n: 21, code: '21', name: '复盘', owner: '项目负责人', stage: 's3', s: '2026-12-25', e: '2026-12-25', ev: '《复盘报告》' }
]

const MILESTONES = [
  { code: 'M1', name: '立项批准', stage: 's1', d: '2026-10-10', st: '已达成', cls: 'done' },
  { code: 'M2', name: '设计输入冻结', stage: 's1', d: '2026-10-15', st: '已达成·延期1天', cls: 'delay' },
  { code: 'M3', name: '外观手板确认', stage: 's1', d: '2026-10-20', st: '进行中', cls: 'ongoing' },
  { code: 'M4', name: '结构评审通过', stage: 's1', d: '2026-10-23', st: '未开始', cls: 'todo' },
  { code: 'M5', name: '手板样品确认', stage: 's1', d: '2026-11-03', st: '未开始', cls: 'todo' },
  { code: 'M6', name: '模具图纸冻结', stage: 's2', d: '2026-11-05', st: '未开始', cls: 'todo' },
  { code: 'M7', name: '模具T1通过', stage: 's2', d: '2026-12-01', st: '未开始', cls: 'todo' },
  { code: 'M8', name: '模具样品确认', stage: 's2', d: '2026-12-06', st: '未开始', cls: 'todo' },
  { code: 'M9', name: '测试报告通过', stage: 's2', d: '2026-12-11', st: '未开始', cls: 'todo' },
  { code: 'M10', name: '技转完成', stage: 's2', d: '2026-12-16', st: '未开始', cls: 'todo' },
  { code: 'M11', name: '产品发布', stage: 's3', d: '2026-12-18', st: '未开始', cls: 'todo' },
  { code: 'M12', name: '生产试产通过+复盘', stage: 's3', d: '2026-12-25', st: '未开始', cls: 'todo' }
]

const SPRINTS = [
  { code: 'S1', r: '2026-10-08 ~ 10-21', s: '2026-10-08', e: '2026-10-21', goal: '需求与方案定型', tasks: [1, 2] },
  { code: 'S2', r: '2026-10-22 ~ 11-04', s: '2026-10-22', e: '2026-11-04', goal: '结构与手板验证', tasks: [3, 4, 6] },
  { code: 'S3', r: '2026-11-05 ~ 11-18', s: '2026-11-05', e: '2026-11-18', goal: '电子与工装', tasks: [5, 8, 12] },
  { code: 'S4', r: '2026-11-19 ~ 12-02', s: '2026-11-19', e: '2026-12-02', goal: '模具验证', tasks: [9, 10, 11, 17] },
  { code: 'S5', r: '2026-12-03 ~ 12-16', s: '2026-12-03', e: '2026-12-16', goal: '认证与技转', tasks: [13, 14, 15, 16] },
  { code: 'S6', r: '2026-12-17 ~ 12-25', s: '2026-12-17', e: '2026-12-25', goal: '量产准备', tasks: [18, 19, 20, 21] }
]

const ROLES = ['研发/业务', '项目负责人', '结构工程师', '电子工程师', '包装工程师', '品质工程师', '工艺工程师', '生产主管']

const DEPS = {
  1: { pre: '—', type: '—', float: 0, cp: false },
  2: { pre: '1(SS+2)', type: 'SS', float: 0, cp: false },
  3: { pre: '2(SS+3)', type: 'SS', float: 0, cp: false },
  4: { pre: '3(FS)', type: 'FS', float: 0, cp: true },
  5: { pre: '2(FS)', type: 'FS', float: 43, cp: false },
  6: { pre: '4(FS)、5(FS)', type: 'FS', float: 47, cp: false },
  7: { pre: '6(SS+8)', type: 'SS', float: 47, cp: false },
  8: { pre: '4(FS)', type: 'FS', float: 0, cp: true },
  9: { pre: '8(FS+10)', type: 'FS', float: 0, cp: true },
  10: { pre: '9(FS+2)', type: 'FS', float: 0, cp: true },
  11: { pre: '10(FS)', type: 'FS', float: 0, cp: true },
  12: { pre: '2(SS+30)', type: 'SS', float: 20, cp: false },
  13: { pre: '11(FS)', type: 'FS', float: 0, cp: true },
  14: { pre: '5(FS)、11(FS)', type: 'FS', float: 3, cp: false },
  15: { pre: '11(FS)', type: 'FS', float: 3, cp: false },
  16: { pre: '13(FS)', type: 'FS', float: 0, cp: true },
  17: { pre: '2(SS+50)', type: 'SS', float: 0, cp: false },
  18: { pre: '16(FS)', type: 'FS', float: 0, cp: true },
  19: { pre: '18(FS)', type: 'FS', float: 0, cp: true },
  20: { pre: '19(FS)', type: 'FS', float: 0, cp: false },
  21: { pre: '20(FS)', type: 'FS', float: 0, cp: true }
}

const ACTUAL = { 1: 1, 2: 1, 3: 0.85, 4: 0, 5: 0.4, 6: 0, 7: 0, 8: 0, 9: 0, 10: 0, 11: 0, 12: 0, 13: 0, 14: 0, 15: 0, 16: 0, 17: 0, 18: 0, 19: 0, 20: 0, 21: 0 }

const LG_STAGE = '<span><i class="lg" style="background:#409eff"></i>手板阶段</span><span><i class="lg" style="background:#e6a23c"></i>模具阶段</span><span><i class="lg" style="background:#67c23a"></i>生产阶段</span>'
const FOOT = '<div class="foot">编制：研发中心　审核：项目负责人　批准：总经理　｜　注：本表为演示数据（demo），排期按 XM2026-001 项目主表计划周期推演。</div>'

function buildRoadmap() {
  return MILESTONES.map(m => {
    const stg = STAGES[m.stage]
    return { label: `<span class="st-label" style="background:${stg.color}">${stg.name}</span>${m.code} ${m.name}`, ms: [{ d: m.d, cls: m.cls, title: `${m.code} ${m.name} · ${m.d} · ${m.st}` }] }
  })
}
function buildWaterfall() {
  const rows = []
  let cur = ''
  TASKS.forEach(t => {
    if (t.stage !== cur) {
      rows.push({ label: STAGES[t.stage].name, cls: 'stage', bars: [{ s: t.s, e: t.e, cls: 'dark', text: STAGES[t.stage].range, title: STAGES[t.stage].name + ' ' + STAGES[t.stage].range }] })
      cur = t.stage
    }
    rows.push({ label: `XM2026-001-${t.code} ${t.name}`, indent: true, title: t.ev, bars: [{ s: t.s, e: t.e, cls: t.stage, text: `${t.s.slice(5)}~${t.e.slice(5)}`, title: `${t.name} · ${t.owner} · ${t.s}~${t.e}\n${t.ev}` }] })
  })
  return rows
}
function buildSprint() {
  const rows = []
  SPRINTS.forEach(sp => {
    rows.push({ label: `${sp.code} ${sp.r} · ${sp.goal}`, cls: 'stage', bars: [{ s: sp.s, e: sp.e, cls: 'track', text: sp.code, title: `${sp.code} ${sp.r} ${sp.goal}` }] })
    sp.tasks.forEach(tn => {
      const t = TASKS.find(x => x.n === tn)
      rows.push({ label: `XM2026-001-${t.code} ${t.name}`, indent: true, title: t.ev, bars: [{ s: t.s, e: t.e, cls: t.stage, text: `${t.s.slice(5)}~${t.e.slice(5)}`, title: `${t.name} · ${t.owner}` }] })
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
      rows.push({ label: '■ ' + STAGES[t.stage].name, cls: 'stage', bars: [{ s: t.s, e: t.e, cls: 'dark', text: STAGES[t.stage].range }] })
    }
    const sp = SPRINTS.find(s => s.tasks.includes(t.n))
    rows.push({ label: `${sp ? sp.code : ''}　XM2026-001-${t.code} ${t.name}`, indent: true, title: t.ev, bars: [{ s: t.s, e: t.e, cls: t.stage, text: `${t.s.slice(5)}~${t.e.slice(5)}`, title: `${sp ? sp.code : ''} ${t.name} · ${t.owner}\n${t.ev}` }] })
  })
  return rows
}
function buildBaseline() {
  const rows = []
  TASKS.forEach(t => {
    const pct = ACTUAL[t.n] || 0
    rows.push({ label: `XM2026-001-${t.code} ${t.name}　<span style="color:#909399">${t.owner} · ${pct === 1 ? '已完成' : pct > 0 ? '进行中' : '未开始'}</span>`, bars: [{ s: t.s, e: t.e, cls: 'dark', text: `基准 ${t.s.slice(5)}~${t.e.slice(5)}`, title: `基准 ${t.s}~${t.e}` }] })
    if (pct > 0) {
      const done = pct === 1
      rows.push({ label: `　└ 实际执行　${Math.round(pct * 100)}%`, indent: true, bars: [{ s: t.s, e: done ? t.e : TODAY, cls: done ? 'done' : 'ongoing', text: `实际 ${Math.round(pct * 100)}%`, title: `实际执行 ${Math.round(pct * 100)}%` }] })
    } else {
      rows.push({ label: '　└ 实际执行　0%', indent: true })
    }
  })
  return rows
}
function buildResource() {
  return ROLES.map(role => {
    const bars = []
    for (let d = RANGE_START; d <= RANGE_END; d++) {
      const iso = new Date(d * DAY).toISOString().slice(0, 10)
      const cnt = TASKS.filter(t => t.owner === role && D(t.s) <= d && d <= D(t.e)).length
      if (cnt > 0) bars.push({ s: iso, e: iso, cls: 'h' + (cnt >= 3 ? 3 : cnt === 2 ? 2 : 1), text: cnt > 1 ? String(cnt) : '', title: `${role} ${iso} 并行 ${cnt} 项` })
    }
    return { label: role, bars }
  })
}
function buildCritical() {
  return TASKS.map(t => {
    const dep = DEPS[t.n]
    const cp = dep.cp
    return { label: `XM2026-001-${t.code} ${t.name}　<span style="color:#909399;font-size:11px">前置:${dep.pre} 浮动:${dep.float}${cp ? '天 ★关键' : ''}</span>`, bars: [{ s: t.s, e: t.e, cls: cp ? 'done' : t.stage, text: cp ? '★' : `${t.s.slice(5)}~${t.e.slice(5)}`, title: `${t.name} · 前置 ${dep.pre} · 浮动 ${dep.float}天${cp ? ' · 关键路径' : ''}` }] }
  })
}

const TYPES = [
  { key: 'roadmap', label: '①里程碑', title: '① 里程碑路标甘特图（Roadmap·向上汇报版）', desc: '一页纸对齐管理层/跨团队；只看 12 个 Gate 关键节点，不看任务细节。', leftHead: '里程碑节点', build: buildRoadmap, legend: '<span><i class="lg" style="background:#67c23a"></i>已达成</span><span><i class="lg" style="background:#e6a23c"></i>延期达成</span><span><i class="lg" style="background:#f56c6c"></i>进行中</span><span><i class="lg" style="background:#c0c4cc"></i>未开始</span>' + LG_STAGE },
  { key: 'waterfall', label: '②瀑布', title: '② 瀑布/阶段式甘特图（串行阶段+评审关卡）', desc: '项目组排期主表，模板 8甘特表 的标准形态；阶段串行、每阶段有评审关卡。', leftHead: '任务清单（阶段/工作项目）', build: buildWaterfall, legend: LG_STAGE + '<span><i class="lg" style="background:#333"></i>阶段总条</span>' },
  { key: 'sprint', label: '③迭代', title: '③ 迭代式甘特图（Sprint/双周迭代）', desc: '需求不确定、电子/软件方案需反复验证时；每个迭代内是「设计-打样-测试」小循环。', leftHead: '迭代清单（Sprint Backlog）', build: buildSprint, legend: '<span><i class="lg" style="background:#409eff"></i>迭代区间</span>' + LG_STAGE },
  { key: 'hybrid', label: '④混合', title: '④ 混合式甘特图（外层阶段 + 内层迭代嵌套）', desc: '复杂软硬件一体项目主视图；外层管阶段关卡，内层用迭代吸收需求变更。', leftHead: '阶段 / 迭代 / 任务（三层嵌套）', build: buildHybrid, legend: '<span><i class="lg" style="background:#333"></i>阶段总条</span>' + LG_STAGE },
  { key: 'baseline', label: '⑤基准vs跟踪', title: '⑤ 基准计划 vs 进度跟踪甘特图', desc: '周会跟进度，一眼看出谁延期、偏差几天、证据表单是否齐。', leftHead: '基准（上行）/ 实际（下行）', build: buildBaseline, legend: '<span><i class="lg" style="background:#333"></i>基准计划条</span><span><i class="lg" style="background:#67c23a"></i>实际·按时完成</span><span><i class="lg" style="background:#f56c6c"></i>实际·进行中/预警</span>' },
  { key: 'resource', label: '⑥资源负荷', title: '⑥ 资源/负荷甘特图（按角色看人力挤占）', desc: '看谁被塞满、哪周撞车；单元格数字=该角色当日并行执行的任务数。', leftHead: '负责人/角色', build: buildResource, legend: '<span><i class="lg" style="background:#67c23a"></i>并行1项</span><span><i class="lg" style="background:#e6a23c"></i>并行2项(饱和)</span><span><i class="lg" style="background:#f56c6c"></i>并行≥3项(过载)</span>' },
  { key: 'critical', label: '⑦依赖关键路径', title: '⑦ 依赖关系/关键路径甘特图', desc: '识别「谁卡谁」；绿色/★条=关键路径，动一天全项目动一天。', leftHead: '工作项目（前置/依赖类型/总浮动/关键路径）', build: buildCritical, legend: '<span><i class="lg" style="background:#67c23a"></i>关键路径</span>' + LG_STAGE }
]

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
    const first = dayNum === 1
    arr.push({ key: d, label: first ? '1' : String(dayNum), left: (d - RANGE_START) * DAYW, first })
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

function barStyle(b, i) {
  return { left: x(b.s) + 'px', width: Math.max(w(b.s, b.e), 2) + 'px', top: i * ROWH + 7 + 'px' }
}
function msStyle(m, i) {
  return { left: x(m.d) - 6 + 'px', top: i * ROWH + 11 + 'px' }
}
</script>

<style scoped lang="scss">
.hd { margin-bottom: 8px; }
.hd h3 { margin: 0 0 6px; font-size: 16px; }
.hd-sub { font-size: 12px; color: #909399; line-height: 1.8; }
.g-desc { font-size: 12px; color: #606266; margin: 8px 0; }
.legend { display: flex; gap: 14px; flex-wrap: wrap; font-size: 12px; margin-bottom: 10px; }
.legend span { display: inline-flex; align-items: center; gap: 5px; }
.lg { width: 12px; height: 12px; border-radius: 3px; display: inline-block; }
.st-label { display: inline-block; font-size: 11px; padding: 0 6px; border-radius: 3px; color: #fff; margin-right: 6px; }
.gantt { display: flex; border: 1px solid #e4e7ed; border-radius: 8px; overflow: hidden; }
.g-left { flex: 0 0 min(360px, 42vw); border-right: 1px solid #e4e7ed; background: #fafbfc; }
.gl-head { height: 50px; line-height: 50px; padding: 0 12px; font-weight: 600; font-size: 12px; border-bottom: 1px solid #e4e7ed; }
.gl-row { height: 34px; line-height: 34px; padding: 0 12px; border-bottom: 1px solid #f0f2f5; font-size: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.gl-row.indent { padding-left: 26px; color: #606266; }
.gl-row.stage { font-weight: 700; background: #f5f7fa; }
.g-right { flex: 1; overflow-x: auto; }
.gr { position: relative; }
.gr-axis { position: sticky; top: 0; z-index: 5; background: #fff; border-bottom: 1px solid #e4e7ed; }
.gr-month-row { position: relative; height: 25px; border-bottom: 1px solid #e4e7ed; }
.gr-month { position: absolute; top: 0; height: 25px; line-height: 25px; font-size: 11px; border-left: 1px solid #e4e7ed; padding-left: 5px; color: #606266; white-space: nowrap; overflow: hidden; }
.gr-day-row { position: relative; height: 25px; }
.gr-day { position: absolute; top: 0; height: 25px; line-height: 25px; font-size: 10px; color: #999; border-left: 1px solid #f0f2f5; padding-left: 3px; }
.gr-day.first { border-left: 1px solid #e4e7ed; color: #333; font-weight: 600; }
.gr-body { position: relative; }
.wk { position: absolute; top: 0; background: #eaf2fb; z-index: 0; }
.today { position: absolute; top: 0; width: 2px; background: #f56c6c; z-index: 4; }
.today span { position: absolute; top: -2px; left: 2px; background: #f56c6c; color: #fff; font-size: 10px; padding: 0 4px; border-radius: 2px; }
.bar { position: absolute; height: 20px; border-radius: 4px; color: #fff; font-size: 10px; line-height: 20px; padding: 0 5px; overflow: hidden; white-space: nowrap; cursor: pointer; z-index: 1; }
.bar.s1 { background: #409eff; }
.bar.s2 { background: #e6a23c; }
.bar.s3 { background: #67c23a; }
.bar.dark { background: #333; }
.bar.done { background: #67c23a; }
.bar.delay { background: #e6a23c; }
.bar.ongoing { background: #f56c6c; }
.bar.track { background: #409eff; }
.bar.h1 { background: #67c23a; }
.bar.h2 { background: #e6a23c; }
.bar.h3 { background: #f56c6c; }
.ms { position: absolute; width: 12px; height: 12px; transform: rotate(45deg); background: #f56c6c; border-radius: 2px; cursor: pointer; z-index: 3; }
.ms.done { background: #67c23a; }
.ms.delay { background: #e6a23c; }
.ms.ongoing { background: #f56c6c; }
.ms.todo { background: #c0c4cc; }
.foot { font-size: 12px; color: #909399; margin-top: 10px; }

@media (max-width: 768px) {
  .g-left { flex-basis: min(220px, 45vw); }
  .gl-row { font-size: 11px; }
  .hd-sub { font-size: 11px; }
}
</style>
