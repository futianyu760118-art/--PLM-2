<template>
  <div class="page-container">
    <el-row :gutter="16">
      <el-col :span="9">
        <el-card>
          <template #header><span>录入知识（ima 导出资料）</span></template>
          <el-form label-width="70px">
            <el-form-item label="标题"><el-input v-model="form.title" placeholder="知识/文档标题" /></el-form-item>
            <el-form-item label="正文">
              <el-input v-model="form.content" type="textarea" :rows="12" placeholder="粘贴 ima 导出的正文内容" />
            </el-form-item>
            <el-form-item label="来源"><el-input v-model="form.source" placeholder="默认 ima" /></el-form-item>
            <el-button type="primary" :loading="saving" @click="save">保存知识</el-button>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="15">
        <el-card>
          <template #header><span>知识库问答</span></template>
          <div class="qa-input">
            <el-input v-model="question" placeholder="用大白话提问，比如：我们的开模评审标准是什么？" @keydown.enter.exact="ask" />
            <el-button type="primary" :loading="asking" @click="ask">提问</el-button>
          </div>
          <div v-if="answer" class="qa-answer">
            <div class="qa-label">回答</div>
            <pre class="qa-text">{{ answer }}</pre>
            <div v-if="refs.length" class="qa-refs">引用：<el-tag v-for="r in refs" :key="r.id" size="small" style="margin-left:6px">{{ r.title }}</el-tag></div>
          </div>

          <el-divider content-position="left">知识列表</el-divider>
          <el-input v-model="keyword" placeholder="搜索知识" clearable style="width:240px;margin-bottom:10px" @input="load" />
          <el-table :data="items" size="small" border stripe>
            <el-table-column prop="id" label="#" width="60" />
            <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
            <el-table-column prop="source" label="来源" width="90" />
            <el-table-column prop="created_by" label="录入人" width="90" />
            <el-table-column label="操作" width="70"><template #default="{ row }"><el-button link type="danger" size="small" @click="remove(row)">删除</el-button></template></el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listKnowledge, createKnowledge, askKnowledge, deleteKnowledge } from '@/api/knowledge'

const form = reactive({ title: '', content: '', source: 'ima' })
const saving = ref(false)
const items = ref([])
const keyword = ref('')
const question = ref('')
const answer = ref('')
const refs = ref([])
const asking = ref(false)

async function load() {
  const r = await listKnowledge(keyword.value || undefined)
  items.value = r.data?.items || []
}
async function save() {
  if (!form.title || !form.content) { ElMessage.warning('请填写标题和正文'); return }
  saving.value = true
  try {
    await createKnowledge({ ...form })
    ElMessage.success('已保存')
    Object.assign(form, { title: '', content: '', source: 'ima' })
    load()
  } finally { saving.value = false }
}
async function ask() {
  if (!question.value.trim()) return
  asking.value = true
  try {
    const r = await askKnowledge(question.value)
    answer.value = r.data?.answer || ''
    refs.value = r.data?.refs || []
  } finally { asking.value = false }
}
async function remove(row) {
  await ElMessageBox.confirm(`删除知识 #${row.id} ${row.title}？`, '删除', { type: 'warning' })
  await deleteKnowledge(row.id)
  ElMessage.success('已删除')
  load()
}
onMounted(load)
</script>

<style scoped lang="scss">
.qa-input { display: flex; gap: 8px; margin-bottom: 12px; }
.qa-answer { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; margin-bottom: 8px;
  .qa-label { font-size: 12px; color: #909399; margin-bottom: 6px; }
  .qa-text { margin: 0; white-space: pre-wrap; font-family: inherit; font-size: 13px; color: #334155; }
  .qa-refs { margin-top: 8px; font-size: 12px; color: #909399; } }
</style>
