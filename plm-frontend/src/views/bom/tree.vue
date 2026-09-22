<template>
  <div class="page-container">
    <el-card v-if="notFound">
      <el-result icon="warning" title="BOM 不存在" sub-title="该 BOM 可能已被删除或ID无效">
        <template #extra>
          <el-button type="primary" @click="router.push('/bom/list')">返回 BOM 列表</el-button>
        </template>
      </el-result>
    </el-card>
    <el-card v-else>
      <template #header>
        <div class="tree-header">
          <div>
            <el-button icon="ArrowLeft" @click="router.push('/bom/list')">返回</el-button>
            <span style="margin-left:12px;font-weight:600">BOM编号: {{ bom?.bomNo }}</span>
            <el-tag style="margin-left:8px" type="info">{{ bom?.rootPartNo }} · {{ bom?.versionNo }}</el-tag>
          </div>
          <div>
            <el-button type="primary" icon="Plus" @click="openAdd(0)">添加顶级子件</el-button>
            <el-button type="success" icon="Sort" :disabled="!orderedChildren.length" @click="saveReorder">
              保存排序 ({{ orderedChildren.length }})
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="treeData"
        row-key="id"
        border
        default-expand-all
        :tree-props="{ children: 'children' }"
        :row-class-name="rowClass"
      >
        <el-table-column label="拖拽" width="50" align="center">
          <template #default="{ row }">
            <el-icon
              v-if="row.parentItemId === 0"
              style="cursor:move;color:#909399;font-size:18px"
              draggable="true"
              @dragstart="onDragStart(row, $event)"
              @dragend="e => e.target.classList.remove('dragging')"
              @drop="onDrop(row, $event)"
              @dragover="onDragOver($event)"
              @dragleave="e => e.target.classList.remove('drag-over')"
              @mousedown="e => e.preventDefault()"
            >
              <Rank />
            </el-icon>
          </template>
        </el-table-column>
        <el-table-column prop="partName" label="零件名称" min-width="220" />
        <el-table-column prop="partNo" label="料号" min-width="160" />
        <el-table-column prop="quantity" label="数量" width="90" align="center" />
        <el-table-column prop="materialTexture" label="材质" width="100" />
        <el-table-column prop="specification" label="规格" min-width="140" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="80" align="center" />
        <el-table-column label="自制/外购" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.makeType===0?'info':'success'">{{ row.makeType===0?'自制':'外购' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="levelNo" label="层级" width="80" align="center" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openAdd(row.id)">添加子件</el-button>
            <el-button link type="warning" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-alert style="margin-top:16px" type="info" :closable="false"
        title="说明: 拖拽子件排序后,点「保存排序」提交; 删除父件会级联删除其全部子件; 系统自动检测循环引用,防止 BOM 死循环" />
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="子件料号" prop="partNo"><el-input v-model="form.partNo" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="零件名称"><el-input v-model="form.partName" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="数量" prop="quantity"><el-input-number v-model="form.quantity" :min="1" :precision="4" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单位"><el-input v-model="form.unit" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="制造类型">
              <el-radio-group v-model="form.makeType">
                <el-radio :value="0">自制</el-radio>
                <el-radio :value="1">外购</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="材质"><el-input v-model="form.materialTexture" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="规格"><el-input v-model="form.specification" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Rank } from '@element-plus/icons-vue'
import { getBom, getBomTree, addBomItem, updateBomItem, deleteBomItem } from '@/api/bom'
import { reorderItems } from '@/api/bomTpl'

const route = useRoute()
const router = useRouter()
const bomId = computed(() => route.params.bomId)

const loading = ref(false)
const submitting = ref(false)
const savingOrder = ref(false)
const bom = ref(null)
const treeData = ref([])
const formRef = ref()
const dialog = reactive({ visible: false, title: '' })
const form = reactive({})
const rules = {
  partNo: [{ required: true, message: '请输入子件料号', trigger: 'blur' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }]
}

// 拖拽排序状态: 有序的顶级子件 id 列表(由用户调整)
const orderedChildren = ref([])
// 原始顺序(对比是否有变化)
const originalOrder = ref([])

const notFound = ref(false)

function rowClass({ row }) {
  if (row.parentItemId === 0 || row.parentItemId == null) return 'drag-row'
  return ''
}

// 顶级子件(非根节点,且 parent_item_id=0)
function syncOrderFromTree() {
  const top = (treeData.value || []).map(r => r.id)
  orderedChildren.value = [...top]
  originalOrder.value = [...top]
}

async function loadData() {
  if (!bomId.value || isNaN(Number(bomId.value))) {
    notFound.value = true
    return
  }
  loading.value = true
  try {
    const b = await getBom(bomId.value)
    bom.value = b.data
    const t = await getBomTree(bomId.value)
    treeData.value = t.data
    syncOrderFromTree()
  } catch (e) {
    notFound.value = true
    bom.value = null
    treeData.value = []
  } finally { loading.value = false }
}

function openAdd(parentId) {
  Object.keys(form).forEach(k => delete form[k])
  form.bomId = Number(bomId.value)
  form.parentItemId = parentId
  form.quantity = 1
  form.makeType = 0
  form.sortOrder = 0
  dialog.title = parentId === 0 ? '添加顶级子件' : '添加子件'
  dialog.visible = true
}

function openEdit(row) {
  Object.keys(form).forEach(k => delete form[k])
  Object.assign(form, JSON.parse(JSON.stringify(row)))
  dialog.title = '编辑明细'
  dialog.visible = true
}

async function submitForm() {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (form.id) { await updateBomItem(form); ElMessage.success('修改成功') }
    else { await addBomItem(form); ElMessage.success('添加成功') }
    dialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`删除 [${row.partName}] 及其全部子件?`, '警告', { type: 'warning' })
  await deleteBomItem(row.id)
  ElMessage.success('删除成功')
  loadData()
}

/** 拖拽接收: el-tree 内置 HTML5 drag-drop */
function onDragStart(row, e) {
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', String(row.id))
  e.target.classList.add('dragging')
}
function onDragOver(e) {
  e.preventDefault()
  e.dataTransfer.dropEffect = 'move'
}
function onDragLeave(row, e) {
  e.target.classList.remove('drag-over')
}
function onDrop(row, e) {
  e.preventDefault()
  const fromId = Number(e.dataTransfer.getData('text/plain'))
  const toId = row.id
  e.target.classList.remove('drag-over')
  if (fromId === toId || isNaN(fromId) || isNaN(toId)) return
  // 只允许顶级子件之间重排
  if (row.parentItemId !== 0) return
  const arr = [...orderedChildren.value]
  const fromIdx = arr.indexOf(fromId)
  const toIdx = arr.indexOf(toId)
  if (fromIdx < 0 || toIdx < 0) return
  arr.splice(fromIdx, 1)
  arr.splice(toIdx, 0, fromId)
  orderedChildren.value = arr
  // 立即重排 treeData(本地视觉)
  const map = new Map(treeData.value.map(r => [r.id, r]))
  treeData.value = arr.map(id => map.get(id)).filter(Boolean)
}
async function saveReorder() {
  if (orderedChildren.value.length === 0) { ElMessage.info('无可排序明细'); return }
  savingOrder.value = true
  try {
    await reorderItems(Number(bomId.value), orderedChildren.value)
    ElMessage.success('排序已保存')
    loadData()
  } finally { savingOrder.value = false }
}

// el-table 拖拽实现: 用原生 HTML5 dnd events
import { onMounted as _om, nextTick as _nv } from 'vue'
_om(() => {
  // no-op(占位)
})

onMounted(loadData)
</script>

<style scoped>
.tree-header { display: flex; justify-content: space-between; align-items: center; }
:deep(.drag-row) { cursor: move; }
:deep(.drag-row td:first-child) { background: #f8fafc; }
:deep(.drag-row.dragging) { opacity: 0.4; background: #e1f5fe !important; }
:deep(.drag-row.drag-over) { background: #fff7e6 !important; }
</style>
