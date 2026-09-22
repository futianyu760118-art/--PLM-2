<template>
  <div class="page-container">
    <OperationGuide module-key="improve" />
    <el-tabs v-model="activeTab">
      <el-tab-pane label="问题看板" name="issues">
        <div class="table-toolbar">
          <el-button type="primary" icon="Plus" @click="openIssueDialog">创建问题</el-button>
          <el-button @click="loadIssues">刷新</el-button>
        </div>
        <el-table v-loading="issueLoading" :data="issues" border stripe size="small">
          <el-table-column prop="issueNo" label="编号" width="150" />
          <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
          <el-table-column prop="category" label="分类" width="100" />
          <el-table-column label="严重度" width="80" align="center">
            <template #default="{row}">
              <el-tag :type="sevTag(row.severity)" size="small">{{ row.severity }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sourceType" label="来源" width="80" />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{row}">{{ row.status }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建" width="160" />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{row}">
              <el-button link size="small" type="primary" @click="openIssueDetail(row)">详情</el-button>
              <el-button link size="small" type="success" v-if="row.status==='OPEN'" @click="changeStatus(row.id,'ANALYZING')">分析</el-button>
              <el-button link size="small" type="warning" v-if="row.status==='ACTION'" @click="changeStatus(row.id,'VERIFY')">验证</el-button>
              <el-button link size="small" type="info" v-if="row.status==='VERIFY'" @click="changeStatus(row.id,'CLOSED')">关闭</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="洞察列表" name="insights">
        <div class="table-toolbar">
          <el-button type="primary" icon="View" @click="runPatrol">质检巡检(生成洞察)</el-button>
          <el-button @click="loadInsights">刷新</el-button>
        </div>
        <el-table v-loading="insightLoading" :data="insights" border stripe size="small">
          <el-table-column prop="insightNo" label="编号" width="180" />
          <el-table-column prop="title" label="洞察" min-width="250" show-overflow-tooltip />
          <el-table-column prop="category" label="分类" width="100" />
          <el-table-column label="严重度" width="80" align="center">
            <template #default="{row}">
              <el-tag :type="sevTag(row.severity)" size="small">{{ row.severity }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="relatedKpiCodes" label="关联KPI" width="160" />
          <el-table-column prop="createdAt" label="时间" width="160" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{row}">
              <el-button link size="small" type="primary" @click="convertInsight(row.id)">转问题</el-button>
              <el-button link size="small" type="info" @click="dismissInsight(row.id)">忽略</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="issueDialog.visible" title="创建问题" width="520px">
      <el-form :model="issueDialog.form" label-width="80px">
        <el-form-item label="标题" required><el-input v-model="issueDialog.form.title" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="issueDialog.form.category" style="width:100%">
            <el-option v-for="c in ['QUALITY','LEAN','CYCLE','DELIVERY','SAFETY']" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="严重度">
          <el-select v-model="issueDialog.form.severity" style="width:100%">
            <el-option v-for="s in ['HIGH','MEDIUM','LOW']" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="issueDialog.form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueDialog.visible=false">取消</el-button>
        <el-button type="primary" @click="submitIssue">创建</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawer.visible" :title="'问题详情 - ' + (detailDrawer.issue?.issueNo||'')" size="600px">
      <el-descriptions :column="1" border v-if="detailDrawer.issue">
        <el-descriptions-item label="标题">{{ detailDrawer.issue.title }}</el-descriptions-item>
        <el-descriptions-item label="分类/严重度">{{ detailDrawer.issue.category }} / {{ detailDrawer.issue.severity }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ detailDrawer.issue.description || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-divider>改善对策</el-divider>
      <div style="margin-bottom:10px">
        <el-button size="small" type="primary" icon="Plus" @click="addActionVisible=true">添加对策</el-button>
      </div>
      <el-table :data="detailDrawer.actions" border size="small">
        <el-table-column prop="actionNo" label="编号" width="140" />
        <el-table-column prop="title" label="对策" min-width="180" />
        <el-table-column prop="actionType" label="类型" width="90" />
        <el-table-column prop="status" label="状态" width="70" />
        <el-table-column label="操作" width="70">
          <template #default="{row}">
            <el-button link size="small" type="success" v-if="row.status!=='DONE'" @click="completeAction(row.id)">完成</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="addActionVisible" title="添加对策" width="460px" append-to-body>
        <el-form :model="newAction" label-width="70px">
          <el-form-item label="对策" required><el-input v-model="newAction.title" /></el-form-item>
          <el-form-item label="类型">
            <el-select v-model="newAction.actionType" style="width:100%">
              <el-option v-for="t in ['CORRECTIVE','PREVENTIVE','STANDARDIZE']" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="addActionVisible=false">取消</el-button>
          <el-button type="primary" @click="submitAction">添加</el-button>
        </template>
      </el-dialog>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listIssues, createIssue, updateIssueStatus, getIssue, listActions, createAction,
  listInsights, convertInsight as convertInsightApi, dismissInsight as dismissInsightApi,
  completeAction as completeActionApi, qaPatrol
} from '@/api/issue'

const activeTab = ref('issues')
const issueLoading = ref(false)
const insightLoading = ref(false)
const issues = ref([])
const insights = ref([])

const sevTag = (s) => ({ HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }[s] || 'info')
const issueDialog = reactive({ visible: false, form: { severity: 'MEDIUM', category: 'QUALITY' } })
const detailDrawer = reactive({ visible: false, issue: null, actions: [] })
const addActionVisible = ref(false)
const newAction = reactive({ title: '', actionType: 'CORRECTIVE' })

async function loadIssues() {
  issueLoading.value = true
  try {
    const res = await listIssues()
    issues.value = res.data || []
  } finally { issueLoading.value = false }
}

async function loadInsights() {
  insightLoading.value = true
  try {
    const res = await listInsights('NEW')
    insights.value = res.data || []
  } finally { insightLoading.value = false }
}

async function runPatrol() {
  insightLoading.value = true
  try {
    const res = await qaPatrol()
    const d = res.data || {}
    ElMessage.success(`巡检完成：生成洞察 ${d.generatedInsights ?? 0} 条，HIGH ${d.highSeverity ?? 0} 条已自动开 ISSUE`)
    await loadInsights()
    await loadIssues()
  } catch (e) {
    ElMessage.error('巡检失败')
  } finally { insightLoading.value = false }
}

function openIssueDialog() {
  issueDialog.form = { title: '', severity: 'MEDIUM', category: 'QUALITY', description: '' }
  issueDialog.visible.value = true
  issueDialog.visible = true
}

async function submitIssue() {
  if (!issueDialog.form.title) { ElMessage.warning('请填写标题'); return }
  await createIssue(issueDialog.form)
  ElMessage.success('已创建')
  issueDialog.visible = false
  loadIssues()
}

async function changeStatus(id, status) {
  await updateIssueStatus(id, status)
  ElMessage.success('状态已更新')
  loadIssues()
}

async function openIssueDetail(row) {
  detailDrawer.issue = row
  detailDrawer.visible = true
  const res = await listActions(row.id)
  detailDrawer.actions = res.data || []
}

async function submitAction() {
  if (!newAction.title) { ElMessage.warning('请填写对策'); return }
  await createAction(detailDrawer.issue.id, { ...newAction })
  ElMessage.success('已添加')
  addActionVisible.value = false
  newAction.title = ''
  const res = await listActions(detailDrawer.issue.id)
  detailDrawer.actions = res.data || []
}

async function completeAction(actionId) {
  await completeActionApi(actionId)
  ElMessage.success('对策已完成')
  const res = await listActions(detailDrawer.issue.id)
  detailDrawer.actions = res.data || []
}

async function convertInsight(id) {
  await convertInsightApi(id)
  ElMessage.success('已转为问题')
  loadInsights()
}

async function dismissInsight(id) {
  await dismissInsightApi(id)
  ElMessage.success('已忽略')
  loadInsights()
}

onMounted(() => { loadIssues(); loadInsights() })
</script>
