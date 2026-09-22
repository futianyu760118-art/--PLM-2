<template>
  <div v-if="guide" class="op-guide" :class="{ 'is-open': open }">
    <div class="og-head" @click="toggle">
      <el-icon class="og-icon"><Guide /></el-icon>
      <span class="og-title">操作流程</span>
      <span class="og-module">{{ guide.name }}</span>
      <el-tag size="small" effect="plain" class="og-count">{{ guide.steps.length }} 步</el-tag>
      <span class="og-spacer"></span>
      <span class="og-toggle">
        {{ open ? '收起' : '展开' }}
        <el-icon><ArrowUp v-if="open" /><ArrowDown v-else /></el-icon>
      </span>
    </div>
    <el-collapse-transition>
      <div v-show="open" class="og-body">
        <div class="og-steps">
          <template v-for="(s, i) in guide.steps" :key="i">
            <div class="og-step" :class="{ done: false }">
              <span class="og-num">{{ i + 1 }}</span>
              <span class="og-text">{{ s }}</span>
            </div>
            <el-icon v-if="i < guide.steps.length - 1" class="og-arrow"><Right /></el-icon>
          </template>
        </div>
        <div v-if="guide.tips" class="og-tips">
          <el-icon><InfoFilled /></el-icon><span>{{ guide.tips }}</span>
        </div>
      </div>
    </el-collapse-transition>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { operationGuides } from '@/config/operationGuides'

const props = defineProps({
  moduleKey: { type: String, required: true }
})

const guide = computed(() => operationGuides[props.moduleKey])
const STORE_KEY = 'plm_op_guide_open'
const open = ref(localStorage.getItem(STORE_KEY) !== '0')

function toggle() {
  open.value = !open.value
  localStorage.setItem(STORE_KEY, open.value ? '1' : '0')
}
</script>

<style lang="scss" scoped>
.op-guide {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-left: 3px solid #409eff;
  border-radius: 8px;
  margin-bottom: 14px;
  overflow: hidden;
  transition: box-shadow 0.16s;
  &:hover { box-shadow: 0 2px 10px rgba(0, 21, 41, 0.06); }
}

.og-head {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px;
  cursor: pointer; user-select: none;
  .og-icon { color: #409eff; font-size: 16px; }
  .og-title { font-size: 14px; font-weight: 600; color: #303133; }
  .og-module { font-size: 13px; color: #909399; }
  .og-count { margin-left: 2px; }
  .og-spacer { flex: 1; }
  .og-toggle {
    display: flex; align-items: center; gap: 3px;
    font-size: 12px; color: #909399;
    &:hover { color: #409eff; }
  }
}

.og-body { padding: 4px 14px 12px; }

.og-steps {
  display: flex; flex-wrap: wrap; align-items: center; gap: 8px;
  padding: 8px 0;
  .og-step {
    display: flex; align-items: center; gap: 6px;
    background: #f5f8ff;
    border: 1px solid #d9ecff;
    border-radius: 20px;
    padding: 5px 12px 5px 6px;
    transition: all 0.15s;
    &:hover { background: #ecf5ff; border-color: #a0cfff; transform: translateY(-1px); }
    .og-num {
      width: 18px; height: 18px; flex: 0 0 18px;
      display: flex; align-items: center; justify-content: center;
      background: #409eff; color: #fff;
      border-radius: 50%; font-size: 11px; font-weight: 600;
      font-variant-numeric: tabular-nums;
    }
    .og-text { font-size: 12.5px; color: #4a5568; }
  }
  .og-arrow { color: #c0c4cc; font-size: 13px; flex: 0 0 auto; }
}

.og-tips {
  display: flex; align-items: flex-start; gap: 6px;
  margin-top: 6px; padding: 8px 10px;
  background: #fdf6ec; border-radius: 6px;
  font-size: 12.5px; color: #b88230; line-height: 1.5;
  .el-icon { margin-top: 2px; flex: 0 0 auto; }
}
</style>
