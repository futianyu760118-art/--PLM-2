<template>
  <el-drawer v-model="visible" title="恒剑PLM智能体" size="500px" :append-to-body="true">
    <div class="agent-chat">
      <div class="chat-head">
        <span class="hint">用大白话说需求，我来办；办不了我会给你选项</span>
        <el-button size="small" @click="resetChat">新对话</el-button>
      </div>

      <div ref="chatArea" class="chat-area">
        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="bubble">{{ m.text }}</div>
          <!-- 智能体分析解读 -->
          <div v-if="m.analysis" class="result-analysis">
            <span class="result-analysis-tag">分析</span>
            <p>{{ m.analysis }}</p>
          </div>
          <!-- 通用结果卡片 -->
          <div v-if="m.data && m.data.length" class="result-cards">
            <div class="result-summary">{{ summaryText(m) }}</div>
            <article v-for="(row, index) in m.data" :key="index" class="result-card">
              <div class="result-card-head">
                <div class="result-card-title">
                  <strong>{{ rowTitle(row, index, m.display) }}</strong>
                  <small v-if="rowSubtitle(row, m.display)">{{ rowSubtitle(row, m.display) }}</small>
                </div>
                <span v-if="row.status" class="status-chip">{{ formatResultValue('status', row.status) }}</span>
              </div>

              <div class="result-grid">
                <div v-for="key in primaryFields(row, m.display)" :key="key" class="result-field">
                  <span>{{ labelFor(key, m.display) }}</span>
                  <b>{{ formatResultValue(key, row[key]) }}</b>
                </div>
              </div>

              <details v-if="detailFields(row, m.display).length" class="result-more">
                <summary>查看全部字段</summary>
                <div class="result-detail-grid">
                  <div v-for="key in detailFields(row, m.display)" :key="key" class="result-detail">
                    <span>{{ labelFor(key, m.display) }}</span>
                    <b>{{ formatResultValue(key, row[key]) }}</b>
                  </div>
                </div>
              </details>
            </article>
          </div>
          <!-- 选项按钮（不能执行时提问） -->
          <div v-if="m.options && m.options.length" class="options">
            <el-button v-for="opt in m.options" :key="opt" size="small" round
              :disabled="m.chosen" :type="m.chosen === opt ? 'primary' : 'default'"
              @click="send(opt, m)">{{ opt }}</el-button>
          </div>
        </div>
        <div v-if="!messages.length" class="chat-empty">
          <el-icon :size="30"><MagicStick /></el-icon>
          <p>你好，我是恒剑PLM智能体</p>
          <div class="quick-asks">
            <el-button v-for="q in QUICK" :key="q" size="small" round @click="send(q)">{{ q }}</el-button>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <el-input v-model="input" type="textarea" :rows="2" placeholder="例如：HJ.26042 进度怎样 / 我的待办 / 打开变更"
          @keydown.enter.exact.prevent="send()" />
        <el-button type="primary" :loading="sending" @click="send()">发送</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { chatExecute } from '@/api/agent'
import { formatResultValue, rowTitle, rowSubtitle, primaryFields, detailFields, labelFor, summaryText } from './result-display'

const visible = defineModel({ type: Boolean, default: false })
const router = useRouter()
const route = useRoute()

const QUICK = ['HJ.26042 进度怎样', '我的待办', '我的逾期节点', '打开研发项目跟踪', '生命周期总览']

const messages = ref([])
const input = ref('')
const sending = ref(false)
const sessionId = ref(null)
const chatArea = ref(null)

watch(visible, (v) => { if (v) scrollBottom() })

async function send(text, optionMsg) {
  const content = (text || input.value).trim()
  if (!content || sending.value) return
  if (optionMsg) optionMsg.chosen = content
  input.value = ''
  messages.value.push({ role: 'user', text: content })
  scrollBottom()
  sending.value = true
  try {
    const res = await chatExecute(content, sessionId.value, { path: route.path, title: route.meta?.title || '' })
    const d = res.data || {}
    sessionId.value = d.sessionId
    messages.value.push({
      role: 'assistant',
      text: d.reply || '(空)',
      analysis: d.analysis || null,
      data: d.data || null,
      display: d.display || null,
      options: d.options || null
    })
    if (d.navigate) {
      setTimeout(() => { router.push(d.navigate) }, 600)
    }
  } catch (e) { /* 拦截器已提示 */ } finally {
    sending.value = false
    scrollBottom()
  }
}

function resetChat() {
  messages.value = []; sessionId.value = null
}
function scrollBottom() {
  nextTick(() => { if (chatArea.value) chatArea.value.scrollTop = chatArea.value.scrollHeight })
}
</script>

<style scoped lang="scss">
.agent-chat { display: flex; flex-direction: column; height: 100%; gap: 10px;
  .chat-head { display: flex; align-items: center; gap: 8px;
    .hint { flex: 1; font-size: 12px; color: #909399; } }
  .chat-area { flex: 1; overflow-y: auto; padding: 10px; background: #f7f8fd; border-radius: 8px; }
  .msg { margin-bottom: 14px; }
  .msg.user { text-align: right;
    .bubble { background: var(--plm-accent, #409eff); color: #fff; padding: 8px 12px; border-radius: 10px 10px 2px 10px; display: inline-block; max-width: 85%; text-align: left; white-space: pre-wrap; word-break: break-word; } }
  .msg.assistant {
    .bubble { background: #fff; border: 1px solid #ebeef5; padding: 8px 12px; border-radius: 10px 10px 10px 2px; display: inline-block; max-width: 92%; white-space: pre-wrap; word-break: break-word; } }
  .result-analysis { margin-top: 6px; background: #f0f7ff; border: 1px solid #d4e6ff; border-radius: 8px; padding: 8px 10px;
    .result-analysis-tag { display: inline-block; font-size: 11px; color: #409eff; background: #e8f2ff; border-radius: 4px; padding: 0 6px; margin-bottom: 4px; }
    p { margin: 0; font-size: 12px; color: #303133; line-height: 1.6; white-space: pre-wrap; word-break: break-word; } }
  .result-cards { margin-top: 6px; display: flex; flex-direction: column; gap: 8px;
    .result-summary { font-size: 12px; color: #909399; padding: 0 2px; }
    .result-card { background: #fff; border: 1px solid #ebeef5; border-radius: 8px; padding: 8px 10px;
      .result-card-head { display: flex; align-items: flex-start; gap: 8px; margin-bottom: 6px;
        .result-card-title { flex: 1; min-width: 0;
          strong { display: block; font-size: 13px; color: #303133; word-break: break-all; }
          small { display: block; font-size: 12px; color: #909399; margin-top: 2px; } }
        .status-chip { flex-shrink: 0; font-size: 12px; padding: 1px 8px; border-radius: 10px; background: #ecf5ff; color: #409eff; } }
      .result-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px 12px;
        .result-field { display: flex; flex-direction: column; min-width: 0;
          span { font-size: 11px; color: #909399; }
          b { font-size: 12px; color: #303133; font-weight: 500; word-break: break-all; } } }
      .result-more { margin-top: 6px;
        summary { font-size: 12px; color: #409eff; cursor: pointer; user-select: none; }
        .result-detail-grid { margin-top: 6px; display: grid; grid-template-columns: 1fr; gap: 4px; padding: 6px 8px; background: #fafafa; border-radius: 6px;
          .result-detail { display: flex; gap: 8px; font-size: 12px;
            span { color: #909399; flex-shrink: 0; }
            b { color: #303133; font-weight: 500; word-break: break-all; } } } } } }
  .options { margin-top: 8px; display: flex; flex-wrap: wrap; gap: 8px; }
  .chat-empty { text-align: center; color: #909399; padding: 30px 8px; font-size: 13px;
    p { margin: 8px 0 12px; }
    .quick-asks { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; } }
  .chat-input { display: flex; gap: 8px; align-items: flex-end; } }
</style>
