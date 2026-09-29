<template>
  <div class="page-container">
    <OperationGuide module-key="sys-permission" />
    <el-card>
      <div class="table-toolbar">
        <el-button type="primary" icon="Plus" @click="openDialog()">新增权限项</el-button>
        <el-button icon="Refresh" @click="loadData">刷新</el-button>
        <span class="toolbar-hint">权限类型：1 菜单 / 2 按钮 / 3 接口；删除前需先清空子节点</span>
      </div>
      <el-table :data="tree" border row-key="id" default-expand-all
        :tree-props="{ children: 'children' }">
        <el-table-column prop="permName" label="权限名称" min-width="200" />
        <el-table-column prop="permCode" label="权限编码" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{row}">
            <el-tag :type="typeTag[row.permType] || 'info'" size="small">{{ typeMap[row.permType] || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由路径" min-width="160" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="可见" width="80" align="center">
          <template #default="{row}">
            <el-tag :type="row.visible === 1 ? 'success' : 'info'" size="small">
              {{ row.visible === 1 ? '显示' : '隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openDialog(null, row)">新增子项</el-button>
            <el-button link type="warning" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="上级权限">
          <el-tree-select v-model="form.parentId" :data="parentOptions" check-strictly
            :render-after-expand="false" node-key="id" :props="{ label:'permName', children:'children' }"
            placeholder="不选则为顶级" clearable style="width:100%" />
        </el-form-item>
        <el-form-item label="权限编码" prop="permCode">
          <el-input v-model="form.permCode" placeholder="如 system:permission" />
        </el-form-item>
        <el-form-item label="权限名称" prop="permName">
          <el-input v-model="form.permName" />
        </el-form-item>
        <el-form-item label="权限类型" prop="permType">
          <el-select v-model="form.permType" style="width:100%">
            <el-option v-for="(v,k) in typeMap" :key="k" :label="v" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="路由路径"><el-input v-model="form.path" placeholder="如 /admin/permission" /></el-form-item>
        <el-form-item label="组件路径"><el-input v-model="form.component" placeholder="如 system/permission" /></el-form-item>
        <el-form-item label="图标"><el-input v-model="form.icon" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="可见">
          <el-switch v-model="form.visible" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPermissionTree, createPermission, updatePermission, deletePermission } from '@/api/system'

const tree = ref([])
const formRef = ref()
const submitting = ref(false)
const typeMap = { 1: '菜单', 2: '按钮', 3: '接口' }
const typeTag = { 1: 'primary', 2: 'success', 3: 'warning' }
const dialog = reactive({ visible: false, title: '' })
const form = reactive({})

const rules = {
  permCode: [{ required: true, message: '权限编码不能为空', trigger: 'blur' }],
  permName: [{ required: true, message: '权限名称不能为空', trigger: 'blur' }],
  permType: [{ required: true, message: '请选择权限类型', trigger: 'change' }]
}

// 编辑时不能把自己或自己的后代作为父节点，否则会形成环
const parentOptions = computed(() => {
  const excluded = form.id
  const prune = (nodes) => nodes
    .filter(n => n.id !== excluded)
    .map(n => ({ id: n.id, permName: n.permName, children: prune(n.children || []) }))
  return [{ id: 0, permName: '顶级', children: prune(tree.value) }]
})

async function loadData() {
  const res = await getPermissionTree()
  tree.value = res.data || []
}

function openDialog(row, parent) {
  Object.keys(form).forEach(k => delete form[k])
  if (row) {
    Object.assign(form, JSON.parse(JSON.stringify(row)))
    delete form.children
    dialog.title = '编辑权限项'
  } else {
    form.parentId = parent ? parent.id : 0
    form.permType = 1
    form.sortOrder = 0
    form.visible = 1
    dialog.title = parent ? `新增子项（上级：${parent.permName}）` : '新增权限项'
  }
  dialog.visible = true
  nextTick(() => formRef.value && formRef.value.clearValidate())
}

async function submitForm() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const payload = { ...form, parentId: form.parentId || 0 }
    if (form.id) {
      await updatePermission(payload)
      ElMessage.success('修改成功')
    } else {
      await createPermission(payload)
      ElMessage.success('新增成功')
    }
    dialog.visible = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`删除权限项[${row.permName}]？其角色授权关系将一并清除。`, '警告', { type: 'warning' })
  await deletePermission(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.toolbar-hint {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
