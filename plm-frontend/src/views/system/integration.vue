<template>
  <div class="page-container">
    <OperationGuide module-key="sys-integration" />
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>外部系统对接状态</span>
          <el-button icon="Refresh" @click="loadData">刷新</el-button>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="8" v-for="sys in systems" :key="sys.systemCode">
          <el-card shadow="hover">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
              <strong>{{ sys.systemCode }}</strong>
              <el-tag :type="sys.connected ? 'success' : 'info'" effect="dark">
                {{ sys.connected ? '已连通' : '未连通' }}
              </el-tag>
            </div>
            <p style="color:#606266;font-size:13px;margin:4px 0">{{ sys.description }}</p>
            <p style="color:#909399;font-size:12px;margin:4px 0">地址: {{ sys.baseUrl }}</p>
            <p style="margin:8px 0">
              <el-tag size="small" :type="sys.enabled ? 'success' : 'warning'">{{ sys.enabled ? '已启用' : '模拟模式(预留)' }}</el-tag>
            </p>
            <el-button size="small" type="primary" @click="handleTest(sys.systemCode)">测试连接</el-button>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header><span>企业微信自建应用（按人推送待办到个人）</span></template>
      <el-alert type="info" :closable="false" style="margin-bottom:12px"
        title="按个人推送必须使用企业微信「自建应用」：企业微信管理后台→应用管理→自建→创建应用，获取 企业ID(CorpID)、应用AgentId、应用Secret；并让成员手机号与本系统用户手机号一致(或已匹配到企业微信账号)。" />
      <el-form :model="wecomApp" label-width="140px" style="max-width:760px">
        <el-form-item label="企业ID CorpID">
          <el-input v-model="wecomApp.corpId" placeholder="ww开头，企业微信管理后台-我的企业-企业信息" clearable />
        </el-form-item>
        <el-form-item label="应用 AgentId">
          <el-input v-model="wecomApp.agentId" placeholder="自建应用详情页的 AgentId（数字）" clearable />
        </el-form-item>
        <el-form-item label="应用 Secret">
          <el-input v-model="wecomApp.corpSecret" type="password" show-password placeholder="自建应用详情页的 Secret" clearable />
        </el-form-item>
        <el-form-item label="启用按人推送">
          <el-switch v-model="wecomApp.enabled" />
          <span style="color:#909399;font-size:12px;margin-left:12px">启用后每天 08:30 自动把各自待办推送到个人企业微信</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="wecomAppSaving" @click="saveWecomApp">保存</el-button>
          <el-button type="success" :loading="wecomPushing" @click="pushTodosNow">立即推送待办</el-button>
        </el-form-item>
      </el-form>
      <div v-if="pushResult" style="color:#606266;font-size:13px">
        推送结果：用户 {{ pushResult.users ?? '-' }}，成功 {{ pushResult.sent ?? '-' }}，跳过 {{ pushResult.skipped ?? '-' }}，失败 {{ pushResult.failed ?? '-' }}
        <span v-if="pushResult.error" style="color:#f56c6c">（{{ pushResult.error }}）</span>
      </div>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header><span>企业微信通知（计划审批提醒等）</span></template>
      <el-form :model="wecom" label-width="120px" style="max-width:760px">
        <el-form-item label="群机器人Webhook">
          <el-input v-model="wecom.webhookUrl" placeholder="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxxxxxxx" clearable />
          <div style="color:#e6a23c;font-size:12px;line-height:1.6;margin-top:4px">
            注意：这里要填的是「群机器人」的 Webhook 地址（以 <code>/cgi-bin/webhook/send?key=</code> 结尾），<b>不是「智能机器人」的 Bot ID/Secret</b>。<br>
            获取方式：目标群聊右上角 → 群机器人 → 添加机器人 → 复制 Webhook 地址。
          </div>
        </el-form-item>
        <el-form-item label="启用通知">
          <el-switch v-model="wecom.enabled" />
          <span style="color:#909399;font-size:12px;margin-left:12px">填写 webhook 并开启后，计划提交/审批/驳回、反馈等事件将推送到企业微信群</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="wecomSaving" @click="saveWecom">保存</el-button>
          <el-button :loading="wecomTesting" @click="testWecom">发送测试</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header><span>2D 模具图纸自动分解引擎</span></template>
      <el-alert type="info" :closable="false" style="margin-bottom:16px"
        title="上传模具总装 DWG/DXF 文件, 系统自动解析图层分类为: 总装图/CNC模仁/EDM铜公/线割/水路/排气/模胚/散件 8类标准图纸" />
      <el-upload drag :before-upload="handleDecompose" :show-file-list="false" accept=".dxf,.dwg">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽 DWG/DXF 文件到此处, 或<em>点击上传</em></div>
      </el-upload>

      <div v-if="decomposeResult" style="margin-top:16px">
        <el-descriptions title="分解结果" :column="3" border>
          <el-descriptions-item label="总实体数">{{ decomposeResult.total_entities }}</el-descriptions-item>
          <el-descriptions-item label="图层数">{{ decomposeResult.total_layers }}</el-descriptions-item>
          <el-descriptions-item label="分类图纸数">{{ decomposeResult.classified_count }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="decomposeResult.sheets" border style="margin-top:12px" max-height="300">
          <el-table-column prop="category_name" label="分类" min-width="200" />
          <el-table-column prop="layer" label="来源图层" width="150" />
          <el-table-column prop="entity_count" label="实体数" width="90" align="center" />
          <el-table-column label="尺寸标注" width="90" align="center">
            <template #default="{row}"><el-tag size="small" :type="row.has_dimension?'success':'info'">{{ row.has_dimension?'有':'无' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="公差" width="80" align="center">
            <template #default="{row}"><el-tag size="small" :type="row.has_tolerance?'warning':'info'">{{ row.has_tolerance?'有':'无' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="text_labels" label="文本标注" min-width="200" show-overflow-tooltip>
            <template #default="{row}">{{ (row.text_labels||[]).join(', ') }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { integrationStatus, testIntegration, decomposeDrawing } from '@/api/algorithm'
import request from '@/utils/request'

const systems = ref([])
const decomposeResult = ref(null)
const wecom = ref({ webhookUrl: '', enabled: false })
const wecomSaving = ref(false)
const wecomTesting = ref(false)
const wecomApp = ref({ corpId: '', agentId: '', corpSecret: '', enabled: false })
const wecomAppSaving = ref(false)
const wecomPushing = ref(false)
const pushResult = ref(null)

async function loadWecomApp() {
  const res = await request({ url: '/system/notify/wecom-app', method: 'get' })
  const d = res.data || {}
  wecomApp.value = { corpId: d.corpId || '', agentId: d.agentId || '', corpSecret: d.corpSecret || '', enabled: !!d.enabled }
}
async function saveWecomApp() {
  wecomAppSaving.value = true
  try {
    await request({ url: '/system/notify/wecom-app', method: 'put', data: wecomApp.value })
    ElMessage.success('已保存')
  } finally { wecomAppSaving.value = false }
}
async function pushTodosNow() {
  wecomPushing.value = true
  try {
    await saveWecomApp()
    const res = await request({ url: '/system/notify/wecom-app/push-todos', method: 'post' })
    pushResult.value = res.data || {}
    ElMessage.success(`推送完成：成功 ${pushResult.value.sent ?? 0} 人`)
  } finally { wecomPushing.value = false }
}

async function loadWecom() {
  const res = await request({ url: '/system/notify/wecom', method: 'get' })
  wecom.value = { webhookUrl: res.data?.webhookUrl || '', enabled: !!res.data?.enabled }
}
async function saveWecom() {
  wecomSaving.value = true
  try {
    await request({ url: '/system/notify/wecom', method: 'put', data: wecom.value })
    ElMessage.success('已保存')
  } finally { wecomSaving.value = false }
}
async function testWecom() {
  wecomTesting.value = true
  try {
    await saveWecom()
    const res = await request({ url: '/system/notify/wecom/test', method: 'post' })
    ElMessage.success(res.data || '已发送')
  } finally { wecomTesting.value = false }
}

async function loadData() {
  const res = await integrationStatus()
  systems.value = res.data
}

async function handleTest(code) {
  const res = await testIntegration(code)
  ElMessage.success(res.data ? `${code} 连接成功` : `${code} 连接失败`)
  loadData()
}

async function handleDecompose(file) {
  const fd = new FormData()
  fd.append('file', file)
  ElMessage.info('正在调用算法服务分解图纸...')
  try {
    const res = await decomposeDrawing(fd)
    decomposeResult.value = res.data
    ElMessage.success('图纸分解完成')
  } catch (e) {
    console.error(e)
  }
  return false
}

onMounted(() => { loadData(); loadWecom(); loadWecomApp() })
</script>
