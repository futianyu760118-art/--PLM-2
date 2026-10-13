<template>
  <div class="ai-section">
    <div class="ai-head">
      <el-tag size="small" :type="statusType">{{ statusLabel }}</el-tag>
      <el-tooltip v-if="detail.ai_branch" :content="detail.ai_branch">
        <span class="branch">{{ detail.ai_branch }}</span>
      </el-tooltip>
      <span v-if="detail.ai_requested_by" class="note">{{ detail.ai_requested_by }} 派单{{ detail.ai_requested_at ? ' · ' + fmt(detail.ai_requested_at) : '' }}</span>
      <el-button v-if="canHandle && aiAction" link size="small" type="primary" @click="$emit('ai', detail)">{{ aiAction }}</el-button>
    </div>

    <div v-if="stages.length" class="sec">
      <div class="sec-title">执行进度</div>
      <div v-for="(s, i) in stages" :key="i" class="stage">
        <span class="dot" :class="{ on: isCurrent(s) }">{{ isCurrent(s) ? '·' : '✓' }}</span>
        <span class="sname" :class="{ on: isCurrent(s) }">{{ stageLabel(s.stage) }}</span>
        <span class="note">{{ clock(s.at) }}</span>
      </div>
    </div>

    <div v-if="actions.length" class="sec">
      <div class="sec-title">最近动作（{{ actions.length }}）</div>
      <div class="terminal">
        <div v-for="(a, i) in actions" :key="i" class="line">
          <span class="clock">{{ clock(a.at) }}</span>
          <span class="text">{{ a.text }}</span>
        </div>
      </div>
    </div>

    <template v-if="detail.ai_status === 'done' || detail.ai_status === 'failed'">
      <div v-if="result && result.error" class="sec error-box">{{ result.error }}</div>
      <div v-if="detail.ai_status === 'done' && result && result.summary" class="sec summary">{{ result.summary }}</div>

      <div v-if="files.length" class="sec">
        <div class="sec-title">改动文件 · {{ files.length }} 个 <span class="add">+{{ totalAdd }}</span> <span class="del">-{{ totalDel }}</span></div>
        <div v-for="(f, i) in visibleFiles" :key="i" class="file">
          <el-tag size="small" :type="fileTagType(f.status)" effect="plain">{{ fileLabel(f.status) }}</el-tag>
          <span class="fpath">{{ f.path }}</span>
          <span v-if="f.additions || f.deletions" class="count"><span class="add">+{{ f.additions }}</span> / <span class="del">-{{ f.deletions }}</span></span>
        </div>
        <el-button v-if="files.length > 8" link size="small" @click="filesExpanded = !filesExpanded">{{ filesExpanded ? '收起' : `展开全部 ${files.length} 个文件` }}</el-button>
      </div>

      <div v-if="gates.length" class="sec">
        <div class="sec-title">门禁结果</div>
        <div v-for="(g, i) in gates" :key="i" class="gate">
          <el-icon :color="g.passed ? '#16a34a' : '#dc2626'"><CircleCheckFilled v-if="g.passed" /><CircleCloseFilled v-else /></el-icon>
          <span class="gname">{{ gateLabel(g.name) }}</span>
          <span :class="g.passed ? 'ok' : 'fail'">{{ g.passed ? '通过' : '失败' }}</span>
          <span v-if="g.exitCode" class="note">exit={{ g.exitCode }}</span>
          <span v-if="g.durationSeconds != null" class="note">{{ dur(g.durationSeconds) }}</span>
          <el-button v-if="g.outputTail" link size="small" @click="toggleGate(i)">{{ openGate === i ? '收起输出' : '查看输出' }}</el-button>
          <pre v-if="openGate === i && g.outputTail" class="out">{{ g.outputTail }}</pre>
        </div>
      </div>

      <div v-if="result && result.runDir" class="sec note">运行日志：{{ result.runDir }}</div>
    </template>

    <div v-else-if="detail.ai_note" class="sec summary">{{ detail.ai_note }}</div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({ detail: { type: Object, required: true }, canHandle: { type: Boolean, default: false } })
defineEmits(['ai'])

const STAGE_LABELS = {
  preparing: '准备隔离环境', fetching: '下载附件', agent: 'AI 修改代码',
  gate_backend: '后端测试', gate_frontend: '前端构建', committing: '提交代码'
}
const AI_STATUS_LABELS = { queued: '排队中', running: '实现中', done: '已实现', failed: '失败' }
const AI_STATUS_TYPES = { queued: 'warning', running: 'primary', done: 'success', failed: 'danger' }
const GATE_LABELS = { backend: '后端测试', frontend: '前端构建' }
const FILE_LABELS = { A: '新增', M: '修改', D: '删除' }
const FILE_TYPES = { A: 'success', M: 'warning', D: 'danger' }

const filesExpanded = ref(false)
const openGate = ref(null)

const progress = computed(() => props.detail.ai_progress || [])
const stages = computed(() => progress.value.filter(e => e && e.kind === 'stage'))
const actions = computed(() => progress.value.filter(e => e && e.kind === 'action'))
const result = computed(() => props.detail.ai_result || null)
const files = computed(() => result.value?.files || [])
const gates = computed(() => result.value?.gates || [])
const totalAdd = computed(() => files.value.reduce((s, f) => s + (f.additions || 0), 0))
const totalDel = computed(() => files.value.reduce((s, f) => s + (f.deletions || 0), 0))
const visibleFiles = computed(() => filesExpanded.value ? files.value : files.value.slice(0, 8))

const statusLabel = computed(() => AI_STATUS_LABELS[props.detail.ai_status] || props.detail.ai_status)
const statusType = computed(() => AI_STATUS_TYPES[props.detail.ai_status] || 'info')
const aiAction = computed(() => {
  const r = props.detail
  if (r.ai_status === 'queued') return '取消排队'
  if (r.status === 'resolved' || r.status === 'closed') return ''
  if (r.ai_status === 'running') return '重新排队'
  if (r.ai_status === 'failed') return '重试'
  if (r.ai_status === 'done') return ''
  return '交 AI 实现'
})

const stageLabel = s => STAGE_LABELS[s] || s
const gateLabel = n => GATE_LABELS[n] || n
const fileLabel = s => FILE_LABELS[s] || '修改'
const fileTagType = s => FILE_TYPES[s] || 'warning'
const isCurrent = s => props.detail.ai_status === 'running' && props.detail.ai_stage === s.stage
const fmt = t => (t ? String(t).replace('T', ' ').slice(0, 16) : '')
const clock = t => (t ? String(t).replace('T', ' ').slice(11, 19) : '')
const dur = s => (s == null ? '' : s >= 60 ? `${Math.floor(s / 60)}分${s % 60}秒` : `${s}秒`)
const toggleGate = i => { openGate.value = openGate.value === i ? null : i }
</script>

<style scoped lang="scss">
.ai-section { font-size: 13px;
  .ai-head { display: flex; gap: 8px; align-items: center; flex-wrap: wrap;
    .branch { font-family: monospace; font-size: 12px; color: #475569; }
    .note { font-size: 12px; color: #94a3b8; }
  }
  .sec { margin-top: 12px; }
  .sec-title { font-size: 12px; color: #94a3b8; font-weight: 600; margin-bottom: 6px; }
  .stage { display: flex; gap: 8px; align-items: center; padding: 2px 0;
    .dot { width: 18px; height: 18px; border-radius: 50%; background: #f0fdf4; color: #16a34a; display: inline-flex; align-items: center; justify-content: center; font-size: 12px;
      &.on { background: #eef0ff; color: #5b5bd6; }
    }
    .sname { &.on { font-weight: 600; } }
    .note { font-size: 12px; color: #94a3b8; }
  }
  .terminal { background: #0f172a; border-radius: 8px; padding: 10px 12px; max-height: 240px; overflow: auto;
    .line { display: flex; gap: 10px; line-height: 1.9; }
    .clock { color: #475569; flex-shrink: 0; font-family: monospace; }
    .text { color: #cbd5e1; white-space: pre-wrap; word-break: break-all; }
  }
  .error-box { background: #fef2f2; border: 1px solid #fecaca; border-radius: 8px; padding: 8px 12px; color: #b91c1c; white-space: pre-wrap; word-break: break-all; }
  .summary { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 8px 12px; color: #334155; white-space: pre-wrap; line-height: 1.7; max-height: 220px; overflow: auto; }
  .file { display: flex; align-items: center; gap: 8px; padding: 2px 0; min-width: 0;
    .fpath { font-family: monospace; font-size: 12px; color: #334155; word-break: break-all; min-width: 0; flex: 1; }
    .count { font-size: 11px; }
  }
  .add { color: #16a34a; } .del { color: #dc2626; }
  .gate { border: 1px solid #e2e8f0; border-radius: 8px; padding: 6px 10px; margin-bottom: 6px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap;
    .gname { font-weight: 600; color: #334155; }
    .ok { color: #16a34a; } .fail { color: #dc2626; }
    .note { font-size: 12px; color: #94a3b8; }
    .out { width: 100%; margin: 6px 0 0; background: #0f172a; color: #cbd5e1; border-radius: 6px; padding: 8px 10px; max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-all; font-size: 11px; }
  }
  .note { font-size: 12px; color: #94a3b8; word-break: break-all; }
}
</style>
