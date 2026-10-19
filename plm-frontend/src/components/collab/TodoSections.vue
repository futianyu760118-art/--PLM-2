<template>
  <div>
    <section class="sec">
      <div class="sec-head od">逾期（{{ data.overdue.length }}）</div>
      <el-table v-if="data.overdue.length" :data="data.overdue" size="small" border stripe>
        <el-table-column v-if="showOwner" prop="owner_name" label="成员" width="90" />
        <el-table-column prop="project_no" label="项目" width="160" />
        <el-table-column prop="node_name" label="节点" width="140" />
        <el-table-column label="计划日期" width="110"><template #default="{ row }">{{ fmt(row.plan_date) }}</template></el-table-column>
        <el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="primary" size="small" @click="$emit('open', row)">处理</el-button></template></el-table-column>
      </el-table>
      <p v-else class="muted">无逾期</p>
    </section>

    <section class="sec">
      <div class="sec-head">待办（{{ data.todo.length }}）</div>
      <el-table v-if="data.todo.length" :data="data.todo" size="small" border stripe>
        <el-table-column v-if="showOwner" prop="owner_name" label="成员" width="90" />
        <el-table-column prop="project_no" label="项目" width="160" />
        <el-table-column prop="node_name" label="节点" width="140" />
        <el-table-column label="计划日期" width="110"><template #default="{ row }">{{ row.plan_date ? fmt(row.plan_date) : '无到期' }}</template></el-table-column>
        <el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="primary" size="small" @click="$emit('open', row)">处理</el-button></template></el-table-column>
      </el-table>
      <p v-else class="muted">无待办</p>
    </section>

    <section class="sec">
      <div class="sec-head">已完成（{{ data.done.length }}）</div>
      <el-table v-if="data.done.length" :data="data.done" size="small" border stripe>
        <el-table-column v-if="showOwner" prop="owner_name" label="成员" width="90" />
        <el-table-column prop="project_no" label="项目" width="160" />
        <el-table-column prop="node_name" label="节点" width="140" />
        <el-table-column label="完成日期" width="110"><template #default="{ row }">{{ row.actual_date ? fmt(row.actual_date) : '—' }}</template></el-table-column>
        <el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="primary" size="small" @click="$emit('open', row)">查看</el-button></template></el-table-column>
      </el-table>
      <p v-else class="muted">无已完成</p>
    </section>
  </div>
</template>

<script setup>
defineProps({ data: { type: Object, required: true }, showOwner: { type: Boolean, default: false } })
defineEmits(['open'])
function fmt(dt) { return dt ? String(dt).slice(0, 10) : '—' }
</script>

<style scoped>
.sec { margin-bottom: 18px; }
.sec-head { font-size: 13px; font-weight: 600; margin-bottom: 6px; }
.sec-head.od { color: #f56c6c; }
.muted { color: #c0c4cc; font-size: 12px; }
</style>
