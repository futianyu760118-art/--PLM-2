<template>
  <div class="page-container">
    <OperationGuide module-key="dq" />
    <!-- 头部操作 -->
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <div>
            <el-icon style="vertical-align:middle" :size="20"><DataAnalysis /></el-icon>
            <span style="font-weight:600;margin-left:4px">数据自检 + 智能修复</span>
            <el-tag style="margin-left:8px" size="small" type="info">DQ</el-tag>
          </div>
          <div>
            <el-button @click="loadAll" :loading="loading">刷新</el-button>
            <el-button type="warning" @click="batchFix" :loading="fixing">批量修复 TOP {{ fixLimit }}</el-button>
            <el-button type="primary" @click="triggerScan" :loading="scanning">触发夜检</el-button>
          </div>
        </div>
      </template>

      <!-- 汇总 -->
      <el-row :gutter="12" style="margin-bottom:16px">
        <el-col :span="6">
          <el-card shadow="hover" :body-style="{padding:'14px'}">
            <el-statistic title="规则总数" :value="rulesCount" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" :body-style="{padding:'14px'}">
            <el-statistic title="未关闭债务" :value="openDebts.length" :value-style="{color:'#e6a23c'}" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" :body-style="{padding:'14px'}">
            <el-statistic title="已闭环债务" :value="closedDebts.length" :value-style="{color:'#67c23a'}" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" :body-style="{padding:'14px'}">
            <el-statistic title="修复次数(已闭环)" :value="resolvedAttempts" :value-style="{color:'#409eff'}" />
          </el-card>
        </el-col>
      </el-row>

      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- 债务列表 -->
        <el-tab-pane label="问题债务" name="debts">
          <el-table :data="openDebts" border stripe size="small" v-loading="loading">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="ruleCode" label="规则" width="180" />
            <el-table-column prop="objectId" label="对象" min-width="140">
              <template #default="{row}">
                <el-link type="primary" underline="never">{{ row.objectId }}</el-link>
              </template>
            </el-table-column>
            <el-table-column label="严重度" width="80" align="center">
              <template #default="{row}">
                <el-tag :type="row.severity==='BLOCK'?'danger':row.severity==='WARN'?'warning':'info'" size="small">
                  {{ row.severity }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="描述" min-width="220" show-overflow-tooltip />
            <el-table-column prop="waiveReason" label="处理备注" min-width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="100" fixed="right" align="center">
              <template #default="{row}">
                <el-button link size="small" type="primary" @click="fixOne(row.id)" :loading="fixingId===row.id">
                  智能修复
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 修复尝试记录 -->
        <el-tab-pane label="修复记录" name="attempts">
          <el-table :data="attempts" border stripe size="small" v-loading="loading">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="ruleCode" label="规则" width="180" />
            <el-table-column prop="objectId" label="对象" width="140" />
            <el-table-column prop="strategy" label="策略" width="140">
              <template #default="{row}">
                <el-tag size="small" :type="row.linked_issue_id?'warning':'primary'">{{ row.strategy || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="前后分" width="130" align="center">
              <template #default="{row}">
                <span :style="{color: (row.after_score||0) > (row.before_score||0) ? '#67c23a' : (row.after_score||0) < (row.before_score||0) ? '#f56c6c' : '#909399'}">
                  {{ row.before_score || 0 }} → {{ row.after_score || 0 }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="解决" width="80" align="center">
              <template #default="{row}">
                <el-tag :type="row.resolved?'success':'info'" size="small">{{ row.resolved ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sourceDebtId" label="原债务ID" width="100" />
            <el-table-column prop="linkedIssueId" label="关联Issue" width="100">
              <template #default="{row}">
                <span v-if="row.linked_issue_id">IQ{{ row.linked_issue_id }}</span>
                <span v-else style="color:#c0c4cc">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="attemptedBy" label="来源" width="80" />
            <el-table-column prop="createdAt" label="时间" width="160" />
          </el-table>
        </el-tab-pane>

        <!-- 规则清单 -->
        <el-tab-pane label="DQ 规则(38条)" name="rules">
          <el-table :data="rules" border stripe size="small" v-loading="loading">
            <el-table-column prop="rule_code" label="规则编码" width="180" />
            <el-table-column prop="name" label="名称" min-width="180" />
            <el-table-column prop="object_type" label="对象" width="80" />
            <el-table-column label="严重度" width="90" align="center">
              <template #default="{row}">
                <el-tag :type="row.severity==='BLOCK'?'danger':row.severity==='WARN'?'warning':'info'" size="small">{{ row.severity }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="check_type" label="类型" width="100" />
            <el-table-column prop="message_template" label="消息模板" min-width="200" show-overflow-tooltip />
            <el-table-column label="自动修复" width="140" align="center">
              <template #default="{row}">
                <el-tag v-if="row.auto_fix_strategy" size="small" type="primary">
                  {{ extractStrategy(row.auto_fix_strategy) }}
                </el-tag>
                <el-tag v-else size="small" type="info">无</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="启用" width="70" align="center">
              <template #default="{row}">
                <el-tag :type="row.enabled?'success':'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataAnalysis } from '@element-plus/icons-vue'
import { listDebts, listRules, listAttempts, fixDebt, batchFixDebts } from '@/api/dq'
import { runJob } from '@/api/job'

const loading = ref(false)
const scanning = ref(false)
const fixing = ref(false)
const fixingId = ref(null)
const fixLimit = ref(5)

const openDebts = ref([])
const closedDebts = ref([])
const attempts = ref([])
const rules = ref([])
const activeTab = ref('debts')

const rulesCount = computed(() => rules.value.length)
const resolvedAttempts = computed(() => attempts.value.filter(a => a.resolved).length)

async function loadAll() {
  loading.value = true
  try {
    const [debtsRes, attemptsRes, rulesRes] = await Promise.all([
      listDebts(), listAttempts(), listRules()
    ])
    const debts = debtsRes.data || []
    openDebts.value = debts.filter(d => d.status === 'OPEN')
    closedDebts.value = debts.filter(d => d.status === 'CLOSED')
    attempts.value = attemptsRes.data || []
    rules.value = rulesRes.data || []
  } finally {
    loading.value = false
  }
}

function onTabChange(tab) {
  if (tab === 'attempts') loadAttempts()
  else if (tab === 'rules') loadRules()
}

async function loadAttempts() {
  loading.value = true
  try {
    const r = await listAttempts()
    attempts.value = r.data || []
  } finally { loading.value = false }
}

async function loadRules() {
  loading.value = true
  try {
    const r = await listRules()
    rules.value = r.data || []
  } finally { loading.value = false }
}

async function fixOne(id) {
  fixingId.value = id
  try {
    const r = await fixDebt(id)
    const data = r.data
    if (data.resolved) {
      ElMessage.success(`✅ ${data.ruleCode} 已自动修复 (${data.beforeScore} → ${data.afterScore})`)
    } else if (data.skipReason === 'NO_OP') {
      ElMessage.warning(`⚠️ ${data.ruleCode} 需人工干预,已升级为改善单`)
    } else {
      ElMessage.info(`${data.ruleCode} 修复结果: ${data.skipReason || data.strategy}`)
    }
    loadAll()
  } finally {
    fixingId.value = null
  }
}

async function batchFix() {
  await ElMessageBox.confirm(
    `将对未关闭债务 TOP ${fixLimit} 条尝试自动修复。是否继续?`,
    '批量智能修复', { type: 'warning' }
  )
  fixing.value = true
  try {
    const r = await batchFixDebts(fixLimit.value)
    ElMessage.success(`已尝试 ${r.data.attempted} 条,解决 ${r.data.resolved} 条`)
    loadAll()
  } finally {
    fixing.value = false
  }
}

async function triggerScan() {
  scanning.value = true
  try {
    const r = await runJob('dq/scan')
    ElMessage.success(`DQ 夜检完成:扫描 ${r.data.scanned} 个料号`)
    loadAll()
  } finally {
    scanning.value = false
  }
}

function extractStrategy(json) {
  if (!json) return '-'
  try {
    const o = typeof json === 'string' ? JSON.parse(json) : json
    return o.strategy || '-'
  } catch { return '-' }
}

onMounted(loadAll)
</script>