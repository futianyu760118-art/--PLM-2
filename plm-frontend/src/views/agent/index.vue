<template>
  <div class="page-container">
    <OperationGuide module-key="agent" />
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card>
          <template #header>
            <span>智能体</span>
          </template>
          <el-menu :default-active="currentAgent" @select="selectAgent">
            <el-menu-item v-for="a in agents" :key="a.code" :index="a.code">
              <span style="margin-left:8px">{{ a.label }}</span>
              <span style="float:right;color:#999;font-size:12px">{{ a.tag }}</span>
            </el-menu-item>
          </el-menu>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card>
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>会话 - {{ currentAgent }}</span>
              <div>
                <el-select v-model="sessionId" placeholder="选择历史会话" size="small" style="width:200px" @change="loadMessages">
                  <el-option v-for="s in sessions" :key="s.id" :label="s.sessionNo + ' (' + s.startedAt + ')'" :value="s.id" />
                </el-select>
                <el-button size="small" type="primary" @click="startNewSession" style="margin-left:8px">新会话</el-button>
                <el-button size="small" v-if="sessionId" @click="closeSession">关闭</el-button>
              </div>
            </div>
          </template>
          <div class="chat-area" ref="chatArea">
            <div v-for="m in messages" :key="m.id" class="msg" :class="m.role">
              <div class="bubble">{{ m.content }}</div>
              <div style="font-size:11px;color:#999;margin-top:2px">{{ m.createdAt }}</div>
            </div>
            <el-empty v-if="!messages.length && sessionId" description="开始对话吧" :image-size="60" />
          </div>
          <div style="margin-top:12px;display:flex;gap:8px">
            <el-input v-model="input" type="textarea" :rows="2" placeholder="输入消息..." @keydown.enter.exact.prevent="send" />
            <el-button type="primary" :loading="sending" @click="send">发送</el-button>
          </div>
        </el-card>

        <el-card style="margin-top:16px">
          <template #header><span>待决策建议</span></template>
          <el-table :data="actions" border size="small">
            <el-table-column prop="actionType" label="动作" width="120" />
            <el-table-column prop="objectType" label="对象" width="80" />
            <el-table-column prop="objectId" label="ID" width="160" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{row}">
                <el-tag :type="row.status==='APPROVED'?'success':row.status==='REJECTED'?'danger':'warning'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="estimatedMinutesSaved" label="预计节省(分钟)" width="120" />
            <el-table-column prop="createdAt" label="时间" width="160" />
            <el-table-column label="操作" width="140">
              <template #default="{row}">
                <el-button v-if="row.status==='PROPOSED'" link size="small" type="success" @click="decide(row.id, 'APPROVED')">采纳</el-button>
                <el-button v-if="row.status==='PROPOSED'" link size="small" type="danger" @click="decide(row.id, 'REJECTED')">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { createSession, listSessions, closeSession as closeSessionApi, sendMessage, listMessages, pendingActions, decideAction } from '@/api/agent'

const agents = [
  { code:'AGENT_QA', label:'质检官', tag:'L0只读' },
  { code:'AGENT_ANALYST', label:'分析师', tag:'L0只读' },
  { code:'AGENT_COACH', label:'改善教练', tag:'L0只读' },
  { code:'AGENT_BOM', label:'BOM助理', tag:'L0只读' },
  { code:'AGENT_ECN', label:'ECN助理', tag:'L0只读' },
  { code:'AGENT_GATE', label:'技转助理', tag:'L0只读' },
  { code:'AGENT_HELP', label:'问答员', tag:'L0只读' },
  { code:'AGENT_ORCH', label:'总控', tag:'L0路由' }
]
const currentAgent = ref('AGENT_QA')
const sessionId = ref(null)
const sessions = ref([])
const messages = ref([])
const input = ref('')
const sending = ref(false)
const chatArea = ref()
const actions = ref([])

async function selectAgent(code) {
  currentAgent.value = code
  sessionId.value = null
  messages.value = []
}

async function startNewSession() {
  const res = await createSession(currentAgent.value, { channel: 'UI' })
  sessionId.value = res.data.id
  await loadSessions()
  messages.value = []
  ElMessage.success('新会话已创建')
}

async function loadSessions() {
  const res = await listSessions()
  sessions.value = res.data || []
}

async function loadMessages() {
  if (!sessionId.value) { messages.value = []; return }
  const res = await listMessages(sessionId.value)
  messages.value = res.data || []
  await nextTick(); scrollBottom()
}

async function closeSession() {
  if (!sessionId.value) return
  await closeSessionApi(sessionId.value)
  ElMessage.success('会话已关闭')
  await loadSessions()
  sessionId.value = null
  messages.value = []
}

async function send() {
  if (!input.value.trim()) return
  if (!sessionId.value) { await startNewSession() }
  sending.value = true
  try {
    const res = await sendMessage(sessionId.value, { content: input.value, partNo: extractPartNo(input.value) })
    input.value = ''
    await loadMessages()
    if (res.data?.pendingAction) {
      ElMessage.info('已生成建议,可在下方采纳/驳回')
      await loadActions()
    }
  } finally { sending.value = false }
}

function extractPartNo(text) {
  const m = text.match(/[A-Za-z0-9_-]{4,}/)
  return m ? m[0] : null
}

async function loadActions() {
  const res = await pendingActions('PROPOSED')
  actions.value = res.data || []
}

async function decide(id, decision) {
  await decideAction(id, { decision })
  ElMessage.success(decision === 'APPROVED' ? '已采纳' : '已驳回')
  await loadActions()
}

function scrollBottom() {
  if (chatArea.value) chatArea.value.scrollTop = chatArea.value.scrollHeight
}

onMounted(async () => { await loadSessions(); await loadActions() })
</script>

<style scoped lang="scss">
.chat-area { min-height: 320px; max-height: 460px; overflow-y: auto; padding: 8px; background: #f8f9ff; border-radius: 6px }
.msg { margin-bottom: 12px }
.msg.user .bubble { background: #409eff; color: #fff; padding: 8px 12px; border-radius: 8px; display: inline-block; max-width: 80%; margin-left: auto }
.msg.user { text-align: right }
.msg.assistant .bubble { background: #fff; border: 1px solid #ebeef5; padding: 8px 12px; border-radius: 8px; display: inline-block; max-width: 80% }
</style>
