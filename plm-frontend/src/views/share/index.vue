<template>
  <div class="page-container">
    <OperationGuide module-key="share" />
    <el-card>
      <div class="table-toolbar">
        <el-button type="primary" icon="Share" @click="openDialog">创建分享链接</el-button>
      </div>
      <el-alert type="info" :closable="false" style="margin-bottom:12px"
        title="外网分享链接仅支持脱敏3D外观预览, 自动剥离模具与内部结构, 无图纸下载入口, 支持自定义有效期" />
      <el-table :data="shares" border stripe>
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column prop="partNo" label="料号" width="150" />
        <el-table-column label="分享链接" min-width="280">
          <template #default="{row}">
            <el-link type="primary" :href="`/share/${row.shareToken}`" target="_blank">{{ shareBaseUrl }}/{{ row.shareToken }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="viewCount" label="访问次数" width="100" align="center" />
        <el-table-column prop="expireAt" label="过期时间" width="160" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{row}">
            <el-tag :type="row.status===1 ? (new Date(row.expireAt) > new Date() ? 'success' : 'danger') : 'info'">
              {{ row.status===1 ? (new Date(row.expireAt) > new Date() ? '有效' : '已过期') : '已作废' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="viewLogs(row)">访问日志</el-button>
            <el-button link type="danger" size="small" v-if="row.status===1" @click="handleVoid(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog.visible" title="创建外网分享链接" width="480px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="3D模型ID"><el-input v-model="form.model3dId" placeholder="可选" /></el-form-item>
        <el-form-item label="有效期">
          <el-radio-group v-model="form.days">
            <el-radio :value="1">1天</el-radio>
            <el-radio :value="7">7天</el-radio>
            <el-radio :value="30">30天</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false" style="margin-bottom:12px"
        title="外网仅显示脱敏外观GLB, 自动剥离模具/内部腔体结构, 关闭所有下载通道" />
      <template #footer>
        <el-button @click="dialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">创建</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="logsDrawer.visible" title="访问日志" size="50%">
      <el-table :data="logsDrawer.logs" border>
        <el-table-column prop="visitorIp" label="IP" width="140" />
        <el-table-column prop="userAgent" label="设备" min-width="240" show-overflow-tooltip />
        <el-table-column prop="accessedAt" label="访问时间" width="180" />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createShare, listShares, voidShare, getShareLogs } from '@/api/share'

const shares = ref([])
const submitting = ref(false)
const dialog = reactive({ visible:false })
const logsDrawer = reactive({ visible:false, logs:[] })
const form = reactive({ title:'产品3D外观预览', model3dId:'', days:7, remark:'' })
const shareBaseUrl = computed(() => window.location.origin + '/share')

async function loadData() {
  const res = await listShares()
  shares.value = res.data
}

function openDialog() {
  Object.assign(form, { title:'产品3D外观预览', model3dId:'', days:7, remark:'' })
  dialog.visible = true
}

async function submit() {
  submitting.value = true
  try {
    const res = await createShare(form)
    ElMessage.success('分享链接已创建')
    await navigator.clipboard.writeText(`${shareBaseUrl.value}/${res.data.shareToken}`).catch(()=>{})
    ElMessage.info('链接已复制到剪贴板')
    dialog.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function handleVoid(row) {
  await ElMessageBox.confirm('作废该分享链接?', '确认', { type:'warning' })
  await voidShare(row.id)
  ElMessage.success('已作废')
  loadData()
}

async function viewLogs(row) {
  const res = await getShareLogs(row.id)
  logsDrawer.logs = res.data
  logsDrawer.visible = true
}

onMounted(loadData)
</script>
