<template>
  <el-tooltip v-if="userStore.token && userStore.userId" content="反馈" placement="left" :show-after="200">
    <button
      type="button"
      class="feedback-fab"
      aria-label="反馈"
      @click="visible = true"
    >
      <el-icon><ChatDotRound /></el-icon>
      <span class="feedback-fab__text">反馈</span>
    </button>
  </el-tooltip>

  <FeedbackDialog v-model="visible" />
</template>

<script setup>
import { ref } from 'vue'
import { useUserStore } from '@/stores/user'
import FeedbackDialog from './FeedbackDialog.vue'

const userStore = useUserStore()
const visible = ref(false)
</script>

<style lang="scss" scoped>
// 固定视口右下角,距右/下 24px,尺寸 >= 44x44(AC F1/F2)。
// z-index 低于 Element Plus 弹层基线(2000),避免遮挡页面弹窗与确认按钮。
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
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.22);
  transition: background 0.16s, transform 0.16s;
  .el-icon { font-size: 20px; }
  &__text { font-size: 11px; line-height: 1; }
  &:hover { background: #337ecc; transform: translateY(-2px); }
  &:active { transform: translateY(0); }
}
</style>
