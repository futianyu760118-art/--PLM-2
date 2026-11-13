<template>
  <div class="page-container">
    <OperationGuide module-key="project-progress" />
    <el-card>
      <el-form :inline="true">
        <el-form-item label="选择项目">
          <el-select v-model="projectId" filterable placeholder="选择研发项目" style="width:340px" @change="load">
            <el-option v-for="p in projects" :key="p.id"
              :label="`${p.projectNo || ''} ${p.projectName}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="节点查找">
          <el-input v-model="nodeKeyword" placeholder="节点名/责任人" clearable style="width:180px" prefix-icon="Search" />
        </el-form-item>
        <el-form-item>
          <el-button icon="Refresh" :disabled="!projectId" @click="load">刷新</el-button>
          <el-button type="primary" plain icon="CircleCheck" :disabled="!projectId" @click="doCheck">进度自检</el-button>
          <el-button icon="Camera" :disabled="!projectId" @click="screenshotPage">截图</el-button>
        </el-form-item>
      </el-form>

      <div v-if="summary" class="sum-bar">
        <el-tag type="success" :effect="summaryFilter === 'DONE' ? 'dark' : 'light'" style="cursor:pointer" @click="toggleSummary('DONE')">完成 {{ summary.done }}/{{ summary.total }}</el-tag>
        <el-tag type="primary" :effect="summaryFilter === 'KEY' ? 'dark' : 'light'" style="cursor:pointer" @click="toggleSummary('KEY')">关键节点 {{ summary.keyDone }}/{{ summary.keyTotal }}</el-tag>
        <el-tag type="warning" :effect="summaryFilter === 'OVERDUE' ? 'dark' : 'light'" style="cursor:pointer" @click="toggleSummary('OVERDUE')">逾期 {{ summary.overdue }}</el-tag>
        <el-tag type="info" :effect="summaryFilter === 'NOT_SET' ? 'dark' : 'light'" style="cursor:pointer" @click="toggleSummary('NOT_SET')">未设置 {{ summary.notSet }}</el-tag>
        <el-tag v-if="summary.noNode" type="info" :effect="summaryFilter === 'NO_NODE' ? 'dark' : 'plain'" style="cursor:pointer" @click="toggleSummary('NO_NODE')">无节点 {{ summary.noNode }}</el-tag>
        <el-tag v-if="summaryFilter" type="danger" effect="plain" style="cursor:pointer" @click="summaryFilter = ''">筛选中：{{ summaryFilterLabel }} ✕</el-tag>
        <span class="rate">节点完成率 <b>{{ Math.round((summary.nodeRate || 0) * 100) }}%</b></span>
      </div>

      <el-table v-if="nodes.length" :data="filteredNodes" row-key="id" border ref="tableRef"
        highlight-current-row :row-class-name="rowClassName" :default-sort="{ prop: 'seq', order: 'ascending' }"
        v-loading="loading" @expand-change="onExpand">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div v-if="row.status === 'NO_NODE'" class="std-tip" style="padding:10px 14px;color:#909399">
              无节点：本环节不适用，无需填报进度、上传资料或提交审批。
            </div>
            <div v-else class="node-form">
              <el-form :inline="true" :model="row" label-width="90px">
                <el-form-item label="状态">
                  <el-select v-model="row.status" style="width:140px" @change="onStatusChange(row)">
                    <el-option v-for="s in statusList" :key="s.v" :label="s.l" :value="s.v" />
                  </el-select>
                </el-form-item>
                <el-form-item label="计划开始">
                  <el-date-picker v-model="row.planStartDate" type="date" value-format="YYYY-MM-DD" style="width:140px" />
                </el-form-item>
                <el-form-item label="计划结束">
                  <el-date-picker v-model="row.planEndDate" type="date" value-format="YYYY-MM-DD" style="width:140px" />
                </el-form-item>
                <el-form-item label="实际日期">
                  <el-date-picker v-model="row.actualDate" type="date" value-format="YYYY-MM-DD" style="width:150px" />
                </el-form-item>
                <el-form-item label="责任人"><el-input v-model="row.owner" style="width:120px" /></el-form-item>
                <el-form-item label="交付物"><el-input v-model="row.deliveryDesc" style="width:180px" /></el-form-item>
                <el-form-item label="备注"><el-input v-model="row.remark" style="width:200px" /></el-form-item>
                <el-form-item>
                  <el-button type="success" size="small" :loading="savingId === row.id" @click="confirmDone(row)">提交完成审批</el-button>
                  <el-button type="primary" size="small" :loading="savingId === row.id" @click="saveNode(row)">保存节点</el-button>
                </el-form-item>
              </el-form>

              <!-- 子项节点（如模具重开记录）-->
              <el-divider content-position="left">
                子项 ({{ childNodes(row.nodeCode).length }})
                <el-button size="small" type="warning" plain icon="Plus" style="margin-left:8px" @click="openChildForm(row)">追加子项</el-button>
              </el-divider>
              <el-table v-if="childNodes(row.nodeCode).length" :data="childNodes(row.nodeCode)" size="small" border>
                <el-table-column prop="nodeCode" label="编码" width="120" />
                <el-table-column prop="nodeName" label="子项名称" min-width="160" />
                <el-table-column label="状态" width="90" align="center">
                  <template #default="{ row: c }"><el-tag size="small" :type="statusTag(c.status)">{{ statusLabel(c.status) }}</el-tag></template>
                </el-table-column>
                <el-table-column label="计划开始" width="110">
                  <template #default="{ row: c }">{{ c.planStartDate || c.planDate || '—' }}</template>
                </el-table-column>
                <el-table-column label="计划完成" width="110">
                  <template #default="{ row: c }">{{ c.planEndDate || c.planDate || '—' }}</template>
                </el-table-column>
                <el-table-column prop="actualDate" label="实际日期" width="110" />
                <el-table-column prop="owner" label="责任人" width="90" />
                <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
                <el-table-column label="操作" width="80" align="center" fixed="right">
                  <template #default="{ row: c }">
                    <el-button size="small" link type="primary" icon="EditPen" @click="openEditChild(c)">编辑</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <p v-else class="std-tip" style="margin-top:4px">无子项（如遇模具重开等，点「追加子项」记录）</p>

              <el-divider content-position="left">
                资料文件 ({{ row.evidenceCount || (evidenceMap[row.id] || []).length }})
                <el-upload style="display:inline-block;margin-left:8px"
                  accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.png,.jpg,.jpeg,.gif,.bmp,.webp,.zip,.rar,.7z,.dwg,.dxf,.stp,.step"
                  :show-file-list="false" :http-request="opt => doUpload(row, opt)" :disabled="uploadingId === row.id">
                  <el-button size="small" type="success" plain icon="Upload" :loading="uploadingId === row.id">上传资料</el-button>
                </el-upload>
                <el-button size="small" type="primary" plain icon="EditPen" style="margin-left:6px"
                  @click="openTextEvidence(row)">填写资料</el-button>
                <el-tag v-if="completionApprovalTag(row.completionApprovalStatus)" size="small"
                  :type="completionApprovalTag(row.completionApprovalStatus).type" style="margin-left:8px"
                  :title="row.completionRejectNote || ''">{{ completionApprovalTag(row.completionApprovalStatus).text }}</el-tag>
                <el-button v-if="canSubmitCompletion(row)" size="small" type="warning" plain icon="Stamp"
                  style="margin-left:6px" @click="submitCompletion(row)">提交完成审批</el-button>
                <template v-if="row.completionApprovalStatus === 'PENDING_APPROVAL' && isManager">
                  <el-button size="small" type="success" plain style="margin-left:6px" @click="approveCompletion(row)">通过</el-button>
                  <el-button size="small" type="danger" plain @click="rejectCompletion(row)">驳回</el-button>
                </template>
              </el-divider>
              <el-table :data="evidenceMap[row.id] || []" size="small" border empty-text="暂无资料文件">
                <el-table-column label="资料物" min-width="200" show-overflow-tooltip>
                  <template #default="{ row: ev }">
                    <el-link type="primary" @click="previewEvidence(ev)">{{ ev.fileName }}</el-link>
                  </template>
                </el-table-column>
                <el-table-column label="来源" width="80" align="center">
                  <template #default="{ row: ev }">
                    <el-tag size="small" :type="ev.source === 'TEXT' ? 'warning' : 'primary'">
                      {{ ev.source === 'TEXT' ? '填写' : '文件' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="docType" label="类型" width="120" />
                <el-table-column prop="note" label="说明" width="140" show-overflow-tooltip />
                <el-table-column prop="uploadedBy" label="上传人" width="90" />
                <el-table-column prop="uploadedAt" label="时间" width="165" />
                <el-table-column label="操作" width="200">
                  <template #default="{ row: ev }">
                    <el-button link type="primary" size="small" @click="previewEvidence(ev)">打开</el-button>
                    <el-button link type="warning" size="small" @click="openEditEvidence(ev)">编辑</el-button>
                    <el-button v-if="ev.fileId" link type="info" size="small" @click="downloadEvidence(ev.fileId, ev.fileName)">下载</el-button>
                    <el-button link type="danger" size="small" @click="removeEvidence(row, ev)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div class="std-tip">完成标准: 状态=已完成 且 填写实际完成日期 且 资料文件≥1</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="seq" label="#" width="50" align="center" sortable />
        <el-table-column label="节点" width="150" sortable prop="nodeName" :sort-method="sortNodeName">
          <template #default="{ row }">
            <el-tag v-if="row.isKey" type="danger" size="small" effect="dark" style="margin-right:4px">★</el-tag>
            {{ row.nodeName }}
            <el-tag v-if="row.status === 'NO_NODE'" size="small" type="info" effect="plain" style="margin-left:4px">无节点</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130" align="center" sortable :sort-method="sortStatus" prop="status">
          <template #default="{ row }">
            <el-select :model-value="row.status" size="small" style="width:110px" @change="v => { row.status = v; onStatusChange(row); saveNode(row) }">
              <el-option v-for="s in statusList" :key="s.v" :label="s.l" :value="s.v" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="计划时间(开始~结束)" width="270" sortable prop="planStartDate">
          <template #default="{ row }">
            <div style="display:flex;flex-direction:column;gap:2px">
              <el-date-picker v-if="row.status !== 'NO_NODE'" type="daterange" :model-value="planRange(row)" size="small" value-format="YYYY-MM-DD"
                range-separator="~" start-placeholder="开始" end-placeholder="结束"
                style="width:235px" :clearable="true"
                @update:model-value="v => { row.planStartDate = v?.[0] || ''; row.planEndDate = v?.[1] || ''; row.planDate = row.planStartDate; saveNode(row) }" />
              <span v-else style="color:#909399">—</span>
              <div v-if="planApprovalTag(row.planApprovalStatus)" style="display:flex;align-items:center;gap:4px">
                <el-tooltip v-if="row.planApprovalStatus === 'REJECTED' && row.planRejectNote" :content="row.planRejectNote" placement="top">
                  <el-tag size="small" :type="planApprovalTag(row.planApprovalStatus).type">{{ planApprovalTag(row.planApprovalStatus).text }}</el-tag>
                </el-tooltip>
                <el-tag v-else size="small" :type="planApprovalTag(row.planApprovalStatus).type">{{ planApprovalTag(row.planApprovalStatus).text }}</el-tag>
              </div>
              <div style="display:flex;gap:4px">
                <el-button v-if="(row.planStartDate || row.planDate) && row.planApprovalStatus !== 'PENDING_APPROVAL' && row.planApprovalStatus !== 'APPROVED'"
                  size="small" link type="primary" @click="submitPlan(row)">提交审批</el-button>
                <template v-if="row.planApprovalStatus === 'PENDING_APPROVAL' && isManager">
                  <el-button size="small" link type="success" @click="approvePlan(row)">通过</el-button>
                  <el-button size="small" link type="danger" @click="rejectPlan(row)">驳回</el-button>
                </template>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="实际日期" width="150" sortable prop="actualDate">
          <template #default="{ row }">
            <el-date-picker :model-value="row.actualDate" type="date" size="small" value-format="YYYY-MM-DD"
              style="width:135px" :clearable="true" @update:model-value="v => { row.actualDate = v; saveNode(row) }" />
          </template>
        </el-table-column>
        <el-table-column label="责任人" width="130" sortable prop="owner">
          <template #default="{ row }">
            <el-input :model-value="row.owner" size="small" style="width:110px"
              @change="v => { row.owner = v; saveNode(row) }" />
          </template>
        </el-table-column>
        <el-table-column prop="deliveryDesc" label="交付物" min-width="160" show-overflow-tooltip />
        <el-table-column label="资料" width="80" align="center">
          <template #default="{ row }">
            <el-badge :value="row.evidenceCount || 0" :type="row.evidenceCount ? 'success' : 'info'" />
          </template>
        </el-table-column>
        <el-table-column label="工作表" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="nodeSheet(row.nodeCode)" link type="primary" size="small" @click="openSheet(row)">填工作表</el-button>
            <el-tooltip v-else :content="unmappedTip(row.nodeCode)" placement="top">
              <el-button link type="info" size="small" disabled>无工作表</el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="请选择项目查看进度节点" :image-size="100" />
    </el-card>

    <!-- 自检 -->
    <el-dialog v-model="checkVisible" title="项目进度自检" width="760px">
      <template v-if="check">
        <div class="check-head">
          <el-progress type="dashboard" :percentage="check.score" :width="120" :color="scoreColor(check.score)">
            <template #default="{ percentage }">
              <div class="score-num">{{ percentage }}</div>
              <div class="score-lbl">健康分</div>
            </template>
          </el-progress>
          <div class="metrics">
            <p>节点完成率 NCR: <b>{{ pct(check.metrics?.ncr) }}</b></p>
            <p>关键节点完成率 KCR: <b>{{ pct(check.metrics?.kcr) }}</b></p>
            <p>计划准时率 OTR: <b>{{ pct(check.metrics?.otr) }}</b></p>
            <p>数据完整率 CR: <b>{{ pct(check.metrics?.cr) }}</b></p>
            <p>
              问题: <el-tag type="danger" size="small">HIGH {{ check.bySeverity?.HIGH || 0 }}</el-tag>
              <el-tag type="warning" size="small" style="margin-left:6px">MEDIUM {{ check.bySeverity?.MEDIUM || 0 }}</el-tag>
            </p>
          </div>
        </div>
        <el-divider content-position="left">问题清单</el-divider>
        <el-table :data="check.issues" size="small" border max-height="320">
          <el-table-column prop="nodeName" label="节点" width="110" />
          <el-table-column label="关键" width="60" align="center">
            <template #default="{ row }"><span v-if="row.isKey">★</span></template>
          </el-table-column>
          <el-table-column label="级别" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.severity === 'HIGH' ? 'danger' : 'warning'">{{ row.severity }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="问题" />
        </el-table>
      </template>
    </el-dialog>

    <!-- 资料预览/打开 -->
    <el-dialog v-model="preview.visible" :title="'资料 · ' + (preview.name || '')" width="840px" top="5vh">
      <div v-if="preview.loading" style="text-align:center;padding:40px;color:#909399">加载中...</div>
      <template v-else>
        <pre v-if="preview.kind === 'text'" class="preview-text">{{ preview.text }}</pre>
        <img v-else-if="preview.kind === 'image'" :src="preview.url" class="preview-img" />
        <iframe v-else-if="preview.kind === 'pdf'" :src="preview.url" class="preview-frame"></iframe>
        <div v-else-if="preview.kind === 'html'" class="preview-html" v-html="preview.html"></div>
        <div v-else-if="preview.kind === 'docx'" ref="docxBox" class="preview-docx"></div>
        <el-empty v-else description="该类型暂不支持在线预览, 请下载查看" :image-size="90" />
      </template>
      <template #footer>
        <el-button v-if="preview.fileId" type="primary" @click="downloadEvidence(preview.fileId, preview.name)">下载</el-button>
        <el-button @click="preview.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 追加/编辑子项节点 -->
    <el-dialog v-model="childForm.visible" :title="(childForm.editMode ? '编辑子项 · ' : '追加子项 · ') + childForm.parentName" width="560px">
      <el-form :model="childForm" label-width="90px">
        <el-form-item label="子项名称" required>
          <el-input v-model="childForm.nodeName" placeholder="如: 模具重开 / 追加工序 / 二次T1" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="childForm.status" style="width:160px">
            <el-option v-for="s in statusList" :key="s.v" :label="s.l" :value="s.v" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划开始">
          <el-date-picker v-model="childForm.planDate" type="date" value-format="YYYY-MM-DD" style="width:180px" />
        </el-form-item>
        <el-form-item label="计划完成">
          <el-date-picker v-model="childForm.planEndDate" type="date" value-format="YYYY-MM-DD" style="width:180px" />
        </el-form-item>
        <el-form-item label="实际日期">
          <el-date-picker v-model="childForm.actualDate" type="date" value-format="YYYY-MM-DD" style="width:180px" />
        </el-form-item>
        <el-form-item label="责任人"><el-input v-model="childForm.owner" style="width:160px" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="childForm.remark" type="textarea" :rows="2" placeholder="如: 重开原因/维修内容" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="childForm.visible = false">取消</el-button>
        <el-button type="primary" :loading="childForm.busy" @click="submitChild">{{ childForm.editMode ? '保存' : '追加' }}</el-button>
      </template>
    </el-dialog>

    <!-- 填写/编辑资料 -->
    <el-dialog v-model="evForm.visible" :title="evForm.id ? '编辑资料' : '填写资料(系统内)'" width="640px">
      <el-form :model="evForm" label-width="70px">
        <el-form-item label="类型"><el-input v-model="evForm.docType" placeholder="如 PLAN / BOM / 测试报告" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="evForm.note" placeholder="简要说明" /></el-form-item>
        <el-form-item label="内容">
          <el-input v-model="evForm.content" type="textarea" :rows="9"
            placeholder="在系统内填写资料内容, 如: 检查记录 / 结论 / 测试数据 / 会议纪要" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="evForm.visible = false">取消</el-button>
        <el-button type="primary" :loading="evForm.saving" @click="saveEvidenceForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch, nextTick, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as XLSX from 'xlsx'
import { renderAsync } from 'docx-preview'
import { pageProject } from '@/api/project'
import request from '@/utils/request'
import { nodeSheet, UNMAPPED_NODES } from '@/config/nodeSheetLinks'

const route = useRoute()
const router = useRouter()
import { getNodeMatrix, updateNode, listEvidence, uploadEvidence, deleteEvidence, progressCheck, downloadEvidence, createTextEvidence, updateEvidence, fetchPreview, fetchTextEvidence, submitNodePlan, approveNodePlan, rejectNodePlan, submitNodeCompletion, approveNodeCompletion, rejectNodeCompletion } from '@/api/progress'
import { useUserStore } from '@/stores/user'

const projects = ref([])
const projectId = ref(null)
const nodes = ref([])
const summary = ref(null)
const loading = ref(false)
const savingId = ref(null)
const uploadingId = ref(null)
const evidenceMap = ref({})
const tableRef = ref(null)
const focusNodeCode = computed(() => route.query.node ? String(route.query.node) : '')
const userStore = useUserStore()
const MANAGER_ROLES = ['ADMIN', 'admin', 'rd_manager', 'sales_manager', 'sales_director', 'project_manager', 'gm', 'hr_manager', 'cw1']
const isManager = computed(() => (userStore.roles || []).some(r => MANAGER_ROLES.includes(r)))

// ===== 表格：查找 + 排序 + 统计穿透 =====
const nodeKeyword = ref('')
const summaryFilter = ref('')
const summaryFilterLabel = computed(() => ({ DONE: '已完成', KEY: '关键节点', OVERDUE: '逾期', NOT_SET: '未设置', NO_NODE: '无节点' }[summaryFilter.value] || ''))
function toggleSummary(f) { summaryFilter.value = summaryFilter.value === f ? '' : f }
function isOverdueNode(n) {
  return n.planDate && n.status !== 'DONE' && n.status !== 'NO_NODE' && String(n.planDate).slice(0, 10) < todayStr()
}
const filteredNodes = computed(() => {
  let list = nodes.value
  const f = summaryFilter.value
  if (f === 'DONE') list = list.filter(n => n.status === 'DONE')
  else if (f === 'NOT_SET') list = list.filter(n => n.status === 'NOT_SET')
  else if (f === 'NO_NODE') list = list.filter(n => n.status === 'NO_NODE')
  else if (f === 'KEY') list = list.filter(n => n.isKey)
  else if (f === 'OVERDUE') list = list.filter(n => isOverdueNode(n))
  const kw = nodeKeyword.value.trim().toLowerCase()
  if (!kw) return list
  return list.filter(n =>
    (n.nodeName || '').toLowerCase().includes(kw) ||
    (n.owner || '').toLowerCase().includes(kw) ||
    (n.nodeCode || '').toLowerCase().includes(kw))
})
const STATUS_ORDER = ['NO_NODE', 'NOT_SET', 'PLANNED', 'PENDING', 'IN_PROGRESS', 'FAILED', 'DONE']
function sortStatus(a, b) {
  return STATUS_ORDER.indexOf(a.status) - STATUS_ORDER.indexOf(b.status)
}
function sortNodeName(a, b) { return (a.nodeName || '').localeCompare(b.nodeName || '', 'zh') }

const checkVisible = ref(false)
const check = ref({})

const preview = reactive({ visible: false, loading: false, kind: '', url: '', text: '', html: '', name: '', fileId: null })
const docxBox = ref(null)
const evForm = reactive({ visible: false, saving: false, id: null, nodeId: null, node: null, docType: '', note: '', content: '' })

const statusList = [
  { v: 'NO_NODE', l: '无节点' }, { v: 'NOT_SET', l: '未设置' },
  { v: 'PLANNED', l: '已计划' }, { v: 'IN_PROGRESS', l: '进行中' },
  { v: 'DONE', l: '已完成' }, { v: 'FAILED', l: '未完成' }, { v: 'PENDING', l: '待办' }
]
const statusLabel = (s) => statusList.find(x => x.v === s)?.l || s
const statusTag = (s) => ({ DONE: 'success', IN_PROGRESS: 'warning', FAILED: 'danger', PENDING: 'warning', PLANNED: 'primary', NO_NODE: 'info' }[s] || 'info')

// 各节点应交付资料/表单（交付物默认值）
const NODE_DELIVERABLES = {
  PLAN: '《立项书》',
  BOM: '《基础BOM表》',
  SPEC: '《规格书》',
  CONFIG: '《配置表》',
  MOLD_DRAWING: '《开修模评审表》、2D图纸',
  MOLD_REVIEW: '《开修模评审表》',
  HAND_SAMPLE: '《样机评审表》',
  APPEARANCE: '《外观评审表》',
  STRUCTURE: '《结构评审表》',
  ELECTRONICS: '《电子评审表》《成本BOM》',
  MOLD: '《问题清单》',
  MOLD_SAMPLE: '《样品评审表》《模具验收书》',
  PACKAGING: '《包装平面图纸》',
  ELEC_TRIAL: '《试产报告》《预测单》《基础BOM表》',
  RD_TRIAL: '《试产报告》《基础BOM表》',
  ENG_TRIAL: '《SOP》《客户BOM》《工程试产报告》',
  PROD_TRIAL: '《生产试产报告》',
  TEST_REPORT: '《新产品测试报告》',
  TECH_TRANSFER: '《技转评审报告》《新产品测试报告》《BOM表》',
  SHIPMENT: '《出货/验货记录》',
  REVIEW: '《复盘报告》',
  OTHER: '其他'
}
function deliverableOf(code) { return NODE_DELIVERABLES[code] || '' }
function currentProject() { return projects.value.find(p => p.id === projectId.value) }
function todayStr() { return new Date().toISOString().slice(0, 10) }

// ===== 截图（浏览器原生屏幕捕获，保存当前页面为 PNG） =====
async function screenshotPage() {
  const md = navigator.mediaDevices
  if (!md || !md.getDisplayMedia) { ElMessage.warning('当前浏览器不支持截图，请用系统截图工具'); return }
  let stream
  try {
    stream = await md.getDisplayMedia({ video: { displaySurface: 'browser' }, audio: false })
    const video = document.createElement('video')
    video.srcObject = stream
    video.muted = true
    await video.play()
    await new Promise(r => setTimeout(r, 300))
    const canvas = document.createElement('canvas')
    canvas.width = video.videoWidth
    canvas.height = video.videoHeight
    canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height)
    const a = document.createElement('a')
    a.href = canvas.toDataURL('image/png')
    a.download = `PLM进度明细_${todayStr()}.png`
    a.click()
    ElMessage.success('截图已保存')
  } catch (e) {
    /* 用户取消或浏览器限制 */
  } finally {
    if (stream) stream.getTracks().forEach(t => t.stop())
  }
}
const pct = (v) => Math.round((v || 0) * 100) + '%'
const scoreColor = (s) => s >= 85 ? '#67c23a' : s >= 70 ? '#e6a23c' : '#f56c6c'

function unmappedTip(code) { return UNMAPPED_NODES[code] || '该节点暂无可填写工作表' }

function openSheet(row) {
  const link = nodeSheet(row.nodeCode)
  if (!link) { ElMessage.info(unmappedTip(row.nodeCode)); return }
  const project = projects.value.find(p => p.id === projectId.value)
  router.push({
    path: '/project/worksheets',
    query: {
      type: link.sheet,
      projectNo: project?.projectNo || '',
      preset: link.preset ? encodeURIComponent(JSON.stringify(link.preset)) : undefined,
      new: '1'
    }
  })
}

function syncFromRoute() {
  const wanted = route.query.project ? Number(route.query.project) : null
  const found = wanted ? projects.value.find(p => p.id === wanted) : null
  const no = route.query.projectNo ? String(route.query.projectNo) : ''
  const foundByNo = no ? projects.value.find(p => p.projectNo === no) : null
  if (found) projectId.value = found.id
  else if (foundByNo) projectId.value = foundByNo.id
  else if (!projectId.value && projects.value.length) projectId.value = projects.value[0].id
}

function rowClassName({ row }) {
  const cls = []
  if (row.nodeCode === focusNodeCode.value) cls.push('focus-node')
  if (row.status === 'NO_NODE') cls.push('row-no-node')
  return cls.join(' ')
}

async function focusTarget() {
  if (!focusNodeCode.value) return
  await nextTick()
  const row = nodes.value.find(n => n.nodeCode === focusNodeCode.value)
  if (row && tableRef.value) {
    tableRef.value.setCurrentRow(row)
    tableRef.value.toggleRowExpansion(row, true)
  }
  await nextTick()
  const el = document.querySelector('.el-table__row.focus-node')
  if (el) el.scrollIntoView({ block: 'center', behavior: 'smooth' })
}

async function loadProjects() {
  const res = await pageProject({ pageNum: 1, pageSize: 1000 })
  projects.value = res.data.records || []
  syncFromRoute()
  if (projectId.value) { await load(); await focusTarget() }
}

async function load() {
  if (!projectId.value) return
  loading.value = true
  try {
    const res = await getNodeMatrix(projectId.value)
    const projOwner = currentProject()?.owner || ''
    nodes.value = (res.data.nodes || []).map(n => {
      if (!n.owner) n.owner = projOwner
      if (!n.deliveryDesc) n.deliveryDesc = deliverableOf(n.nodeCode)
      n.__origStatus = n.status
      return n
    })
    summary.value = res.data.summary || null
    evidenceMap.value = {}
  } finally { loading.value = false }
}

async function onExpand(row, expandedRows) {
  const isOpen = expandedRows.some(r => r.id === row.id)
  if (isOpen && !evidenceMap.value[row.id]) {
    await loadEvidences(row)
  }
}

async function loadEvidences(row) {
  const res = await listEvidence(row.id)
  evidenceMap.value = { ...evidenceMap.value, [row.id]: res.data || [] }
}

function planRange(row) {
  const s = row.planStartDate || row.planDate || ''
  const e = row.planEndDate || row.planDate || ''
  return (s || e) ? [s || e, e || s] : []
}

async function saveNode(row) {
  const planStart = row.planStartDate || row.planDate || ''
  const planEnd = row.planEndDate || row.planDate || ''
  if (row.status === 'PENDING' && !planStart) {
    ElMessage.warning('待办必须填写计划日期')
    return
  }
  // 禁止直接置完成：必须走“提交完成审批”
  if (row.status === 'DONE' && row.__origStatus !== 'DONE' && row.completionApprovalStatus !== 'APPROVED') {
    ElMessage.warning('请点击「提交完成审批」，由经理审批通过后自动完成')
    await load()
    return
  }
  if (!row.owner) row.owner = currentProject()?.owner || ''
  if (row.status === 'DONE' && !row.actualDate) row.actualDate = todayStr()
  savingId.value = row.id
  try {
    await updateNode(projectId.value, row.nodeCode, {
      status: row.status, planDate: planStart || null,
      planStartDate: planStart || null, planEndDate: planEnd || null,
      actualDate: row.actualDate,
      clearPlan: !planStart && !planEnd, clearActual: !row.actualDate,
      owner: row.owner, deliveryDesc: row.deliveryDesc, remark: row.remark
    })
    ElMessage.success('节点已保存')
    await load()
  } finally { savingId.value = null }
}

// ===== 计划审批流：工程师提交，经理审批 =====
const planApprovalTag = (s) => ({
  PENDING_APPROVAL: { type: 'warning', text: '待审批' },
  APPROVED: { type: 'success', text: '已审批' },
  REJECTED: { type: 'danger', text: '已驳回' }
}[s] || null)

async function submitPlan(row) {
  if (!row.planDate && !row.planStartDate) { ElMessage.warning('请先填写计划日期'); return }
  await saveNode(row)
  try {
    await submitNodePlan(projectId.value, row.nodeCode)
    ElMessage.success('已提交经理审批')
    await load()
  } catch (e) { /* 错误已由 request 弹出 */ }
}

async function approvePlan(row) {
  try {
    await approveNodePlan(projectId.value, row.nodeCode)
    ElMessage.success('审批通过')
    await load()
  } catch (e) { /* ignore */ }
}

async function rejectPlan(row) {
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回计划', {
      inputPlaceholder: '如：计划日期不合理', confirmButtonText: '驳回', cancelButtonText: '取消'
    })
    await rejectNodePlan(projectId.value, row.nodeCode, value)
    ElMessage.success('已驳回')
    await load()
  } catch (e) { /* 取消或失败 */ }
}

// ===== 完成审批流：上传证据后工程师提交，经理审批通过才完成 =====
const completionApprovalTag = (s) => ({
  PENDING_APPROVAL: { type: 'warning', text: '完成待审批' },
  APPROVED: { type: 'success', text: '完成已通过' },
  REJECTED: { type: 'danger', text: '完成已驳回' }
}[s] || null)
function canSubmitCompletion(row) {
  return (row.evidenceCount || 0) >= 1
    && row.status !== 'DONE' && row.status !== 'NO_NODE'
    && row.completionApprovalStatus !== 'PENDING_APPROVAL'
    && row.completionApprovalStatus !== 'APPROVED'
}
async function submitCompletion(row) {
  try {
    await submitNodeCompletion(projectId.value, row.nodeCode)
    ElMessage.success('已提交完成审批')
    await load()
  } catch (e) { /* 错误已由 request 弹出 */ }
}
async function approveCompletion(row) {
  try {
    await approveNodeCompletion(projectId.value, row.nodeCode)
    ElMessage.success('完成审批通过')
    await load()
  } catch (e) { /* ignore */ }
}
async function rejectCompletion(row) {
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回完成审批', {
      inputPlaceholder: '如：证据不完整', confirmButtonText: '驳回', cancelButtonText: '取消'
    })
    await rejectNodeCompletion(projectId.value, row.nodeCode, value)
    ElMessage.success('已驳回')
    await load()
  } catch (e) { /* 取消或失败 */ }
}

// ===== 追加/编辑子项节点（如模具重开记录）=====
const childForm = reactive({ visible: false, busy: false, editMode: false, nodeCode: '', parentCode: '', parentName: '', nodeName: '', status: 'NOT_SET', planDate: '', planEndDate: '', actualDate: '', owner: '', remark: '' })
function openChildForm(row) {
  Object.assign(childForm, { visible: true, busy: false, editMode: false, nodeCode: '', parentCode: row.nodeCode, parentName: row.nodeName, nodeName: '', status: 'NOT_SET', planDate: '', planEndDate: '', actualDate: '', owner: row.owner || currentProject()?.owner || '', remark: '' })
}
function openEditChild(c) {
  const parent = nodes.value.find(n => n.nodeCode === c.parentCode)
  Object.assign(childForm, {
    visible: true, busy: false, editMode: true, nodeCode: c.nodeCode,
    parentCode: c.parentCode || '', parentName: parent?.nodeName || c.parentCode || '',
    nodeName: c.nodeName || '', status: c.status || 'NOT_SET',
    planDate: c.planStartDate || c.planDate || '', planEndDate: c.planEndDate || c.planDate || '',
    actualDate: c.actualDate || '',
    owner: c.owner || '', remark: c.remark || ''
  })
}
async function submitChild() {
  if (!childForm.nodeName.trim()) { ElMessage.warning('请填写子项名称(如: 模具重开)'); return }
  if (childForm.planDate && childForm.planEndDate && childForm.planDate > childForm.planEndDate) {
    ElMessage.warning('计划开始不能晚于计划完成'); return
  }
  childForm.busy = true
  try {
    if (childForm.editMode) {
      if (childForm.status === 'DONE' && !childForm.actualDate) childForm.actualDate = todayStr()
      await updateNode(projectId.value, childForm.nodeCode, {
        nodeName: childForm.nodeName, status: childForm.status,
        planDate: childForm.planDate || null, planStartDate: childForm.planDate || null,
        planEndDate: childForm.planEndDate || childForm.planDate || null,
        actualDate: childForm.actualDate || null,
        clearPlan: !childForm.planDate && !childForm.planEndDate, clearActual: !childForm.actualDate,
        owner: childForm.owner, remark: childForm.remark
      })
      ElMessage.success('子项已更新')
    } else {
      await request({ url: `/project/${projectId.value}/nodes/${childForm.parentCode}/child`, method: 'post', data: {
        nodeName: childForm.nodeName, status: childForm.status,
        planDate: childForm.planDate || null, planStartDate: childForm.planDate || null,
        planEndDate: childForm.planEndDate || childForm.planDate || null, owner: childForm.owner, remark: childForm.remark
      } })
      ElMessage.success('子项已追加')
    }
    childForm.visible = false
    await load()
  } finally { childForm.busy = false }
}
const childNodes = (parentCode) => nodes.value.filter(n => n.parentCode === parentCode)

async function confirmDone(row) {
  // 不再直接置完成：走“提交完成审批”，由经理审批通过后自动完成
  return submitCompletion(row)
}

function onStatusChange(row) {
  if (row.status === 'DONE' && !row.actualDate) row.actualDate = todayStr()
}

async function doUpload(row, opt) {
  uploadingId.value = row.id
  try {
    await uploadEvidence(row.id, opt.file, 'NODE_EVIDENCE', '')
    opt.onSuccess && opt.onSuccess()
    ElMessage.success('资料已上传')
    await load()
    await loadEvidences(row)
  } catch (e) {
    opt.onError && opt.onError(e)
  } finally { uploadingId.value = null }
}

async function removeEvidence(row, ev) {
  await ElMessageBox.confirm(`删除资料 [${ev.fileName}]?`, '警告', { type: 'warning' })
  await deleteEvidence(ev.id)
  ElMessage.success('已删除')
  await loadEvidences(row)
  await load()
}

async function previewEvidence(ev) {
  preview.visible = true
  preview.loading = true
  preview.name = ev.fileName
  preview.fileId = ev.fileId
  preview.kind = ''
  preview.url = ''
  preview.text = ''
  preview.html = ''
  try {
    if (ev.source === 'TEXT' || !ev.fileId) {
      const d = await fetchTextEvidence(ev.id)
      preview.kind = 'text'
      preview.text = d.content || '(空)'
    } else {
      const r = await fetchPreview(ev.fileId)
      const ext = (ev.fileName || '').toLowerCase().split('.').pop() || ''
      if (r.type.startsWith('image/')) { preview.kind = 'image'; preview.url = r.url }
      else if (r.type.includes('pdf')) { preview.kind = 'pdf'; preview.url = r.url }
      else if (r.type.startsWith('text/')) { preview.kind = 'text'; preview.text = r.text }
      else if (['xlsx', 'xls', 'csv'].includes(ext)) {
        const wb = XLSX.read(await r.blob.arrayBuffer(), { type: 'array' })
        preview.html = wb.SheetNames.map(n =>
          `<div class="sheet-title">${escapeHtml(n)}</div>` +
          XLSX.utils.sheet_to_html(wb.Sheets[n], { header: '', footer: '' })
        ).join('')
        preview.kind = 'html'
      } else if (ext === 'docx') {
        preview.kind = 'docx'
        await nextTick()
        if (docxBox.value) {
          docxBox.value.innerHTML = ''
          await renderAsync(r.blob, docxBox.value)
        }
      } else {
        // 不支持预览的类型: 直接下载, 交由本机程序打开
        preview.visible = false
        await downloadEvidence(ev.fileId, ev.fileName)
      }
    }
  } catch (e) {
    preview.visible = false
    ElMessage.error('预览失败，请改用「下载」查看')
  } finally { preview.loading = false }
}

function escapeHtml(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function openTextEvidence(row) {
  Object.assign(evForm, { visible: true, saving: false, id: null, nodeId: row.id, node: row, docType: row.nodeCode, note: '', content: '' })
}

function openEditEvidence(ev) {
  Object.assign(evForm, { visible: true, saving: false, id: ev.id, nodeId: ev.nodeId, node: null,
    docType: ev.docType, note: ev.note, content: ev.content || '' })
}

async function saveEvidenceForm() {
  if (!evForm.content || !evForm.content.trim()) { ElMessage.warning('请填写资料内容'); return }
  evForm.saving = true
  try {
    if (evForm.id) {
      await updateEvidence(evForm.id, { docType: evForm.docType, note: evForm.note, content: evForm.content })
      ElMessage.success('资料已更新')
    } else {
      await createTextEvidence(evForm.nodeId, { docType: evForm.docType, note: evForm.note, content: evForm.content })
      ElMessage.success('资料已填写')
    }
    evForm.visible = false
    await load()
    if (evForm.node) await loadEvidences(evForm.node)
    else {
      const row = nodes.value.find(n => n.id === evForm.nodeId)
      if (row) await loadEvidences(row)
    }
  } finally { evForm.saving = false }
}

async function doCheck() {
  const res = await progressCheck(projectId.value)
  check.value = res.data
  checkVisible.value = true
}

watch(() => [route.query.project, route.query.projectNo, route.query.node], async () => {
  if (!projects.value.length) { await loadProjects(); return }
  syncFromRoute()
  if (projectId.value) { await load(); await focusTarget() }
})

onMounted(loadProjects)
</script>

<style scoped lang="scss">
.sum-bar { display: flex; align-items: center; gap: 10px; margin: 10px 0;
  .rate { margin-left: auto; font-size: 13px; color: #606266; b { color: #409eff; } }
}
.node-form { padding: 6px 12px 12px; background: #fafafa; border-radius: 6px;
  .std-tip { margin-top: 8px; font-size: 12px; color: #e6a23c; }
}
.check-head { display: flex; align-items: center; gap: 30px;
  .score-num { font-size: 22px; font-weight: 700; }
  .score-lbl { font-size: 12px; color: #909399; }
  .metrics p { margin: 6px 0; font-size: 13px; color: #606266; b { color: #409eff; } }
}
.preview-text { max-height: 60vh; overflow: auto; background: #f7f8fa; border: 1px solid #eee;
  border-radius: 6px; padding: 14px; white-space: pre-wrap; font-size: 13px; line-height: 1.7; margin: 0; }
.preview-img { max-width: 100%; max-height: 65vh; display: block; margin: 0 auto; }
.preview-frame { width: 100%; height: 65vh; border: 1px solid #eee; border-radius: 6px; }
.preview-html { max-height: 65vh; overflow: auto; border: 1px solid #eee; border-radius: 6px; padding: 8px; background: #fff; }
.preview-html :deep(.sheet-title) { font-weight: 600; margin: 8px 0 4px; }
.preview-html :deep(table) { border-collapse: collapse; width: 100%; font-size: 12px; }
.preview-html :deep(td), .preview-html :deep(th) { border: 1px solid #e0e0e0; padding: 3px 6px; }
.preview-docx { max-height: 65vh; overflow: auto; border: 1px solid #eee; border-radius: 6px; padding: 16px; background: #fff; }
:deep(.el-table__row.focus-node) { background: #fff7e6 !important; }
:deep(.el-table__row.focus-node:hover > td) { background: #fff1d6 !important; }
:deep(.el-table__row.row-no-node > td) { background: #f4f4f5 !important; color: #b1b3b8; }
:deep(.el-table__row.row-no-node > td .cell) { font-style: italic; }
</style>
