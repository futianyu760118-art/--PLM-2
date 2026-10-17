<template>
  <div class="page-container">
    <el-row :gutter="16">
      <el-col :xs="24" :md="14">
        <el-card v-loading="loading">
          <template #header><span>工作项（按状态分组 · 紧凑列表）</span></template>
          <div v-for="g in groups" :key="g.key" class="group">
            <div class="g-head" :class="g.cls">{{ g.label }} <b>{{ g.items.length }}</b></div>
            <div v-for="it in g.items" :key="it.id" class="wi">
              <StatusDot :cls="g.cls" />
              <span class="t">{{ it.title }}</span>
              <span class="due">{{ (it.slaDueAt || '').slice(0, 10) }}</span>
              <el-button v-if="it.status !== 'DONE'" link size="small" type="success" @click="complete(it)">完成</el-button>
            </div>
            <p v-if="!g.items.length" class="muted">无</p>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="10">
        <el-card>
          <template #header><span>收件箱（反馈 + 提醒）</span></template>
          <div v-for="f in feedbacks" :key="f.id" class="wi">
            <span class="t">{{ f.title }}</span>
            <el-tag size="small" :type="f.status === 'pending' ? 'warning' : f.status === 'resolved' ? 'success' : 'info'">
              {{ statusLabel(f.status) }}
            </el-tag>
          </div>
          <p v-if="!feedbacks.length" class="muted">暂无反馈</p>
          <el-button size="small" style="margin-top:8px" @click="$router.push('/admin/feedback')">查看全部</el-button>
        </el-card>
        <el-card style="margin-top:16px">
          <template #header><span>工具与角色</span></template>
          <div class="tools">
            <div class="tool" @click="$router.push('/agent/list')"><el-icon><MagicStick /></el-icon><span>智能体</span></div>
            <div class="tool" @click="openChat"><el-icon><ChatDotRound /></el-icon><span>总控对话</span></div>
            <div class="tool" @click="$router.push('/help')"><el-icon><QuestionFilled /></el-icon><span>帮助</span></div>
          </div>
        </el-card>
        <el-card style="margin-top:16px">
          <template #header><span>执行记录（最近变更留痕）</span></template>
          <div class="wi" v-for="c in changes" :key="c.id">
            <span class="t">{{ c.project_no }} · {{ c.object_type }} {{ c.action }}</span>
            <span class="due">{{ (c.created_at || '').slice(5, 16) }}</span>
          </div>
          <p v-if="!changes.length" class="muted">暂无</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, h } from 'vue'
import { myWorkItems, completeWorkItem } from '@/api/workitem'
import { listFeedback } from '@/api/feedback'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const items = ref([])
const feedbacks = ref([])
const changes = ref([])
const loading = ref(false)

const statusLabel = (s) => ({ pending: '待处理', processing: '处理中', resolved: '已解决', closed: '已关闭' }[s] || s)

const groups = computed(() => [
  { key: 'ESCALATED', label: '已升级', cls: 'danger', items: items.value.filter((i) => i.status === 'ESCALATED') },
  { key: 'OPEN', label: '进行中', cls: 'primary', items: items.value.filter((i) => i.status === 'OPEN') },
  { key: 'DONE', label: '已完成', cls: 'success', items: items.value.filter((i) => i.status === 'DONE').slice(0, 5) }
])

const StatusDot = {
  props: { cls: String },
  render() { return h('span', { class: ['dot', this.cls] }) }
}

function openChat() {
  window.dispatchEvent(new CustomEvent('plm-open-agent-chat'))
}

async function load() {
  loading.value = true
  try {
    myWorkItems().then((r) => (items.value = r.data || [])).catch(() => {})
    listFeedback({ page: 1, page_size: 5 }).then((r) => (feedbacks.value = r.data.items || [])).catch(() => {})
    request({ url: '/project/changes', params: { pageNum: 1, pageSize: 6 } }).then((r) => (changes.value = r.data.records || [])).catch(() => {})
  } finally { loading.value = false }
}
async function complete(it) {
  await completeWorkItem(it.id)
  ElMessage.success('已完成')
  const r = await myWorkItems()
  items.value = r.data || []
}
onMounted(load)
</script>

<style scoped>
.group { margin-bottom: 14px; }
.g-head { font-size: 12px; font-weight: 600; margin-bottom: 4px; display: flex; align-items: center; gap: 6px; }
.g-head.danger { color: #f56c6c; } .g-head.primary { color: #409eff; } .g-head.success { color: #67c23a; }
.wi { display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: 6px; font-size: 13px; }
.wi:hover { background: #f7f8fa; }
.wi .t { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wi .due { font-size: 11px; color: #c0c4cc; font-family: monospace; }
.dot { width: 8px; height: 8px; border-radius: 50%; flex: 0 0 8px; }
.dot.danger { background: #f56c6c; } .dot.primary { background: #409eff; } .dot.success { background: #67c23a; }
.muted { color: #c0c4cc; font-size: 12px; margin: 4px 0; }
.tools { display: flex; gap: 10px; }
.tool { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 14px 6px;
  border: 1px solid #e9e9ee; border-radius: 8px; cursor: pointer; font-size: 12px; color: #606266; background: #fafafa;
  transition: transform .12s cubic-bezier(.34,1.56,.64,1); }
.tool:hover { color: var(--plm-accent, #409eff); }
.tool:active { transform: scale(.94); }
</style>
