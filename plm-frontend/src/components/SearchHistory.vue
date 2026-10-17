<template>
  <el-popover :visible="popVisible" placement="bottom-start" width="280" trigger="click">
    <template #reference>
      <el-input v-model="model" :placeholder="placeholder" clearable :prefix-icon="prefixIcon"
        @focus="popVisible = true" @keyup.enter="commit" @clear="commit" />
    </template>
    <div class="hist">
      <div class="hist-head">
        <span>{{ history.length ? '历史输入' : '暂无历史' }}</span>
        <el-button v-if="history.length" link size="small" @click="clear">清空</el-button>
      </div>
      <div v-if="history.length" class="hist-items">
        <div v-for="h in history" :key="h" class="hist-item" @click="pick(h)">{{ h }}</div>
      </div>
      <div v-if="suggestions.length" class="hist-sec">
        <div class="hist-head">相关建议</div>
        <div v-for="s in suggestions" :key="s" class="hist-item" @click="pick(s)">{{ s }}</div>
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useSearchHistory } from '@/composables/useSearchHistory'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '搜索' },
  prefixIcon: { type: String, default: 'Search' },
  historyKey: { type: String, required: true },
  suggestions: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'search'])

const model = ref(props.modelValue)
const popVisible = ref(false)
const { history, record, clear } = useSearchHistory(props.historyKey)

watch(() => props.modelValue, (v) => { model.value = v })

function commit() {
  record(model.value)
  emit('update:modelValue', model.value)
  emit('search')
  popVisible.value = false
}
function pick(v) {
  model.value = v
  record(v)
  emit('update:modelValue', v)
  emit('search')
  popVisible.value = false
}
</script>

<style scoped>
.hist { max-height: 320px; overflow: auto; }
.hist-head { display: flex; justify-content: space-between; align-items: center; font-size: 12px; color: #909399; margin: 2px 0 6px; }
.hist-sec { margin-top: 8px; }
.hist-item { padding: 6px 8px; border-radius: 6px; font-size: 13px; cursor: pointer; }
.hist-item:hover { background: #f0f4ff; color: var(--plm-accent, #409eff); }
</style>
