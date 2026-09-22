<template>
  <div class="page-container">
    <OperationGuide module-key="sys-user" />
    <el-card>
      <el-form :inline="true" :model="query" class="filter-bar">
        <el-form-item label="用户名"><el-input v-model="query.username" clearable /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="query.realName" clearable /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="loadData">查询</el-button></el-form-item>
      </el-form>
      <div class="table-toolbar">
        <el-button type="primary" icon="Plus" @click="openDialog()">新增用户</el-button>
      </div>
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="120" />
        <el-table-column prop="employeeNo" label="工号" width="100" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{row}"><el-tag :type="row.status===1?'success':'danger'">{{ row.status===1?'启用':'禁用' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" width="160" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="handleReset(row)">重置密码</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next,jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="560px">
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="用户名" required><el-input v-model="form.username" :disabled="!!form.id" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="姓名" required><el-input v-model="form.realName" /></el-form-item></el-col>
          <el-col :span="12" v-if="!form.id"><el-form-item label="密码"><el-input v-model="form.password" placeholder="默认123456" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="工号"><el-input v-model="form.employeeNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="角色">
            <el-select v-model="form.roleCodes" multiple style="width:100%">
              <el-option v-for="r in roles" :key="r.roleCode" :label="r.roleName" :value="r.roleCode" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="状态">
            <el-radio-group v-model="form.status"><el-radio :value="1">启用</el-radio><el-radio :value="0">禁用</el-radio></el-radio-group>
          </el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageUser, createUser, updateUser, resetPassword, deleteUser, listRoles } from '@/api/system'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const roles = ref([])
const query = reactive({ pageNum:1, pageSize:10, username:'', realName:'' })
const dialog = reactive({ visible:false, title:'' })
const form = reactive({})

async function loadData() {
  loading.value = true
  try {
    const res = await pageUser(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function openDialog(row) {
  Object.keys(form).forEach(k => delete form[k])
  if (row) { Object.assign(form, JSON.parse(JSON.stringify(row))); form.roleCodes = form.roleCodes || []; dialog.title = '编辑用户' }
  else { form.status = 1; form.roleCodes = []; form.password = '123456'; dialog.title = '新增用户' }
  dialog.visible = true
}

async function submitForm() {
  submitting.value = true
  try {
    if (form.id) { await updateUser(form); ElMessage.success('修改成功') }
    else { await createUser(form); ElMessage.success('新增成功') }
    dialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function handleReset(row) {
  const { value } = await ElMessageBox.prompt('请输入新密码', '重置密码', { inputValue:'' })
  await resetPassword(row.id, value)
  ElMessage.success('密码已重置')
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`删除用户[${row.username}]?`, '警告', { type:'warning' })
  await deleteUser(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(async () => {
  const res = await listRoles()
  roles.value = res.data
  loadData()
})
</script>
