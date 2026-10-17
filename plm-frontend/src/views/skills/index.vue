<template>
  <div class="page-container">
    <el-card>
      <div class="head">
        <h3>技能库（做过一次 → 下次调用）</h3>
        <el-input v-model="query.keyword" placeholder="搜索技能名称/标签" clearable style="width:220px" @keyup.enter="search" @clear="search" />
        <el-button type="primary" icon="Search" @click="search">搜索</el-button>
        <el-button icon="Plus" @click="openCreate">新建技能</el-button>
        <el-button type="primary" plain icon="MagicStick" :loading="generating" @click="genVisible = true">智能体生成</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="name" label="技能" min-width="180">
          <template #default="{ row }">
            <el-link type="primary" @click="openDetail(row)">{{ row.name }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="标签" width="180">
          <template #default="{ row }">
            <el-tag v-for="t in splitTags(row.tags)" :key="t" size="small" style="margin:1px">{{ t }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="90" align="center">
          <template #default="{ row }"><el-tag size="small" :type="row.source === 'AGENT' ? 'success' : 'info'">{{ row.source === 'AGENT' ? '智能体' : '手工' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="usageCount" label="调用" width="70" align="center" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">调用</el-button>
            <el-button link type="warning" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" @size-change="load" @current-change="load" />
      </div>
    </el-card>

    <!-- 详情/调用 -->
    <el-drawer v-model="detail.visible" :title="detail.data?.name" size="520px">
      <template v-if="detail.data">
        <el-tag v-if="detail.data.source === 'AGENT'" type="success" size="small">智能体生成</el-tag>
        <p class="d-desc">{{ detail.data.description }}</p>
        <h4>经验/步骤</h4>
        <pre class="d-content">{{ detail.data.content || '（无）' }}</pre>
        <div class="d-tags">
          <el-tag v-for="t in splitTags(detail.data.tags)" :key="t" size="small" type="info">{{ t }}</el-tag>
        </div>
        <p class="d-meta">已调用 {{ detail.data.usageCount }} 次 · 创建人 {{ detail.data.createdBy }}</p>
      </template>
    </el-drawer>

    <!-- 新建/编辑 -->
    <el-dialog v-model="form.visible" :title="form.id ? '编辑技能' : '新建技能'" width="560px">
      <el-form label-width="70px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="经验"><el-input v-model="form.content" type="textarea" :rows="6" placeholder="分步骤的可复用经验/流程" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="逗号分隔，如：BOM 检查, 变更" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="form.visible = false">取消</el-button>
        <el-button type="primary" :loading="form.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 智能体生成 -->
    <el-dialog v-model="genVisible" title="智能体自动制作技能" width="560px">
      <p class="tip">用大白话描述你刚完成的任务，智能体会总结成一条可复用技能。</p>
      <el-input v-model="genDesc" type="textarea" :rows="4" placeholder="例如：处理物料料号重复的检查，先按名称模糊匹配，再人工确认，最后更新规范" />
      <template #footer>
        <el-button @click="genVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="doGenerate">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageSkills, getSkill, createSkill, updateSkill, deleteSkill, agentGenerateSkill } from '@/api/skill'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })
const detail = reactive({ visible: false, data: null })
const form = reactive({ visible: false, saving: false, id: null, name: '', description: '', content: '', tags: '' })
const genVisible = ref(false)
const genDesc = ref('')
const generating = ref(false)

const splitTags = (t) => (t || '').split(/[,，]/).map((x) => x.trim()).filter(Boolean).slice(0, 6)

async function load() {
  loading.value = true
  try {
    const res = await pageSkills(query)
    rows.value = res.data.records || []
    total.value = res.data.total || 0
  } finally { loading.value = false }
}
function search() { query.pageNum = 1; load() }

async function openDetail(row) {
  const res = await getSkill(row.id)
  detail.data = res.data
  detail.visible = true
  row.usageCount = res.data.usageCount
}
function openCreate() {
  Object.assign(form, { visible: true, id: null, name: '', description: '', content: '', tags: '' })
}
function openEdit(row) {
  Object.assign(form, { visible: true, id: row.id, name: row.name, description: row.description, content: row.content, tags: row.tags })
}
async function save() {
  if (!form.name) { ElMessage.warning('请填技能名称'); return }
  form.saving = true
  try {
    if (form.id) await updateSkill({ id: form.id, name: form.name, description: form.description, content: form.content, tags: form.tags })
    else await createSkill({ name: form.name, description: form.description, content: form.content, tags: form.tags })
    ElMessage.success('已保存')
    form.visible = false
    load()
  } finally { form.saving = false }
}
async function remove(row) {
  await ElMessageBox.confirm(`删除技能 [${row.name}]?`, '提示', { type: 'warning' })
  await deleteSkill(row.id)
  ElMessage.success('已删除')
  load()
}
async function doGenerate() {
  if (!genDesc.trim()) { ElMessage.warning('请描述任务'); return }
  generating.value = true
  try {
    const res = await agentGenerateSkill(genDesc)
    ElMessage.success('已生成技能：' + res.data.name)
    genVisible.value = false
    genDesc.value = ''
    load()
  } catch (e) { /* 拦截器已提示 */ } finally { generating.value = false }
}
onMounted(load)
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.head h3 { margin: 0; margin-right: auto; font-size: 15px; }
.d-desc { color: #606266; font-size: 13px; margin: 8px 0; }
.d-content { background: #f7f8fa; border: 1px solid #ebeef5; border-radius: 6px; padding: 12px; white-space: pre-wrap; font-size: 13px; line-height: 1.8; max-height: 50vh; overflow: auto; }
.d-tags { margin: 10px 0; }
.d-meta { font-size: 12px; color: #909399; }
.tip { color: #909399; font-size: 13px; margin: 0 0 10px; }
</style>
