<template>
  <div class="poms-page">
    <div class="poms-bar">
      <span class="poms-title">06 微系统 · M10 POMS</span>
      <span class="poms-tip">与 PLM 数据实时同步 · 采集录入 / 我的待办 / 22 节点明细 / ECN / 工序 / AI</span>
      <span class="poms-tabs">
        <button v-for="t in tabs" :key="t.key" :class="{ on: tab === t.key }" @click="tab = t.key">{{ t.label }}</button>
      </span>
      <a class="poms-open" :href="frameUrl" target="_blank" rel="noopener">新窗口 ↗</a>
    </div>
    <iframe :src="frameUrl" class="poms-frame" frameborder="0" allow="camera; microphone; fullscreen" referrerpolicy="no-referrer" title="M10 POMS 微系统" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const tabs = [
  { key: 'plm2', label: '研发工作台', path: '/plm2' },
  { key: 'hlc', label: '人事行政', path: '/hlc' },
  { key: 'org', label: '岗位装配', path: '/org' },
  { key: 'portal', label: '门户', path: '/' }
]
const tab = ref('plm2')
const host = window.location.hostname || '127.0.0.1'
const frameUrl = computed(() => {
  const t = tabs.find(x => x.key === tab.value) || tabs[0]
  return `http://${host}:4173${t.path}`
})
</script>

<style scoped>
.poms-page { display: flex; flex-direction: column; height: calc(100vh - 84px); background: #f4f6fa; }
.poms-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; padding: 8px 14px; background: #10233b; color: #dbe7f5; font-size: 12px; }
.poms-title { font-weight: 700; color: #fff; }
.poms-tip { opacity: .72; }
.poms-tabs { display: flex; gap: 4px; }
.poms-tabs button { border: 1px solid #3b5a7a; background: transparent; color: #a8c4e0; padding: 4px 10px; border-radius: 6px; font-size: 11px; cursor: pointer; }
.poms-tabs button.on { background: #1767c9; border-color: #1767c9; color: #fff; }
.poms-open { margin-left: auto; color: #7fb2ff; text-decoration: none; }
.poms-frame { flex: 1; width: 100%; border: 0; background: #fff; }
</style>
