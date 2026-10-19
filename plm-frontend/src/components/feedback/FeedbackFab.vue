<template>
  <template v-if="userStore.token && userStore.userId">
    <button v-if="minimized" type="button" class="feedback-mini" aria-label="展开反馈" @click="restore">
      <el-icon><ChatDotRound /></el-icon>
      <span>反馈</span>
    </button>
    <button v-else type="button" class="feedback-fab" :style="fabStyle" aria-label="反馈"
      @pointerdown="onPointerDown" @click="openIfNotDragged">
      <el-icon><ChatDotRound /></el-icon>
      <span class="feedback-fab__text">反馈</span>
    </button>
  </template>

  <FeedbackDialog v-model="visible" @minimize="onMinimize" />
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useUserStore } from '@/stores/user'
import FeedbackDialog from './FeedbackDialog.vue'

const userStore = useUserStore()
const visible = ref(false)
const minimized = ref(false)

const pos = reactive({ x: null, y: null })
const drag = reactive({ startX: 0, startY: 0, origLeft: 0, origTop: 0, moved: false })

const fabStyle = computed(() => (pos.x != null && pos.y != null
  ? { left: pos.x + 'px', top: pos.y + 'px', right: 'auto', bottom: 'auto' }
  : {}))

function onPointerDown(e) {
  drag.moved = false
  drag.startX = e.clientX
  drag.startY = e.clientY
  const rect = e.currentTarget.getBoundingClientRect()
  drag.origLeft = rect.left
  drag.origTop = rect.top
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp, { once: true })
}

function onPointerMove(e) {
  const dx = e.clientX - drag.startX
  const dy = e.clientY - drag.startY
  if (!drag.moved && (Math.abs(dx) > 4 || Math.abs(dy) > 4)) drag.moved = true
  if (!drag.moved) return
  const size = 56
  pos.x = Math.max(0, Math.min(window.innerWidth - size, drag.origLeft + dx))
  pos.y = Math.max(0, Math.min(window.innerHeight - size, drag.origTop + dy))
}

function onPointerUp() {
  window.removeEventListener('pointermove', onPointerMove)
}

function openIfNotDragged() {
  if (drag.moved) return
  visible.value = true
}

function onMinimize() { visible.value = false; minimized.value = true }
function restore() { minimized.value = false; visible.value = true }
</script>

<style lang="scss" scoped>
// 悬浮球：默认右下角，可拖拽到任意位置；尺寸 >= 44x44(AC F1/F2)。
// z-index 低于 Element Plus 弹层基线(2000)，抽屉为非模态，可同时操作页面其它内容。
.feedback-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1500;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: 56px;
  height: 56px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  cursor: grab;
  touch-action: none;
  user-select: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.22);
  transition: background 0.16s, transform 0.16s;
  .el-icon { font-size: 20px; }
  &__text { font-size: 11px; line-height: 1; }
  &:hover { background: #337ecc; }
  &:active { cursor: grabbing; }
}

// 收起后的最小化胶囊
.feedback-mini {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1500;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 14px;
  border: none;
  border-radius: 20px;
  background: #409eff;
  color: #fff;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.22);
  transition: background 0.16s;
  .el-icon { font-size: 18px; }
  span { font-size: 13px; }
  &:hover { background: #337ecc; }
}
</style>
