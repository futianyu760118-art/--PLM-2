<template>
  <div class="page-container">
    <OperationGuide module-key="sys-log" />
    <el-card>
      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="操作人"><el-input v-model="query.operator" clearable /></el-form-item>
        <el-form-item label="操作类型"><el-input v-model="query.operation" clearable /></el-form-item>
        <el-form-item label="料号"><el-input v-model="query.partNo" clearable /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="loadData">查询</el-button></el-form-item>
      </el-form>
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="operator" label="操作人" width="110" />
        <el-table-column prop="username" label="账号" width="120" />
        <el-table-column prop="operation" label="操作类型" min-width="160" show-overflow-tooltip />
        <el-table-column prop="method" label="请求方法" min-width="240" show-overflow-tooltip />
        <el-table-column prop="partNo" label="关联料号" width="140" />
        <el-table-column prop="fileVersion" label="文件版本" width="100" align="center" />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column label="结果" width="80" align="center">
          <template #default="{row}"><el-tag :type="row.result===1?'success':'danger'" size="small">{{ row.result===1?'成功':'失败' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="costMs" label="耗时(ms)" width="90" align="right" />
        <el-table-column prop="createdAt" label="操作时间" width="160" fixed="right" />
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[20,50,100]" layout="total,sizes,prev,pager,next,jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { pageLog } from '@/api/system'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ pageNum:1, pageSize:20, operator:'', operation:'', partNo:'' })

async function loadData() {
  loading.value = true
  try {
    const res = await pageLog(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

onMounted(loadData)
</script>
