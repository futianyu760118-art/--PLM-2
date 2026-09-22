<template>
  <div class="page-container">
    <OperationGuide module-key="sys-check" />
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <div>
            <el-icon style="vertical-align:middle" :size="20"><Monitor /></el-icon>
            <span style="font-weight:600;margin-left:4px">系统自检</span>
          </div>
          <div>
            <el-button type="primary" icon="Refresh" :loading="loading" @click="runCheck">运行自检</el-button>
          </div>
        </div>
      </template>

      <!-- 汇总卡片 -->
      <el-row :gutter="12" style="margin-bottom:16px" v-if="summary">
        <el-col :span="6">
          <el-card shadow="hover" :body-style="{padding:'16px'}">
            <div style="display:flex;align-items:center;gap:12px">
              <el-statistic title="检查项总数" :value="summary.total" />
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" :body-style="{padding:'16px'}">
            <el-statistic title="通过" :value="summary.pass">
              <template #suffix><el-icon color="#67c23a" style="vertical-align:middle"><CircleCheckFilled /></el-icon></template>
            </el-statistic>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" :body-style="{padding:'16px'}">
            <el-statistic title="警告" :value="summary.warn">
              <template #suffix><el-icon color="#e6a23c" style="vertical-align:middle"><WarningFilled /></el-icon></template>
            </el-statistic>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" :body-style="{padding:'16px'}">
            <el-statistic title="失败" :value="summary.fail" :value-style="{color: summary.fail > 0 ? '#f56c6c' : ''}">
              <template #suffix><el-icon color="#f56c6c" style="vertical-align:middle"><CircleCloseFilled /></el-icon></template>
            </el-statistic>
          </el-card>
        </el-col>
      </el-row>

      <!-- 整体状态 -->
      <el-alert v-if="summary" :type="summary.fail === 0 ? 'success' : 'error'" :closable="false" style="margin-bottom:16px">
        <template #title>
          <span style="font-size:15px;font-weight:600">
            {{ summary.fail === 0 ? '✅ 系统状态健康 — 所有检查项通过' : '❌ 发现 ' + summary.fail + ' 个问题需要修复' }}
          </span>
          <span style="margin-left:12px;font-size:12px;color:#909399">检测时间: {{ formatTime(timestamp) }}</span>
        </template>
      </el-alert>

      <!-- 检查项列表 -->
      <el-table :data="checks" v-loading="loading" border stripe>
        <el-table-column label="#" width="50" type="index" align="center" />
        <el-table-column prop="name" label="检查项" min-width="180">
          <template #default="{row}">
            <strong>{{ row.name }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{row}">
            <el-tag :type="statusTag(row.status)" effect="dark" size="small">
              <el-icon style="vertical-align:middle">
                <CircleCheckFilled v-if="row.status==='PASS'" />
                <WarningFilled v-else-if="row.status==='WARN'" />
                <CircleCloseFilled v-else />
              </el-icon>
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="详情" min-width="400" show-overflow-tooltip />
      </el-table>

      <!-- 说明 -->
      <el-card style="margin-top:16px" shadow="never">
        <template #header><span style="font-size:13px">自检覆盖范围 (11 项)</span></template>
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="1. 数据库连通性">PostgreSQL 连接 + 核心表查询</el-descriptions-item>
          <el-descriptions-item label="2. 核心表完整性">16 张业务表存在性验证</el-descriptions-item>
          <el-descriptions-item label="3. deleted 列完整性">BaseEntity 表软删除列 (防回归 Bug)</el-descriptions-item>
          <el-descriptions-item label="4. ltree 扩展">BOM 树形查询所需 PG 扩展</el-descriptions-item>
          <el-descriptions-item label="5. 六大固定角色">ADMIN/ENGINEER/QUALITY/SALES/SUPPLIER/CUSTOMER</el-descriptions-item>
          <el-descriptions-item label="6. Admin 权限完整性">管理员拥有全部权限码</el-descriptions-item>
          <el-descriptions-item label="7. 管理员账号可用">admin 账号 + BCrypt 密码 + 启用状态</el-descriptions-item>
          <el-descriptions-item label="8. 编号序列">料号/ECN/BOM/外协/模具 自增序列</el-descriptions-item>
          <el-descriptions-item label="9. 算法微服务">Python 算法服务健康检查</el-descriptions-item>
          <el-descriptions-item label="10. 业务数据统计">各模块数据量一览</el-descriptions-item>
          <el-descriptions-item label="11. 档案目录树">17 层固定目录模板就绪状态</el-descriptions-item>
        </el-descriptions>
      </el-card>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const loading = ref(false)
const checks = ref([])
const summary = ref(null)
const timestamp = ref('')

async function runCheck() {
  loading.value = true
  checks.value = []
  summary.value = null
  try {
    const res = await import('@/api/check').then(m => m.runSystemCheck())
    checks.value = res.data.checks
    summary.value = res.data.summary
    timestamp.value = res.data.timestamp
  } finally {
    loading.value = false
  }
}

function statusTag(s) {
  return { PASS: 'success', WARN: 'warning', FAIL: 'danger' }[s] || 'info'
}
function statusLabel(s) {
  return { PASS: '通过', WARN: '警告', FAIL: '失败' }[s] || s
}
function formatTime(ts) {
  if (!ts) return ''
  try { return new Date(ts).toLocaleString('zh-CN') } catch { return ts }
}

onMounted(runCheck)
</script>
