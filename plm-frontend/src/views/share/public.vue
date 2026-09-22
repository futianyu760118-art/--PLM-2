<template>
  <div class="share-public">
    <div class="share-header">
      <img src="@/assets/logo.svg" alt="logo" />
      <h2>{{ data?.title || '产品3D外观预览' }}</h2>
      <p>恒剑光电 · 产品外观在线预览 · 到期时间 {{ data?.expireAt }}</p>
    </div>
    <div class="share-viewer" v-loading="loading">
      <div v-if="error" class="share-error">
        <el-icon :size="48"><WarningFilled /></el-icon>
        <h3>{{ error }}</h3>
        <p>该链接可能已过期或被作废,请联系分享人重新生成</p>
      </div>
      <div v-else-if="data" style="height:100%">
        <Model3DViewer v-if="data.modelUrl" :modelUrl="data.modelUrl" :autoRotate="true" />
        <el-empty v-else description="无可用3D模型" />
      </div>
    </div>
    <div class="share-footer">
      <el-tag type="info">料号: {{ data?.partNo || '-' }}</el-tag>
      <el-tag type="warning">仅外观预览 · 无图纸 · 无尺寸 · 无结构</el-tag>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import Model3DViewer from '@/components/three/Model3DViewer.vue'
import { accessShare } from '@/api/share'

const route = useRoute()
const loading = ref(true)
const data = ref(null)
const error = ref('')

onMounted(async () => {
  try {
    const res = await accessShare(route.params.token)
    data.value = res.data
  } catch (e) {
    error.value = e.message || '链接不可用'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.share-public { min-height: 100vh; background: linear-gradient(135deg,#1e3c72,#2a5298); display:flex; flex-direction:column; }
.share-header { text-align:center; color:#fff; padding:24px; }
.share-header img { width:48px; }
.share-header h2 { margin:12px 0 4px; }
.share-header p { font-size:13px; opacity:0.8; }
.share-viewer { flex:1; margin:0 24px; border-radius:8px; overflow:hidden; min-height:60vh; }
.share-error { display:flex; flex-direction:column; align-items:center; justify-content:center; height:100%; color:#fff; }
.share-footer { display:flex; justify-content:center; gap:12px; padding:16px; }
</style>
