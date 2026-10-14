<template>
  <div class="page-container">
    <OperationGuide module-key="subscriptionCategory" />

    <el-card>
      <template #header>
        <div class="card-head">
          <span>本部门分类维度</span>
          <span class="head-tip">
            维度最多 10 个 / 部门，分类值最多 50 个 / 维度；全局维度由系统预置，只读
          </span>
          <el-button v-if="canManage" type="primary" icon="Plus" @click="openCreate">新建维度</el-button>
        </div>
      </template>

      <!-- 维度列表：editable=false 的维度（全局/无管理权）不显示编辑入口（AC-B2-1/B2-6） -->
      <el-table v-loading="loading" :data="dims" border stripe row-key="id">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="value-box">
              <div class="value-head">
                <span>分类值（{{ row.values.length }}）</span>
                <el-button v-if="row.editable" link type="primary" size="small" @click="openAddValue(row)">
                  新增分类值
                </el-button>
              </div>
              <el-table :data="row.values" size="small" border>
                <el-table-column prop="valueName" label="分类值" min-width="160" show-overflow-tooltip />
                <el-table-column label="状态" width="100" align="center">
                  <template #default="{ row: v }">
                    <el-tag size="small" :type="v.status === 'ENABLED' ? 'success' : 'info'">
                      {{ v.status === 'ENABLED' ? '启用' : '已停用' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="180">
                  <template #default="{ row: v }">
                    <template v-if="row.editable">
                      <el-button link type="primary" size="small" @click="openEditValue(row, v)">改名</el-button>
                      <el-button link :type="v.status === 'ENABLED' ? 'warning' : 'success'" size="small"
                        @click="toggleValue(row, v)">
                        {{ v.status === 'ENABLED' ? '停用' : '启用' }}
                      </el-button>
                    </template>
                    <span v-else class="muted">—</span>
                  </template>
                </el-table-column>
                <template #empty>
                  <span class="muted">该维度暂无分类值</span>
                </template>
              </el-table>
              <div class="value-foot muted">
                停用后的分类值不再出现在新建分类的可选项里，但已打在资料上的历史标签照常保留与展示。
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="dimName" label="维度名称" min-width="150" />
        <el-table-column label="来源" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.global" size="small" type="info" effect="plain">全局（只读）</el-tag>
            <el-tag v-else-if="row.shared" size="small" type="warning" effect="plain">已共享</el-tag>
            <el-tag v-else size="small" effect="plain">本部门</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分类值" width="90" align="center">
          <template #default="{ row }">{{ row.values.length }}</template>
        </el-table-column>
        <el-table-column label="已用于资料" width="110" align="center">
          <template #default="{ row }">{{ row.usedCount ?? 0 }} 条</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <template v-if="row.editable">
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeDim(row)">删除</el-button>
            </template>
            <span v-else class="muted">无管理权</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="本部门还没有分类维度" :image-size="70">
            <el-button v-if="canManage" type="primary" @click="openCreate">新建第一个维度</el-button>
          </el-empty>
        </template>
      </el-table>
    </el-card>

    <el-dialog v-model="dimDlg.visible" :title="dimDlg.id ? '编辑分类维度' : '新建分类维度'" width="560px">
      <el-form label-width="110px">
        <el-form-item label="维度名称" required>
          <el-input v-model="dimDlg.dimName" maxlength="20" show-word-limit placeholder="如 产品线 / 阶段 / 客户" />
        </el-form-item>
        <el-form-item label="共享给他部门">
          <el-switch v-model="dimDlg.shared" />
          <span class="muted" style="margin-left:10px">共享后他部门可选该维度的值，但不能改定义</span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dimDlg.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <!-- 分类值只在新建时随维度一次提交，之后用展开行里的「新增分类值」追加 -->
        <el-form-item v-if="!dimDlg.id" label="分类值">
          <div class="val-edit">
            <div v-for="(v, i) in dimDlg.values" :key="i" class="val-row">
              <el-input v-model="dimDlg.values[i]" maxlength="30" placeholder="分类值，如 吸顶灯" />
              <el-button link type="danger" @click="dimDlg.values.splice(i, 1)">删除</el-button>
            </div>
            <el-button link type="primary" icon="Plus" @click="dimDlg.values.push('')">添加一行</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dimDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDim">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="valueDlg.visible" :title="valueDlg.valueId ? '分类值改名' : '新增分类值'" width="460px">
      <el-form label-width="90px">
        <el-form-item label="所属维度">
          <span>{{ valueDlg.dimName }}</span>
        </el-form-item>
        <el-form-item label="分类值" required>
          <el-input v-model="valueDlg.valueName" maxlength="30" show-word-limit placeholder="如 吸顶灯" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="valueDlg.sortOrder" :min="0" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="valueDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveValue">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import OperationGuide from '@/components/OperationGuide.vue'
import { useUserStore } from '@/stores/user'
import {
  categoryManage, createCategoryDim, updateCategoryDim, categoryDimUsage,
  deleteCategoryDim, addCategoryValue, updateCategoryValue
} from '@/api/subscription'

const userStore = useUserStore()
const canManage = computed(() => userStore.hasPermission('category:manage'))

const loading = ref(false)
const saving = ref(false)
const dims = ref([])

const dimDlg = reactive({ visible: false, id: null, dimName: '', shared: false, sortOrder: 0, values: [''] })
const valueDlg = reactive({ visible: false, dimId: null, dimName: '', valueId: null, valueName: '', sortOrder: 0 })

async function load() {
  loading.value = true
  try {
    const r = await categoryManage()
    dims.value = r.data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(dimDlg, { visible: true, id: null, dimName: '', shared: false, sortOrder: dims.value.length, values: [''] })
}

function openEdit(row) {
  Object.assign(dimDlg, {
    visible: true, id: row.id, dimName: row.dimName, shared: !!row.shared,
    sortOrder: row.sortOrder ?? 0, values: []
  })
}

async function saveDim() {
  if (!dimDlg.dimName.trim()) {
    ElMessage.warning('请填写维度名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      dimName: dimDlg.dimName.trim(),
      shared: dimDlg.shared,
      sortOrder: dimDlg.sortOrder,
      values: dimDlg.id ? undefined : dimDlg.values.map(v => v.trim()).filter(Boolean)
    }
    if (dimDlg.id) await updateCategoryDim(dimDlg.id, payload)
    else await createCategoryDim(payload)
    ElMessage.success('已保存')
    dimDlg.visible = false
    await load()
  } finally {
    saving.value = false
  }
}

/** 删除前先拉使用情况，把「影响 N 条资料」摆到确认框里（AC-B1-7） */
async function removeDim(row) {
  const usage = await categoryDimUsage(row.id).catch(() => null)
  const used = usage?.data?.usedCount ?? row.usedCount ?? 0
  const msg = used > 0
    ? `维度「${row.dimName}」已被 ${used} 条资料使用，删除后这些资料的该维度标签将被移除，且不可恢复。确定删除？`
    : `删除维度「${row.dimName}」及其全部分类值，不可恢复。确定删除？`
  await ElMessageBox.confirm(msg, '删除确认', { type: 'warning', confirmButtonText: '确认删除' })
  await deleteCategoryDim(row.id, true)
  ElMessage.success('已删除')
  await load()
}

function openAddValue(row) {
  Object.assign(valueDlg, {
    visible: true, dimId: row.id, dimName: row.dimName,
    valueId: null, valueName: '', sortOrder: row.values.length
  })
}

function openEditValue(row, v) {
  Object.assign(valueDlg, {
    visible: true, dimId: row.id, dimName: row.dimName,
    valueId: v.id, valueName: v.valueName, sortOrder: v.sortOrder ?? 0
  })
}

async function saveValue() {
  if (!valueDlg.valueName.trim()) {
    ElMessage.warning('请填写分类值')
    return
  }
  saving.value = true
  try {
    const payload = { valueName: valueDlg.valueName.trim(), sortOrder: valueDlg.sortOrder }
    if (valueDlg.valueId) await updateCategoryValue(valueDlg.dimId, valueDlg.valueId, payload)
    else await addCategoryValue(valueDlg.dimId, payload)
    ElMessage.success('已保存')
    valueDlg.visible = false
    await load()
  } finally {
    saving.value = false
  }
}

/** 分类值只停用不删除：历史标签必须还能查到（AC-B1-4） */
async function toggleValue(dim, v) {
  const next = v.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  if (next === 'DISABLED') {
    await ElMessageBox.confirm(
      `停用「${v.valueName}」后新建分类将不再出现该选项，已打的标签保留。确定停用？`, '停用确认', { type: 'warning' })
  }
  await updateCategoryValue(dim.id, v.id, { status: next })
  ElMessage.success(next === 'ENABLED' ? '已启用' : '已停用')
  await load()
}

onMounted(load)
</script>

<style lang="scss" scoped>
.page-container { padding: 16px; }
.card-head { display: flex; align-items: center; gap: 12px; }
.head-tip { flex: 1; font-size: 12.5px; color: #909399; }
.value-box { padding: 8px 16px 4px; }
.value-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; font-size: 13px; color: #606266; }
.value-foot { margin-top: 6px; font-size: 12px; }
.val-edit { width: 100%; }
.val-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.muted { color: #c0c4cc; font-size: 12px; }
</style>
