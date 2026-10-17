<template>
  <div class="page-container">
    <el-row :gutter="16">
      <el-col :xs="24" :lg="14">
        <el-card>
          <h3 class="title">使用说明</h3>
          <el-collapse>
            <el-collapse-item v-for="g in GUIDES" :key="g.t" :title="g.t">
              <ul class="guide-list">
                <li v-for="item in g.items" :key="item">{{ item }}</li>
              </ul>
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="10">
        <el-card>
          <h3 class="title">学习教材（阅读进度保存在本机）</h3>
          <div v-for="c in COURSES" :key="c.id" class="course" :class="{ read: readMap[c.id] }">
            <label>
              <input v-model="readMap[c.id]" type="checkbox" @change="persist" />
              <span class="cname">{{ c.name }}</span>
            </label>
            <p class="cdesc">{{ c.desc }}</p>
          </div>
          <div class="progress-row">
            <el-progress :percentage="progress" />
            <el-button size="small" @click="resetRead">重置阅读进度</el-button>
          </div>
          <p class="note">阅读进度仅用于个人学习记录，不影响业务数据与外观设置。</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { reactive, computed } from 'vue'

const GUIDES = [
  { t: '五种模式怎么用', items: [
    '顶部模式条可切换：原版业务 / 产品工作空间 / 专业视图集 / 项目协同 / Multica参考。',
    '原版业务保持原有分组与入口，随时可回退；其他模式只是重新分组导航，不改变权限与数据。',
    '切换模式会记住所选项目与筛选（适用时）；模式偏好保存在本机。' ] },
  { t: '外观与个性化', items: [
    '右上角「外观」可设置强调色、页面底色与密度；设置互相独立，刷新后保持。',
    '侧栏配色随模式自动适配；恢复默认不会清除学习进度。' ] },
  { t: '业务与生命周期', items: [
    '全生命周期视图（12环节）用于业务导航，不改变 G0–G8 阶段门判定。',
    '「待接入」标签表示规划入口，尚无真实数据源，系统不会虚构进度。' ] },
  { t: '智能体与帮助', items: [
    '右上角「PLM智能体」可对话查询项目/物料等数据；结论仅供参考，关键决策需人工确认。',
    '遇到无权限或接口失败时会有明确提示，不会静默当作成功。' ] }
]

const COURSES = [
  { id: 'c1', name: '第1课 系统总览与登录', desc: '模块地图、账号与首页' },
  { id: 'c2', name: '第2课 研发项目与立项', desc: '立项申请→批准转项目→明细表' },
  { id: 'c3', name: '第3课 跟踪表与节点完成', desc: '22节点、计划/逾期颜色、证据标准' },
  { id: 'c4', name: '第4课 物料/BOM/变更', desc: '建档向导、BOM结构、ECN流程' },
  { id: 'c5', name: '第5课 工作表与档案', desc: '9类工作表、档案与证据' },
  { id: 'c6', name: '第6课 智能体与协作', desc: '智能体对话、待办协同、反馈' }
]

const LS_KEY = 'plm_reading_v1'
const readMap = reactive({})
try {
  const saved = JSON.parse(localStorage.getItem(LS_KEY) || '{}')
  COURSES.forEach((c) => { readMap[c.id] = !!saved[c.id] })
} catch { /* ignore */ }

const progress = computed(() => Math.round((COURSES.filter((c) => readMap[c.id]).length / COURSES.length) * 100))

function persist() {
  localStorage.setItem(LS_KEY, JSON.stringify({ ...readMap }))
}
function resetRead() {
  COURSES.forEach((c) => { readMap[c.id] = false })
  persist()
}
</script>

<style scoped lang="scss">
.title { margin: 0 0 10px; font-size: 15px; }
.guide-list { margin: 0; padding-left: 18px; font-size: 13px; color: #606266; line-height: 1.9; }
.course { border-bottom: 1px dashed #ebeef5; padding: 8px 0;
  label { display: flex; align-items: center; gap: 8px; cursor: pointer; font-size: 14px; }
  .cname { font-weight: 500; }
  .cdesc { margin: 2px 0 0 24px; font-size: 12px; color: #909399; }
  &.read .cname { color: #67c23a; } }
.progress-row { display: flex; align-items: center; gap: 12px; margin-top: 12px; }
.note { margin: 10px 0 0; font-size: 12px; color: #909399; }
</style>
