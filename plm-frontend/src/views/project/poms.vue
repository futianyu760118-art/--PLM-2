<template>
  <div class="m04-personal">
    <div class="m04-top"><div><small>AEOS / M04 × M10 POMS</small><h2>我的研发微系统</h2><p>PLM 专业流程不变 · 岗位动态装配 · 手机优先</p></div><el-tag type="warning">L0 演示 · 未接真实任务</el-tag></div>
    <el-card shadow="never" class="m04-controls">
      <el-select v-model="role" aria-label="演示岗位" style="width:170px"><el-option v-for="r in roleOptions" :key="r.id" :value="r.id" :label="r.label" /></el-select>
      <el-select v-model="product" aria-label="演示产品" style="width:190px"><el-option label="恒剑 · LED工作灯" value="lighting"/><el-option label="贞观 · 定时器" value="timer"/></el-select>
      <el-tag type="info">权限和任务均为演示数据</el-tag>
    </el-card>
    <div class="m04-overview"><el-card shadow="never"><small>未来待办</small><h2>{{ pending.length }}</h2></el-card><el-card shadow="never"><small>过去提交</small><h2>{{ submissions.length }}</h2></el-card></div>
    <el-tabs v-model="tab">
      <el-tab-pane label="未来待办" name="todo"><el-card v-for="task in pending" :key="task.id" shadow="never" class="m04-item"><div class="m04-task"><div><b>{{ task.title }}</b><p>任务示例 · {{ pack.title }} · 待执行</p></div><el-button type="primary" @click="complete(task)">声明完成</el-button></div></el-card><el-empty v-if="!pending.length" description="演示任务已提交"/></el-tab-pane>
      <el-tab-pane label="过去结果" name="result"><el-alert title="执行者声明完成不等于业务验收通过" type="info" :closable="false" show-icon/><el-card v-for="record in submissions" :key="record.id" shadow="never" class="m04-item"><b>{{ record.title }}</b><el-tag type="warning" style="margin-left:12px">待验证 · 演示</el-tag><p>{{ record.description }}</p></el-card><el-empty v-if="!submissions.length" description="暂无演示提交"/></el-tab-pane>
      <el-tab-pane label="研发记录" name="capture"><el-card shadow="never"><h3>{{ pack.title }} · 专用参数</h3><el-form label-position="top"><el-form-item v-for="f in pack.fields" :key="f.key" :label="f.label"><el-input v-model="values[f.key]" :placeholder="'输入'+f.label"/></el-form-item><el-form-item label="异常与证据说明"><el-input v-model="note" type="textarea" :rows="3"/></el-form-item><el-button type="primary" @click="submitCapture">保存演示记录</el-button></el-form></el-card></el-tab-pane>
      <el-tab-pane label="智能体" name="agents"><el-card shadow="never"><p>个人研发主智能体与专业子智能体，遵循 Hub Runtime / Guard / Evidence 模型。</p><div class="m04-agent-buttons"><el-button v-for="a in agents" :key="a" plain @click="agent=a">{{ a }}</el-button></div><el-alert v-if="agent" :title="'已打开 '+agent+'（本地模拟，尚未绑定 Hub Agent）'" type="warning" :closable="false"/><el-input v-model="question" type="textarea" :rows="3" placeholder="输入任务（不发送到真实 AI）" style="margin-top:12px"/><el-button style="margin-top:8px" @click="answerAgent">模拟咨询</el-button><p v-if="reply">{{ reply }}</p></el-card></el-tab-pane>
      <el-tab-pane label="研发全周期" name="lifecycle"><el-steps direction="vertical" :active="3" finish-status="success" style="padding:12px"><el-step v-for="s in phases" :key="s" :title="s"/></el-steps><el-button @click="goProject">进入 PLM 研发项目</el-button></el-tab-pane>
    </el-tabs>
    <p class="m04-note">嵌入式 L0 Demo。任务、权限、记录、智能体均为前端内存模拟；正式版按 M01 身份/DEH、M04 PLM、M02 EDP、Hub Agent Adapter 接口接入。</p>
  </div>
</template>
<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
const router = useRouter()
const roleOptions = [{id:'engineer',label:'研发工程师'},{id:'leader',label:'研发负责人'},{id:'sample',label:'样品验证员'}]
const role = ref('engineer'), product = ref('lighting'), tab = ref('todo')
const taskTemplates = {engineer:['复核技术规格和产品图纸','检查 BOM 与设计版本','提交样品设计验证'],leader:['审核研发立项目标','确认工程变更风险','审核技术转移证据'],sample:['登记样品测试数据','提交缺陷与复测证据','编制验证报告']}
const packs = {lighting:{title:'恒剑 LED 工作灯',fields:[{key:'power',label:'输入功率(W)'},{key:'cct',label:'色温(K)'},{key:'thermal',label:'散热验证'}]},timer:{title:'贞观定时器',fields:[{key:'accuracy',label:'计时误差(ms)'},{key:'range',label:'定时范围(s)'},{key:'control',label:'输出控制测试'}]}}
const pack = computed(()=>packs[product.value])
const values = reactive({}), note = ref(''), submissions = ref([]), completed = ref([])
const pending = computed(()=>taskTemplates[role.value].map((title,i)=>({id:role.value+':'+product.value+':'+i,title})).filter(t=>!completed.value.includes(t.id)))
watch(product,()=>{for(const k of Object.keys(values)) delete values[k]})
function complete(task){completed.value.push(task.id);submissions.value.unshift({id:Date.now()+Math.random(),title:task.title,description:'已声明完成；等待专业 Result 和 Evidence 核验'});ElMessage.success('演示完成声明已记录，不代表验收通过')}
function submitCapture(){const fields=pack.value.fields.map(f=>f.label+': '+(values[f.key]||'未填')).join('；');submissions.value.unshift({id:Date.now()+Math.random(),title:pack.value.title+' 研发现场记录',description:fields+'；备注 '+note.value});note.value='';ElMessage.info('仅保存至本页面内存，未写入 PLM')}
const agents=['研发主智能体','需求分析 Agent','结构设计 Agent','BOM 校核 Agent','样品验证 Agent','ECN Agent','项目进度 Agent','技术转移 Agent']
const agent=ref(''),question=ref(''),reply=ref('')
function answerAgent(){reply.value=question.value.trim()?'模拟建议：核对项目、图纸、BOM 和验证证据的最新版本，形成可核验任务；尚未调用 Hub Runtime。':'请输入要咨询的问题'}
const phases=['立项与需求','结构设计与图纸','BOM 和物料','样品制造','设计验证','ECO / ECN','试产及技术转移','量产维护','退市归档']
function goProject(){router.push('/project/list')}
</script>
<style scoped>
.m04-personal{padding:16px;max-width:1150px;margin:auto;color:#25364b}.m04-top,.m04-task{display:flex;justify-content:space-between;gap:16px;align-items:center}.m04-top h2{margin:8px 0}.m04-top p,.m04-note,.m04-task p{color:#7a8899}.m04-controls{margin:12px 0}.m04-controls :deep(.el-card__body){display:flex;flex-wrap:wrap;gap:12px;align-items:center}.m04-overview{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-bottom:14px}.m04-overview h2{font-size:30px;color:#236abc;margin:6px 0}.m04-item{margin:10px 0}.m04-agent-buttons{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:14px}.m04-agent-buttons :deep(.el-button){margin-left:0}.m04-note{font-size:12px}@media(max-width:650px){.m04-personal{padding:8px}.m04-top,.m04-task{align-items:flex-start}.m04-top h2{font-size:20px}.m04-task{flex-direction:column}.m04-controls :deep(.el-select){width:100%!important}}
</style>