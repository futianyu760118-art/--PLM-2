<template>
  <div class="page-container">
    <OperationGuide module-key="sys-role" />
    <el-card>
      <div class="table-toolbar">
        <el-button type="primary" icon="Plus" @click="openDialog()">新增角色</el-button>
      </div>
      <el-table :data="roles" border stripe>
        <el-table-column prop="roleCode" label="角色编码" min-width="140" />
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column prop="roleLevel" label="级别" width="80" align="center" />
        <el-table-column label="数据范围" width="120" align="center">
          <template #default="{row}">{{ scopeMap[row.dataScope] }}</template>
        </el-table-column>
        <el-table-column label="内置" width="80" align="center">
          <template #default="{row}"><el-tag v-if="row.builtin===1" type="warning">内置</el-tag></template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="240" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openPermission(row)">分配权限</el-button>
            <el-button link type="warning" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" size="small" v-if="row.builtin!==1" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="480px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="角色编码"><el-input v-model="form.roleCode" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="角色名称"><el-input v-model="form.roleName" /></el-form-item>
        <el-form-item label="级别"><el-input-number v-model="form.roleLevel" :min="0" :max="9" /></el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="form.dataScope" style="width:100%">
            <el-option v-for="(v,k) in scopeMap" :key="k" :label="v" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="permDrawer.visible" title="分配权限" size="500px">
      <el-tree ref="permTreeRef" :data="permTree" :props="{label:'permName', children:'children'}" node-key="id"
        show-checkbox default-expand-all :default-checked-keys="checkedKeys" />
      <template #footer>
        <el-button @click="permDrawer.visible=false">取消</el-button>
        <el-button type="primary" @click="submitPermission">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listRoleAll, createRole, updateRole, deleteRole, listPermissions, getRolePermissions, assignPermissions } from '@/api/system'

const roles = ref([])
const allPerms = ref([])
const checkedKeys = ref([])
const permTreeRef = ref()
const submitting = ref(false)
const scopeMap = { 1:'全部数据', 2:'本部门', 3:'仅本人' }
const dialog = reactive({ visible:false, title:'' })
const form = reactive({})
const permDrawer = reactive({ visible:false, roleId:null })

const permTree = computed(() => {
  const map = {}
  const roots = []
  allPerms.value.forEach(p => { map[p.id] = { ...p, children: [] } })
  allPerms.value.forEach(p => {
    if (!p.parentId || p.parentId === 0) {
      roots.push(map[p.id])
    } else if (map[p.parentId]) {
      map[p.parentId].children.push(map[p.id])
    }
  })
  return roots
})

async function loadData() {
  const [r, p] = await Promise.all([listRoleAll(), listPermissions()])
  roles.value = r.data
  allPerms.value = p.data
}

function openDialog(row) {
  Object.keys(form).forEach(k => delete form[k])
  if (row) { Object.assign(form, JSON.parse(JSON.stringify(row))); dialog.title = '编辑角色' }
  else { form.roleLevel = 1; form.dataScope = 1; form.status = 1; dialog.title = '新增角色' }
  dialog.visible = true
}

async function submitForm() {
  submitting.value = true
  try {
    if (form.id) { await updateRole(form); ElMessage.success('修改成功') }
    else { await createRole(form); ElMessage.success('新增成功') }
    dialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function openPermission(row) {
  permDrawer.roleId = row.id
  const res = await getRolePermissions(row.id)
  checkedKeys.value = res.data
  permDrawer.visible = true
}

async function submitPermission() {
  const checked = permTreeRef.value.getCheckedKeys()
  const halfChecked = permTreeRef.value.getHalfCheckedKeys()
  await assignPermissions(permDrawer.roleId, [...checked, ...halfChecked])
  ElMessage.success('权限已保存')
  permDrawer.visible = false
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`删除角色[${row.roleName}]?`, '警告', { type:'warning' })
  await deleteRole(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
