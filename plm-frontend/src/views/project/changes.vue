<template>
  <div class="page-container">
    <el-card>
      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="项目号"><el-input v-model="query.projectNo" clearable @keyup.enter="loadData" /></el-form-item>
        <el-form-item label="对象类型">
          <el-select v-model="query.objectType" clearable style="width:170px">
            <el-option v-for="t in types" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
          <el-button icon="Refresh" @click="loadData">刷新</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="tableData" border stripe size="small" height="calc(100vh - 260px)">
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="projectNo" label="项目号" width="150" />
        <el-table-column prop="objectType" label="对象类型" width="120" />
        <el-table-column prop="nodeCode" label="节点" width="120" />
        <el-table-column prop="action" label="动作" width="90" align="center">
          <template #default="{row}"><el-tag size="small" :type="actTag(row.action)">{{ row.action }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="fieldName" label="字段" width="90" />
        <el-table-column prop="oldValue" label="原值" min-width="110" show-overflow-tooltip />
        <el-table-column prop="newValue" label="新值" min-width="110" show-overflow-tooltip />
        <el-table-column prop="source" label="来源" width="90" align="center" />
        <el-table-column prop="operator" label="操作人" width="100" />
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
const types = ['PROJECT_NODE', 'PLAN', 'SPEC', 'CONFIG', 'SAMPLE', 'REVIEW', 'TEST', 'TRIAL', 'SHIPMENT']
const query = reactive({ pageNum: 1, pageSize: 20, projectNo: '', objectType: '' })
const actTag = (a) => ({ CREATE: 'success', UPDATE: 'primary', DELETE: 'danger', ROLLUP: 'warning', CELL: 'info' }[a] || 'info')

async function loadData() {
  loading.value = true
  try {
    const res = await pageChanges(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}
onMounted(loadData)
</script>
