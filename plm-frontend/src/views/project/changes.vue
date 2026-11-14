<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2>变更留痕</h2>
        <p class="sub">记录各项目节点的每次修改：字段、原值、新值、操作人与时间，便于追溯。</p>
      </div>
    </div>
    <el-card>
      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="项目号"><el-input v-model="query.projectNo" clearable @keyup.enter="loadData" placeholder="如 HJ.26033..." style="width:180px" /></el-form-item>
        <el-form-item label="对象类型">
          <el-select v-model="query.objectType" clearable style="width:150px">
            <el-option v-for="t in types" :key="t.v" :label="t.l" :value="t.v" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
          <el-button icon="Refresh" @click="loadData">刷新</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="tableData" border stripe size="small" height="calc(100vh - 290px)">
        <el-table-column prop="createdAt" label="时间" width="165" />
        <el-table-column label="项目" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectNo }}<span v-if="row.projectName" class="muted"> {{ row.projectName }}</span></template>
        </el-table-column>
        <el-table-column label="节点" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.nodeName || row.nodeCode || '—' }}<span v-if="row.nodeName && row.nodeCode" class="muted"> ({{ row.nodeCode }})</span>
          </template>
        </el-table-column>
        <el-table-column label="对象" width="100" align="center">
          <template #default="{ row }">{{ objLabel(row.objectType) }}</template>
        </el-table-column>
        <el-table-column label="动作" width="130" align="center">
          <template #default="{ row }"><el-tag size="small" :type="actTag(row.action)">{{ actLabel(row.action) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="字段" width="110">
          <template #default="{ row }">{{ fieldLabel(row.fieldName) }}</template>
        </el-table-column>
        <el-table-column label="原值" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ valLabel(row.oldValue) }}</template>
        </el-table-column>
        <el-table-column label="新值" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ valLabel(row.newValue) }}</template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column prop="source" label="来源" width="80" align="center" />
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { pageChanges } from '@/api/project'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const types = [{ v: 'PROJECT_NODE', l: '项目节点' }, { v: 'PROJECT', l: '项目' }]
const query = reactive({ pageNum: 1, pageSize: 20, projectNo: '', objectType: '' })

const ACT = {
  CREATE: '新建', UPDATE: '修改', DELETE: '删除', ROLLUP: '汇总', CELL: '单元格', ADD_CHILD: '追加子项',
  PLAN_SUBMIT: '提交计划审批', PLAN_APPROVE: '计划通过', PLAN_REJECT: '计划驳回',
  CMPL_SUBMIT: '提交完成审批', CMPL_APPROVE: '完成通过', CMPL_REJECT: '完成驳回'
}
const OBJ = {
  PROJECT_NODE: '项目节点', PROJECT: '项目', PLAN: '计划', SPEC: '规格', CONFIG: '配置',
  SAMPLE: '样品', REVIEW: '评审', TEST: '测试', TRIAL: '试产', SHIPMENT: '出货'
}
const FIELD = { status: '状态', planApproval: '计划审批', completionApproval: '完成审批', nodeName: '子项名称' }
const STATUS = {
  DONE: '已完成', IN_PROGRESS: '进行中', PLANNED: '已计划', NOT_SET: '未设置', PENDING: '待定',
  FAILED: '未完成', NO_NODE: '无节点', PENDING_APPROVAL: '待审批', APPROVED: '已通过', REJECTED: '已驳回'
}
const actLabel = (a) => ACT[a] || a || '—'
const objLabel = (t) => OBJ[t] || t || '—'
const fieldLabel = (f) => FIELD[f] || f || '—'
const valLabel = (v) => (v == null || v === '') ? '—' : (STATUS[v] || v)
function actTag(a) {
  if (!a) return 'info'
  if (a.includes('REJECT') || a === 'DELETE') return 'danger'
  if (a.includes('APPROVE') || a === 'CREATE') return 'success'
  if (a.includes('SUBMIT') || a === 'ROLLUP') return 'warning'
  if (a === 'UPDATE' || a === 'CELL') return 'primary'
  return 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageChanges(query)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } finally { loading.value = false }
}
onMounted(loadData)
</script>

<style scoped>
.muted { color: #909399; font-size: 12px; }
</style>
