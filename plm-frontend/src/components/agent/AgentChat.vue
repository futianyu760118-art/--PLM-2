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
          <!-- 数据表 -->
          <div v-if="m.data && m.data.length" class="data-table">
            <div v-for="(row, ri) in m.data" :key="ri" class="data-row">
              <span v-for="(v, k) in row" :key="k" class="data-cell"><i>{{ k }}</i>{{ v }}</span>
            </div>
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
      data: d.data || null,
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
  .data-table { margin-top: 6px; background: #fff; border: 1px solid #ebeef5; border-radius: 8px; overflow: hidden;
    .data-row { display: flex; flex-wrap: wrap; padding: 6px 10px; border-bottom: 1px solid #f5f7fa; gap: 10px;
      &:last-child { border-bottom: none; } }
    .data-cell { font-size: 12px; color: #303133;
      i { font-style: normal; color: #909399; margin-right: 4px; } } }
  .options { margin-top: 8px; display: flex; flex-wrap: wrap; gap: 8px; }
  .chat-empty { text-align: center; color: #909399; padding: 30px 8px; font-size: 13px;
    p { margin: 8px 0 12px; }
    .quick-asks { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; } }
  .chat-input { display: flex; gap: 8px; align-items: flex-end; } }
</style>
