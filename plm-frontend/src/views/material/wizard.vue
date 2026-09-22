<template>
  <div class="page-container">
    <OperationGuide module-key="material-wizard" />
    <el-card>
      <template #header>
        <div class="wiz-header">
          <span>建档向导 · 10 分钟建好一个料号</span>
          <el-button link type="primary" @click="$router.push('/material/list')">返回列表</el-button>
        </div>
      </template>

      <el-steps :active="active" finish-status="success" align-center class="wiz-steps">
        <el-step title="选品类" description="物料类型 / 灯具品类" />
        <el-step title="基础信息" description="名称 + 编号预览" />
        <el-step title="参数模板" description="套灯具参数" />
        <el-step title="档案树 + 自检" description="预览 + DQ评分 + 提交" />
      </el-steps>

      <!-- Step 0: 选品类 -->
      <div v-show="active === 0" class="step-body">
        <el-form :model="form" label-width="120px" style="max-width:640px;margin:0 auto">
          <el-form-item label="物料类型" required>
            <el-select v-model="form.materialType" style="width:100%" @change="onTypeChange">
              <el-option v-for="(v, k) in typeMap" :key="k" :label="v" :value="k" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.materialType === 'FINISHED'" label="灯具品类" required>
            <el-select v-model="form.productType" placeholder="选择灯具品类(决定档案树/参数模板)" style="width:100%">
              <el-option v-for="p in productTypeOptions" :key="p.value" :label="p.label" :value="p.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="将套用模板">
            <el-tag v-if="matchedTreeTpl" type="success">{{ matchedTreeTpl }} · 档案树</el-tag>
            <el-tag v-if="matchedParamTpl" type="warning" style="margin-left:6px">{{ matchedParamTpl }} · 参数</el-tag>
            <span v-if="!matchedTreeTpl && !matchedParamTpl" class="dim">请先选择品类</span>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 1: 基础信息 + 编号预览 -->
      <div v-show="active === 1" class="step-body">
        <el-form ref="basicFormRef" :model="form" :rules="basicRules" label-width="120px" style="max-width:640px;margin:0 auto">
          <el-row :gutter="12">
            <el-col :span="14">
              <el-form-item label="物料名称" prop="materialName">
                <el-input v-model="form.materialName" placeholder="如 投光灯 100W" />
              </el-form-item>
            </el-col>
            <el-col :span="10">
              <el-form-item label="英文名称">
                <el-input v-model="form.nameEn" placeholder="外贸用" />
              </el-form-item>
            </el-col>
            <el-col :span="14">
              <el-form-item label="料号预览">
                <el-input v-model="form.partNo" placeholder="留空提交时自动生成" readonly>
                  <template #append>
                    <el-button :loading="previewing" @click="previewCode">预览编号</el-button>
                  </template>
                </el-input>
              </el-form-item>
            </el-col>
            <el-col :span="10">
              <el-form-item label="单位"><el-input v-model="form.unit" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="产品系列"><el-input v-model="form.productSeries" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="归属项目"><el-input v-model="form.projectNo" /></el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="规格"><el-input v-model="form.specification" /></el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>

      <!-- Step 2: 参数模板 -->
      <div v-show="active === 2" class="step-body">
        <el-empty v-if="!paramItems.length" description="该品类暂无参数模板，可直接下一步" :image-size="80" />
        <el-form v-else :model="paramValues" label-width="160px" style="max-width:720px;margin:0 auto">
          <el-form-item v-for="item in paramItems" :key="item.paramKey" :label="item.paramName + (item.unit ? '(' + item.unit + ')' : '')">
            <span v-if="item.required" class="req-dot">*</span>
            <el-select v-if="item.dataType === 'ENUM'" v-model="paramValues[item.paramKey]" clearable style="width:280px">
              <el-option v-for="o in (item.options || [])" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-input-number v-else-if="item.dataType === 'NUMBER'" v-model="paramValues[item.paramKey]" controls-position="right" style="width:280px" />
            <el-input v-else v-model="paramValues[item.paramKey]" style="width:280px" />
            <span class="dim" style="margin-left:8px">{{ item.dqSeverity }}</span>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 3: 档案树预览 + 自检提交 -->
      <div v-show="active === 3" class="step-body">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-card shadow="never">
              <template #header><span>将自动生成的档案树 ({{ archiveNodes.length }} 节点)</span></template>
              <el-tree v-if="archiveTreeData.length" :data="archiveTreeData" :props="{ label: 'name', children: 'children' }" default-expand-all node-key="code">
                <template #default="{ data: n }">
                  <span>{{ n.name }}</span>
                  <el-tag v-if="!n.leaf" size="small" style="margin-left:6px">目录</el-tag>
                </template>
              </el-tree>
              <el-empty v-else description="加载中或无模板" :image-size="60" />
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <template #header><span>建档汇总</span></template>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="物料类型">{{ typeMap[form.materialType] }}</el-descriptions-item>
                <el-descriptions-item label="灯具品类">{{ form.productType || '—' }}</el-descriptions-item>
                <el-descriptions-item label="名称">{{ form.materialName }}</el-descriptions-item>
                <el-descriptions-item label="参数项">{{ Object.keys(paramValues).filter(k => paramValues[k] !== undefined && paramValues[k] !== '').length }} / {{ paramItems.length }} 已填</el-descriptions-item>
                <el-descriptions-item label="档案树模板">{{ matchedTreeTpl || '—' }}</el-descriptions-item>
              </el-descriptions>

              <div v-if="dqResult" class="dq-box">
                <div class="dq-score">
                  质量分 <span :class="scoreClass">{{ dqResult.score }}</span> / 100
                </div>
                <div class="dq-breakdown">
                  <el-tag v-if="dqResult.blockCount" type="danger" size="small">阻断 {{ dqResult.blockCount }}</el-tag>
                  <el-tag v-if="dqResult.warnCount" type="warning" size="small">警告 {{ dqResult.warnCount }}</el-tag>
                  <el-tag v-if="dqResult.infoCount" type="info" size="small">提示 {{ dqResult.infoCount }}</el-tag>
                </div>
                <el-alert v-if="createdPartNo" type="success" :closable="false" show-icon style="margin-top:8px">
                  建档成功！料号 <b>{{ createdPartNo }}</b>，档案树已自动生成。
                  <el-button link type="primary" @click="$router.push('/archive/list')">查看档案</el-button>
                </el-alert>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <!-- 导航 -->
      <div class="wiz-footer">
        <el-button v-if="active > 0 && !createdPartNo" @click="active--">上一步</el-button>
        <el-button v-if="active < 3" type="primary" @click="next">{{ active === 2 && !paramItems.length ? '跳过' : '下一步' }}</el-button>
        <el-button v-if="active === 3 && !createdPartNo" type="success" :loading="submitting" @click="submit">提交建档</el-button>
        <el-button v-if="createdPartNo" type="primary" @click="resetAll">再建一个</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { addMaterial, saveMaterialParams, dqCheck } from '@/api/material'
import { resolveArchiveTree, resolveParamTpl } from '@/api/template'
import { codegenPreview } from '@/api/metric'

const typeMap = { FINISHED: '成品', SEMI: '半成品', PLASTIC: '塑胶件', HARDWARE: '五金件', STANDARD: '标准件' }
const productTypeOptions = [
  { value: 'FL', label: '投光灯' }, { value: 'WL', label: '工作灯' }, { value: 'HB', label: '工矿灯' },
  { value: 'STR', label: '路灯' }, { value: 'TL', label: '隧道灯' }, { value: 'GD', label: '庭院灯' },
  { value: 'PL', label: '平板灯' }, { value: 'DL', label: '筒灯' }, { value: 'CSL', label: '玉米灯' }
]

const active = ref(0)
const previewing = ref(false)
const submitting = ref(false)
const basicFormRef = ref()
const paramItems = ref([])
const paramValues = reactive({})
const archiveNodes = ref([])
const dqResult = ref(null)
const createdPartNo = ref('')
const matchedTreeTpl = ref('')
const matchedParamTpl = ref('')

const form = reactive({
  materialType: 'FINISHED', productType: '', materialName: '', nameEn: '',
  productSeries: '', projectNo: '', specification: '', unit: 'PCS', makeType: 0, remark: '', partNo: ''
})
const basicRules = { materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }] }

// 物料类型 → part_category(与后端 resolveCategory 一致)
const partCategory = computed(() => {
  switch (form.materialType) {
    case 'FINISHED': return 'PRODUCT'
    case 'SEMI': return 'ASSEMBLY'
    case 'STANDARD': return 'STANDARD'
    default: return 'COMPONENT'
  }
})

const archiveTreeData = computed(() => buildTree(archiveNodes.value))

function buildTree(nodes) {
  const byCode = {}
  const roots = []
  nodes.forEach(n => { byCode[n.code] = { ...n, children: [] } })
  nodes.forEach(n => {
    const node = byCode[n.code]
    if (n.parentCode && byCode[n.parentCode]) {
      byCode[n.parentCode].children.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
}

const scoreClass = computed(() => {
  if (!dqResult.value) return ''
  const s = dqResult.value.score
  if (s >= 90) return 'ok'
  if (s >= 70) return 'warn'
  return 'bad'
})

function onTypeChange() {
  if (form.materialType !== 'FINISHED') form.productType = ''
  matchedTreeTpl.value = ''
  matchedParamTpl.value = ''
  if (form.materialType) loadTplPreview()
}

async function loadTplPreview() {
  const params = { partCategory: partCategory.value, productType: form.productType, materialType: form.materialType }
  const [t, p] = await Promise.all([resolveArchiveTree(params), resolveParamTpl(params)])
  matchedTreeTpl.value = t.data?.tplName || ''
  matchedParamTpl.value = p.data?.tplName && p.data.tplName !== '无参数模板' ? p.data.tplName : ''
}

async function previewCode() {
  previewing.value = true
  try {
    const res = await codegenPreview('PART', { materialType: form.materialType, productType: form.productType })
    form.partNo = res.data.code
  } finally { previewing.value = false }
}

async function next() {
  if (active.value === 0) {
    if (!form.materialType) return ElMessage.warning('请选择物料类型')
    if (form.materialType === 'FINISHED' && !form.productType) return ElMessage.warning('成品请选择灯具品类')
    active.value = 1
  } else if (active.value === 1) {
    await basicFormRef.value.validate()
    await loadParamTemplate()
    active.value = 2
  } else if (active.value === 2) {
    await loadArchivePreview()
    active.value = 3
  }
}

async function loadParamTemplate() {
  const res = await resolveParamTpl({ partCategory: partCategory.value, productType: form.productType, materialType: form.materialType })
  paramItems.value = res.data?.items || []
  paramItems.value.forEach(it => { if (!(it.paramKey in paramValues)) paramValues[it.paramKey] = undefined })
}

async function loadArchivePreview() {
  const res = await resolveArchiveTree({ partCategory: partCategory.value, productType: form.productType, materialType: form.materialType })
  archiveNodes.value = res.data?.nodes || []
  matchedTreeTpl.value = res.data?.tplName || matchedTreeTpl.value
}

async function submit() {
  submitting.value = true
  try {
    const payload = { ...form }
    delete payload.partNo // 留空由系统自动生成
    const createRes = await addMaterial(payload)
    const partNo = createRes.data.partNo
    const id = createRes.data.id

    const filled = {}
    Object.keys(paramValues).forEach(k => {
      if (paramValues[k] !== undefined && paramValues[k] !== '' && paramValues[k] !== null) filled[k] = String(paramValues[k])
    })
    if (Object.keys(filled).length) await saveMaterialParams(partNo, filled)

    const dqRes = await dqCheck(id)
    dqResult.value = dqRes.data
    createdPartNo.value = partNo
    ElMessage.success('建档成功：' + partNo)
  } catch (e) {
    ElMessage.error('建档失败：' + (e.message || ''))
  } finally {
    submitting.value = false
  }
}

function resetAll() {
  active.value = 0
  Object.assign(form, { materialType: 'FINISHED', productType: '', materialName: '', nameEn: '', productSeries: '', projectNo: '', specification: '', unit: 'PCS', makeType: 0, remark: '', partNo: '' })
  paramItems.value = []
  Object.keys(paramValues).forEach(k => delete paramValues[k])
  archiveNodes.value = []
  dqResult.value = null
  createdPartNo.value = ''
  matchedTreeTpl.value = ''
  matchedParamTpl.value = ''
}
</script>

<style scoped lang="scss">
.wiz-header { display: flex; justify-content: space-between; align-items: center; }
.wiz-steps { margin-bottom: 24px; }
.step-body { min-height: 320px; padding: 16px 8px; }
.wiz-footer { text-align: center; margin-top: 16px; padding-top: 16px; border-top: 1px solid #f0f0f0; }
.req-dot { color: #f56c6c; margin-right: 4px; }
.dim { color: #c0c4cc; font-size: 12px; }
.dq-box { margin-top: 16px; }
.dq-score { font-size: 16px; margin-bottom: 8px;
  .ok { color: #67c23a; font-size: 28px; font-weight: 700; }
  .warn { color: #e6a23c; font-size: 28px; font-weight: 700; }
  .bad { color: #f56c6c; font-size: 28px; font-weight: 700; }
}
.dq-breakdown { display: flex; gap: 6px; }
</style>
