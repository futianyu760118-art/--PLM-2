<template>
  <div class="page-container">
    <OperationGuide module-key="okr" />
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>OKR 目标管理</span>
          <div>
            <el-button size="small" @click="syncKpi">同步KPI</el-button>
            <el-select v-model="currentCycleId" placeholder="选择周期" style="width:200px;margin-left:8px" @change="loadTree">
              <el-option v-for="c in cycles" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </div>
        </div>
      </template>

      <div v-if="!currentCycleId" style="text-align:center;padding:40px;color:#999">
        请先选择或创建一个 OKR 周期
      </div>

      <div v-for="node in tree" :key="node.objective.id" style="margin-bottom:20px">
        <el-card shadow="never">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
            <div>
              <el-tag :type="objTag(node.objective.status)" size="small">{{ node.objective.level }}</el-tag>
              <b style="margin-left:8px;font-size:15px">{{ node.objective.title }}</b>
            </div>
            <div style="display:flex;align-items:center;gap:12px">
              <el-progress :percentage="Number(node.objective.progressPct||0)" :width="60" type="dashboard" style="width:60px" />
            </div>
          </div>
          <el-table :data="node.keyResults" border size="small">
            <el-table-column prop="title" label="Key Result" min-width="200" show-overflow-tooltip />
            <el-table-column prop="kpiCode" label="关联KPI" width="160" />
            <el-table-column label="基准→目标" width="140" align="center">
              <template #default="{row}">{{ row.baseline ?? '—' }} → {{ row.target ?? '—' }} {{ row.unit||'' }}</template>
            </el-table-column>
            <el-table-column label="当前值" width="90" align="right">
              <template #default="{row}"><b style="color:#409eff">{{ row.currentValue ?? '—' }}</b></template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{row}"><el-tag :type="krTag(row.status)" size="small">{{ row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{row}">
                <el-button link size="small" type="primary" @click="openCheckin(row)">打卡</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
      <el-empty v-if="currentCycleId && !tree.length" description="暂无目标，请先创建" />
    </el-card>

    <el-dialog v-model="checkinDialog.visible" title="KR 打卡" width="420px">
      <el-form label-width="80px">
        <el-form-item label="KR">{{ checkinDialog.kr?.title }}</el-form-item>
        <el-form-item label="当前值"><el-input v-model="checkinDialog.value" type="number" /></el-form-item>
        <el-form-item label="信心(0-10)"><el-input-number v-model="checkinDialog.confidence" :min="0" :max="10" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="checkinDialog.note" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkinDialog.visible=false">取消</el-button>
        <el-button type="primary" @click="submitCheckin">打卡</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getCycles, createCycle, getTree, checkin as checkinApi, syncFromKpi } from '@/api/okr'

const cycles = ref([])
const currentCycleId = ref(null)
const tree = ref([])
const checkinDialog = reactive({ visible:false, kr:null, value:'', confidence:7, note:'' })

const objTag = (s) => ({ ON_TRACK:'success', AT_RISK:'warning', BEHIND:'danger' }[s] || 'info')
const krTag = (s) => ({ COMPLETED:'success', ON_TRACK:'success', AT_RISK:'warning', BEHIND:'danger' }[s] || 'info')

async function loadCycles() {
  const res = await getCycles()
  cycles.value = res.data || []
  if (cycles.value.length && !currentCycleId.value) {
    const active = cycles.value.find(c => c.status === 'ACTIVE')
    currentCycleId.value = (active || cycles.value[0]).id
    loadTree()
  }
}

async function loadTree() {
  if (!currentCycleId.value) return
  const res = await getTree(currentCycleId.value)
  tree.value = res.data || []
}

function openCheckin(kr) {
  checkinDialog.kr = kr
  checkinDialog.value = kr.currentValue ?? ''
  checkinDialog.confidence = 7
  checkinDialog.note = ''
  checkinDialog.visible = true
}

async function submitCheckin() {
  await checkinApi(checkinDialog.kr.id, { value: Number(checkinDialog.value), confidence: checkinDialog.confidence, note: checkinDialog.note })
  ElMessage.success('打卡成功')
  checkinDialog.visible = false
  loadTree()
}

async function syncKpi() {
  const res = await syncFromKpi()
  ElMessage.success(`已同步 ${res.data?.synced ?? 0} 个KR`)
  loadTree()
}

onMounted(loadCycles)
</script>
