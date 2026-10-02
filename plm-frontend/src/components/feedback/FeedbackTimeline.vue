<template>
  <div class="feedback-timeline">
    <el-empty v-if="!items || items.length === 0" description="暂无流转记录" :image-size="56" />
    <el-timeline v-else>
      <el-timeline-item
        v-for="t in items"
        :key="t.id"
        :timestamp="t.createdAt"
        placement="top"
        :type="tagType(t.toStatus)"
      >
        <div class="tl-head">
          <span class="tl-from">{{ fromLabel(t) }}</span>
          <el-icon class="tl-arrow"><Right /></el-icon>
          <el-tag size="small" :type="tagType(t.toStatus)">{{ statusLabel(t.toStatus) }}</el-tag>
        </div>
        <div class="tl-meta">操作方：{{ t.operatorName || '-' }}</div>
        <div v-if="t.relatedIssueNo" class="tl-meta">立项编号：{{ t.relatedIssueNo }}</div>
        <div v-if="t.note" class="tl-note">{{ t.note }}</div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup>
defineProps({
  /** 流转历史列表, 按时间正序, 元素形如 { id, fromStatus, toStatus, note, operatorName, relatedIssueNo, createdAt } */
  items: { type: Array, default: () => [] }
})

const STATUS_LABELS = {
  PENDING: '待分析', EVALUATING: '评估中', INITIATED: '已立项', RETURNED: '已退回'
}
const STATUS_TAG_TYPES = {
  PENDING: 'info', EVALUATING: 'warning', INITIATED: 'success', RETURNED: 'danger'
}

function statusLabel(status) {
  return STATUS_LABELS[status] || status || '-'
}
function tagType(status) {
  return STATUS_TAG_TYPES[status] || 'primary'
}
// 首行无前序状态, 以「提交」代替, 避免出现 "undefined → 待分析"
function fromLabel(t) {
  return t.fromStatus ? statusLabel(t.fromStatus) : '提交'
}
</script>

<style lang="scss" scoped>
.feedback-timeline { max-height: 320px; overflow-y: auto; padding-right: 4px; }
.tl-head { display: flex; align-items: center; gap: 6px; font-size: 13px; }
.tl-from { color: #909399; }
.tl-arrow { color: #c0c4cc; font-size: 12px; }
.tl-meta { margin-top: 4px; font-size: 12px; color: #909399; }
.tl-note {
  margin-top: 6px;
  padding: 8px 10px;
  font-size: 13px;
  line-height: 1.5;
  color: #606266;
  white-space: pre-wrap;
  word-break: break-word;
  background: #f5f7fa;
  border-radius: 4px;
}
</style>
