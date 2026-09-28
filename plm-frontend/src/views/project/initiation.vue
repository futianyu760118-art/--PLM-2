<template>
  <div class="page-container">
    <OperationGuide module-key="project-initiation" />
    <DataIOBar module="initiation" name="立项申请书" />
    <el-card>
      <el-form :inline="true" :model="query">
        <el-form-item label="搜索">
          <el-input v-model="query.keyword" placeholder="编号/名称/客户/负责人" clearable style="width:220px" />
        </el-form-item>
        <el-form-item label="审批状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:130px">
            <el-option v-for="s in statusList" :key="s.v" :label="s.l" :value="s.v" />
          </el-select>
        </el-form-item>
        <el-form-item label="当前阶段">
          <el-select v-model="query.stage" placeholder="全部" clearable style="width:150px">
            <el-option v-for="s in stageList" :key="s.v" :label="s.l" :value="s.v" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" icon="Plus" @click="openForm()">新建立项申请</el-button>
        <el-button icon="Upload" @click="importVisible = true">导入申请书</el-button>
        <el-button icon="Download" @click="downloadTpl">下载模板</el-button>
        <span class="stat-line">
          共 <b>{{ stats.total || 0 }}</b> 项 ·
          草稿 <b>{{ stats.byStatus?.draft || 0 }}</b> ·
          已提交 <b>{{ stats.byStatus?.submitted || 0 }}</b> ·
          已批准 <b>{{ stats.byStatus?.approved || 0 }}</b> ·
          已驳回 <b>{{ stats.byStatus?.rejected || 0 }}</b>
        </span>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="initNo" label="申请编号" width="150" fixed />
        <el-table-column prop="projectName" label="项目名称" min-width="170" show-overflow-tooltip />
        <el-table-column prop="projectType" label="项目类型" width="110" />
        <el-table-column prop="customerNo" label="客户编号" width="120" />
        <el-table-column prop="owner" label="负责人" width="90" />
        <el-table-column prop="applicant" label="申请人" width="90" />
        <el-table-column prop="applyDate" label="申请日期" width="110" />
        <el-table-column label="审批状态" width="90" align="center">
          <template #default="{row}">
            <el-tag size="small" :type="statusTag(row.approvalStatus)">{{ statusLabel(row.approvalStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前阶段" width="130" align="center">
          <template #default="{row}">
            <el-tag size="small" effect="plain" :type="row.workflowStage==='rejected'?'danger':'info'">
              {{ stageLabel(row.workflowStage) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情/审批</el-button>
            <el-button link type="success" size="small" :disabled="row.approvalStatus==='approved'" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" size="small" :disabled="row.approvalStatus==='approved'" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
          :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="importVisible" title="导入立项申请书（多Sheet结构化，同 EBMS）" width="660px">
      <el-alert type="info" :closable="false" style="margin-bottom:10px"
        title="支持按 Sheet 名识别：立项申请书/基本信息、产品规格对比、可实现性评估、销售预测、特殊要求" />
      <BatchImport endpoint="/v1/initiations/import-structured" accept=".xlsx,.xls" @done="onImported" />
    </el-dialog>

    <el-drawer v-model="formDrawer.visible" :title="form.id ? '编辑立项申请书' : '新建立项申请书'" size="1000px">
      <el-form :model="form" label-width="120px">
        <el-divider content-position="left">一、基本信息</el-divider>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="项目编号"><el-input v-model="form.projectNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="项目名称" required><el-input v-model="form.projectName" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="项目类型">
            <el-select v-model="form.projectType" style="width:100%">
              <el-option label="自研" value="自研" /><el-option label="客制" value="客制" />
              <el-option label="客户定制+自研" value="客户定制+自研" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="起始时间"><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="项目部门"><el-input v-model="form.department" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="主要负责人"><el-input v-model="form.owner" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="配合人员"><el-input v-model="form.cooperators" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="其他"><el-input v-model="form.otherInfo" /></el-form-item></el-col>
        </el-row>

        <el-divider content-position="left">二、客户信息</el-divider>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="客户编号"><el-input v-model="form.customerNo" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="客户类型"><el-input v-model="form.customerType" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="客户等级"><el-input v-model="form.customerLevel" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="客户赢率"><el-input v-model="form.customerWinRate" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="市场状况"><el-input v-model="form.marketStatus" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="竞争对手"><el-input v-model="form.hasCompetitor" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="采购周期"><el-input v-model="form.purchaseCycle" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="定制开发类型"><el-input v-model="form.devType" /></el-form-item></el-col>
          <el-col :span="8"></el-col>
          <el-col :span="12"><el-form-item label="客户痛点"><el-input v-model="form.customerPain" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="关键成功要素"><el-input v-model="form.keySuccess" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>

        <el-divider content-position="left">三、产品规格对比
          <el-button link type="primary" size="small" @click="specRows.push({ 规格项: '', 产品1: '', 产品2: '' })">+ 规格项</el-button>
        </el-divider>
        <el-table :data="specRows" border size="small" style="margin-bottom:8px">
          <el-table-column label="规格项" width="180"><template #default="{row}"><el-input v-model="row.规格项" size="small" /></template></el-table-column>
          <el-table-column label="产品1"><template #default="{row}"><el-input v-model="row.产品1" size="small" /></template></el-table-column>
          <el-table-column label="产品2"><template #default="{row}"><el-input v-model="row.产品2" size="small" /></template></el-table-column>
          <el-table-column width="60" align="center"><template #default="{$index}"><el-button link type="danger" size="small" @click="specRows.splice($index,1)">删除</el-button></template></el-table-column>
        </el-table>

        <el-divider content-position="left">四、可实现性评估
          <el-button link type="primary" size="small" @click="feasRows.push({ 类别: '', 评估项: '', 结果: '', 关联项: '', 备注: '' })">+ 评估项</el-button>
        </el-divider>
        <el-table :data="feasRows" border size="small" style="margin-bottom:8px">
          <el-table-column label="类别" width="130"><template #default="{row}"><el-input v-model="row.类别" size="small" /></template></el-table-column>
          <el-table-column label="评估项"><template #default="{row}"><el-input v-model="row.评估项" size="small" /></template></el-table-column>
          <el-table-column label="结果" width="110"><template #default="{row}"><el-input v-model="row.结果" size="small" /></template></el-table-column>
          <el-table-column label="关联项"><template #default="{row}"><el-input v-model="row.关联项" size="small" /></template></el-table-column>
          <el-table-column label="备注"><template #default="{row}"><el-input v-model="row.备注" size="small" /></template></el-table-column>
          <el-table-column width="60" align="center"><template #default="{$index}"><el-button link type="danger" size="small" @click="feasRows.splice($index,1)">删除</el-button></template></el-table-column>
        </el-table>

        <el-divider content-position="left">五、销售预测
          <el-button link type="primary" size="small" @click="forecastRows.push({ 周期: '', 产品型号: '', 数量: '', 金额: '' })">+ 周期</el-button>
        </el-divider>
        <el-table :data="forecastRows" border size="small" style="margin-bottom:8px">
          <el-table-column label="周期" width="150"><template #default="{row}"><el-input v-model="row.周期" size="small" /></template></el-table-column>
          <el-table-column label="产品型号"><template #default="{row}"><el-input v-model="row.产品型号" size="small" /></template></el-table-column>
          <el-table-column label="数量" width="150"><template #default="{row}"><el-input v-model="row.数量" size="small" /></template></el-table-column>
          <el-table-column label="金额" width="150"><template #default="{row}"><el-input v-model="row.金额" size="small" /></template></el-table-column>
          <el-table-column width="60" align="center"><template #default="{$index}"><el-button link type="danger" size="small" @click="forecastRows.splice($index,1)">删除</el-button></template></el-table-column>
        </el-table>

        <el-divider content-position="left">六、特殊要求
          <el-button link type="primary" size="small" @click="reqRows.push({ 产品: '', 要求: '' })">+ 产品</el-button>
        </el-divider>
        <el-table :data="reqRows" border size="small" style="margin-bottom:8px">
          <el-table-column label="产品" width="200"><template #default="{row}"><el-input v-model="row.产品" size="small" /></template></el-table-column>
          <el-table-column label="要求"><template #default="{row}"><el-input v-model="row.要求" size="small" /></template></el-table-column>
          <el-table-column width="60" align="center"><template #default="{$index}"><el-button link type="danger" size="small" @click="reqRows.splice($index,1)">删除</el-button></template></el-table-column>
        </el-table>

        <el-divider content-position="left">七、研发目标与内容</el-divider>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="立项背景"><el-input v-model="form.background" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="必要性分析"><el-input v-model="form.necessity" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="研发目标"><el-input v-model="form.rdObjectives" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="研发内容"><el-input v-model="form.rdContent" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="关键技术/创新点"><el-input v-model="form.keyInnovation" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="技术方案"><el-input v-model="form.techSolution" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="研发计划概述"><el-input v-model="form.planSummary" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="关键里程碑"><el-input v-model="form.milestones" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="预期成果"><el-input v-model="form.expectedOutcome" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="经济效益"><el-input v-model="form.economicBenefit" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="预算总额"><el-input-number v-model="form.budgetTotal" :min="0" :controls="false" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="团队需求"><el-input v-model="form.teamRequirement" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="目标市场"><el-input v-model="form.targetMarket" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="风险分析"><el-input v-model="form.riskAnalysis" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="风险对策"><el-input v-model="form.riskMeasures" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remarks" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formDrawer.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-drawer>

    <!-- 详情 + 审批 -->
    <el-drawer v-model="detailDrawer.visible" size="860px"
      :title="'立项申请书 · ' + (detail.initNo || '') + ' · ' + (detail.projectName || '')">
      <template v-if="detail.id">
        <div class="stage-steps">
          <el-steps :active="stageIndex" align-center finish-status="success">
            <el-step v-for="(name, code) in stageNames" :key="code"
              :title="name"
              :status="detail.workflowStage==='rejected' && stageIndex===undefined ? 'error' : (stepIndexOf(code) <= stageIndex ? 'success' : 'wait')" />
          </el-steps>
          <div class="stage-actions">
            <el-tag :type="statusTag(detail.approvalStatus)">{{ statusLabel(detail.approvalStatus) }}</el-tag>
            <el-button v-if="canAdvance" type="primary" size="small" @click="openAdvance">
              推进「{{ stageNames[detail.workflowStage] }} → {{ stageNames[nextStage] }}」
            </el-button>
            <el-button v-if="canReject" type="warning" plain size="small" @click="handleReject">驳回</el-button>
            <el-button v-if="canApproveToProject" type="success" size="small" @click="handleApproveToProject">
              {{ detail.projectId ? '查看已转项目 #' + detail.projectId : '批准并转为研发项目' }}
            </el-button>
            <el-button size="small" icon="Printer" @click="previewInit">汇总预览/打印</el-button>
          </div>
        </div>

        <el-descriptions title="一、基本信息" :column="3" border size="small">
          <el-descriptions-item label="申请编号">{{ detail.initNo }}</el-descriptions-item>
          <el-descriptions-item label="项目编号">{{ detail.projectNo }}</el-descriptions-item>
          <el-descriptions-item label="项目类型">{{ detail.projectType }}</el-descriptions-item>
          <el-descriptions-item label="起始时间">{{ detail.startDate }}</el-descriptions-item>
          <el-descriptions-item label="项目部门">{{ detail.department }}</el-descriptions-item>
          <el-descriptions-item label="主要负责人">{{ detail.owner }}</el-descriptions-item>
          <el-descriptions-item label="配合人员">{{ detail.cooperators }}</el-descriptions-item>
          <el-descriptions-item label="其他">{{ detail.otherInfo }}</el-descriptions-item>
          <el-descriptions-item label="预算总额">{{ detail.budgetTotal }}</el-descriptions-item>
        </el-descriptions>

        <el-descriptions title="二、客户信息" :column="3" border size="small" style="margin-top:14px">
          <el-descriptions-item label="客户编号">{{ detail.customerNo }}</el-descriptions-item>
          <el-descriptions-item label="客户类型">{{ detail.customerType }}</el-descriptions-item>
          <el-descriptions-item label="客户等级">{{ detail.customerLevel }}</el-descriptions-item>
          <el-descriptions-item label="客户赢率">{{ detail.customerWinRate }}</el-descriptions-item>
          <el-descriptions-item label="竞争对手">{{ detail.hasCompetitor }}</el-descriptions-item>
          <el-descriptions-item label="采购周期">{{ detail.purchaseCycle }}</el-descriptions-item>
          <el-descriptions-item label="市场状况">{{ detail.marketStatus }}</el-descriptions-item>
          <el-descriptions-item label="定制开发类型">{{ detail.devType }}</el-descriptions-item>
          <el-descriptions-item label="客户痛点">{{ detail.customerPain }}</el-descriptions-item>
          <el-descriptions-item label="关键成功要素" :span="2">{{ detail.keySuccess }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">三、产品规格对比</el-divider>
        <el-table :data="detail.specRows" border size="small" empty-text="无">
          <el-table-column prop="规格项" label="规格项" width="180" />
          <el-table-column prop="产品1" label="产品1" />
          <el-table-column prop="产品2" label="产品2" />
        </el-table>
        <el-divider content-position="left">四、可实现性评估</el-divider>
        <el-table :data="detail.feasRows" border size="small" empty-text="无">
          <el-table-column prop="类别" label="类别" width="130" />
          <el-table-column prop="评估项" label="评估项" />
          <el-table-column prop="结果" label="结果" width="100" />
          <el-table-column prop="关联项" label="关联项" />
          <el-table-column prop="备注" label="备注" />
        </el-table>
        <el-divider content-position="left">五、销售预测</el-divider>
        <el-table v-if="detail.forecastRows && detail.forecastRows.length" :data="detail.forecastRows" border size="small">
          <el-table-column prop="周期" label="时间周期" width="120" />
          <el-table-column v-for="c in (detail.forecastCols || [])" :key="c" :prop="c" :label="String(c).replace(/\n/g, ' ')" />
        </el-table>
        <el-table v-if="detail.forecastExtra && detail.forecastExtra.length" :data="detail.forecastExtra"
          border size="small" style="margin-top:6px">
          <el-table-column prop="项目" label="金额汇总" width="140" />
          <el-table-column label="值"><template #default="{ row }">{{ (row.值 || []).join('　') }}</template></el-table-column>
        </el-table>
        <el-empty v-if="!(detail.forecastRows || []).length && !(detail.forecastExtra || []).length" description="暂无" :image-size="60" />
        <el-divider content-position="left">六、特殊要求</el-divider>
        <el-table :data="detail.reqRows" border size="small" empty-text="无">
          <el-table-column prop="产品" label="产品" width="200" />
          <el-table-column prop="要求" label="要求" />
        </el-table>
        <el-divider content-position="left">立项决议</el-divider>
        <el-table v-if="detail.approvalRows && detail.approvalRows.length" :data="detail.approvalRows" border size="small">
          <el-table-column prop="项目" label="项目" width="140" />
          <el-table-column label="值"><template #default="{ row }">{{ (row.值 || []).join('　') }}</template></el-table-column>
        </el-table>
        <el-empty v-else description="暂无" :image-size="60" />
        <el-divider content-position="left">七、研发目标与内容</el-divider>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="立项背景">{{ detail.background }}</el-descriptions-item>
          <el-descriptions-item label="必要性分析">{{ detail.necessity }}</el-descriptions-item>
          <el-descriptions-item label="研发目标">{{ detail.rdObjectives }}</el-descriptions-item>
          <el-descriptions-item label="研发内容">{{ detail.rdContent }}</el-descriptions-item>
          <el-descriptions-item label="关键技术/创新点">{{ detail.keyInnovation }}</el-descriptions-item>
          <el-descriptions-item label="技术方案">{{ detail.techSolution }}</el-descriptions-item>
          <el-descriptions-item label="研发计划">{{ detail.planSummary }}</el-descriptions-item>
          <el-descriptions-item label="关键里程碑">{{ detail.milestones }}</el-descriptions-item>
          <el-descriptions-item label="预期成果">{{ detail.expectedOutcome }}</el-descriptions-item>
          <el-descriptions-item label="经济效益">{{ detail.economicBenefit }}</el-descriptions-item>
          <el-descriptions-item label="风险分析">{{ detail.riskAnalysis }}</el-descriptions-item>
          <el-descriptions-item label="风险对策">{{ detail.riskMeasures }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">审批流转记录</el-divider>
        <el-timeline>
          <el-timeline-item :timestamp="detail.step1ApplyDate" type="success" placement="top">
            <b>① 立项发起</b> — 申请人: {{ detail.step1Applicant || detail.applicant }}
          </el-timeline-item>
          <el-timeline-item v-if="detail.step2Date || detail.step2Approver" :timestamp="detail.step2Date" placement="top">
            <b>② 部门审核</b> — {{ detail.step2Approver }} · {{ detail.step2Result }}
            <p v-if="detail.step2Opinion" style="color:#666">{{ detail.step2Opinion }}</p>
          </el-timeline-item>
          <el-timeline-item v-if="detail.step3RdDate || detail.step3FinanceDate" placement="top"
            :timestamp="detail.step3RdDate || detail.step3FinanceDate">
            <b>③ 研发/财务审核</b>
            <p style="color:#666">研发: {{ detail.step3RdReviewer }} {{ detail.step3RdOpinion }}</p>
            <p style="color:#666">财务: {{ detail.step3FinanceReviewer }} {{ detail.step3FinanceOpinion }}</p>
          </el-timeline-item>
          <el-timeline-item v-if="detail.step4Date || detail.step4Approver" :timestamp="detail.step4Date" placement="top"
            :type="detail.approvalStatus==='rejected'?'danger':'success'">
            <b>④ 总经理批准</b> — {{ detail.step4Approver }}
            <p v-if="detail.step4Opinion" style="color:#666">{{ detail.step4Opinion }}</p>
          </el-timeline-item>
          <el-timeline-item v-if="detail.projectId" :timestamp="detail.approvalDate" type="success" placement="top">
            <b>⑤ 项目经理执行</b> — 已转为研发项目 #{{ detail.projectId }}
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>

    <!-- 审批推进 -->
    <el-dialog v-model="advanceDialog.visible" :title="'审批推进 · ' + (detail.initNo || '')" width="520px">
      <el-form label-width="90px">
        <el-form-item label="当前阶段"><el-tag>{{ stageNames[detail.workflowStage] }}</el-tag></el-form-item>
        <el-form-item label="审核人"><el-input v-model="advanceForm.reviewer" placeholder="默认当前用户" /></el-form-item>
        <el-form-item v-if="detail.workflowStage==='gm'" label="审批结果">
          <el-radio-group v-model="advanceForm.result">
            <el-radio label="通过">通过</el-radio>
            <el-radio label="reject">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="意见"><el-input v-model="advanceForm.opinion" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="advanceDialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAdvance">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageInitiation, getInitiation, initiationStats, createInitiation, updateInitiation,
  deleteInitiation, advanceInitiation, rejectInitiation, approveToProject, downloadInitiationTemplate
} from '@/api/initiation'

const importVisible = ref(false)
function onImported() { importVisible.value = false; loadData() }
function downloadTpl() { downloadInitiationTemplate() }

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const stats = ref({})

const statusList = [
  { v: 'draft', l: '草稿' }, { v: 'submitted', l: '已提交' },
  { v: 'approved', l: '已批准' }, { v: 'rejected', l: '已驳回' }
]
const stageNames = { apply: '立项发起', dept: '部门审核', review: '研发/财务审核', gm: '总经理批准', execute: '项目经理执行', rejected: '已驳回' }
const stageList = Object.entries(stageNames).map(([v, l]) => ({ v, l }))
const stageOrder = ['apply', 'dept', 'review', 'gm', 'execute']

const statusLabel = (s) => ({ draft: '草稿', submitted: '已提交', approved: '已批准', rejected: '已驳回' }[s] || s)
const statusTag = (s) => ({ draft: 'info', submitted: 'warning', approved: 'success', rejected: 'danger' }[s] || 'info')
const stageLabel = (s) => stageNames[s] || s

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '', stage: '' })

const formDrawer = reactive({ visible: false })
const form = reactive({})
const specRows = ref([])
const feasRows = ref([])
const forecastRows = ref([])
const reqRows = ref([])

const detailDrawer = reactive({ visible: false })
const detail = ref({})

// 汇总预览/打印 (对应 EBMS 立项申请书「汇总预览」)
function esc(v) { return (v === null || v === undefined || v === '') ? '-' : String(v).replace(/</g, '&lt;') }
function previewInit() {
  const d = detail.value || {}
  const td = (l, v) => `<tr><td class="k">${l}</td><td>${esc(v)}</td></tr>`
  let h = '<html><head><meta charset="utf-8"><title>销售需求立项申请书</title><style>' +
    'body{font-family:"Microsoft YaHei",sans-serif;color:#222;padding:24px;font-size:13px}' +
    'h2{text-align:center;margin:0 0 4px} .sub{text-align:center;color:#888;font-size:12px;margin-bottom:16px}' +
    'h3{color:#3a6df0;border-bottom:2px solid #3a6df0;padding-bottom:4px;margin:18px 0 8px}' +
    'table{width:100%;border-collapse:collapse;margin-bottom:12px} td,th{border:1px solid #ddd;padding:5px;text-align:left;vertical-align:top}' +
    'td.k{color:#888;width:130px;background:#fafafa} .sign{display:flex;justify-content:space-between;margin-top:40px}' +
    '.sign div{text-align:center;width:170px} .sign .line{border-top:1px solid #333;padding-top:4px;font-size:12px}' +
    '.bar{text-align:center;margin:16px 0}@media print{.bar{display:none}}</style></head><body>' +
    '<div class="bar"><button onclick="window.print()">打印</button></div>' +
    '<h2>销售需求立项申请书</h2>' +
    `<div class="sub">项目编号：${esc(d.projectNo)} ｜ 申请人：${esc(d.applicant)} ｜ 日期：${esc(d.applyDate)}</div>`

  h += '<h3>一、基本信息</h3><table>'
  h += td('申请编号', d.initNo) + td('项目编号', d.projectNo) + td('项目名称', d.projectName)
  h += td('项目类型', d.projectType) + td('起始时间', d.startDate) + td('项目部门', d.department)
  h += td('主要负责人', d.owner) + td('配合人员', d.cooperators) + td('其他', d.otherInfo)
  h += '</table>'

  h += '<h3>二、客户信息</h3><table>'
  h += td('客户编号', d.customerNo) + td('客户类型', d.customerType) + td('客户等级', d.customerLevel)
  h += td('客户赢率', d.customerWinRate) + td('市场状况', d.marketStatus) + td('竞争对手', d.hasCompetitor)
  h += td('采购周期', d.purchaseCycle) + td('定制开发类型', d.devType) + td('客户痛点', d.customerPain)
  h += td('关键成功要素', d.keySuccess) + '</table>'

  h += '<h3>三、产品规格对比</h3>'
  h += (d.specRows && d.specRows.length)
    ? '<table><tr><th>规格项</th>' + Object.keys(d.specRows[0]).filter(k => k !== '规格项').map(k => `<th>${esc(k)}</th>`).join('') + '</tr>' +
      d.specRows.map(r => `<tr><td class="k">${esc(r['规格项'])}</td>` + Object.keys(r).filter(k => k !== '规格项').map(k => `<td>${esc(r[k])}</td>`).join('') + '</tr>').join('') + '</table>'
    : '<p class="k">暂无</p>'

  h += '<h3>四、可实现性评估</h3>'
  h += (d.feasRows && d.feasRows.length)
    ? '<table><tr><th>类别</th><th>评估项</th><th>结果</th><th>关联项</th><th>备注</th></tr>' +
      d.feasRows.map(r => `<tr><td>${esc(r['类别'])}</td><td>${esc(r['评估项'])}</td><td>${esc(r['结果'])}</td><td>${esc(r['关联项'])}</td><td>${esc(r['备注'])}</td></tr>`).join('') + '</table>'
    : '<p class="k">暂无</p>'

  h += '<h3>五、销售预测</h3>'
  const fcols = d.forecastCols || []
  h += (d.forecastRows && d.forecastRows.length)
    ? '<table><tr><th>时间周期</th>' + fcols.map(c => `<th>${esc(String(c).replace(/\n/g, ' '))}</th>`).join('') + '</tr>' +
      d.forecastRows.map(r => `<tr><td class="k">${esc(r['周期'])}</td>` + fcols.map(c => `<td>${esc(r[c])}</td>`).join('') + '</tr>').join('') + '</table>'
    : '<p class="k">暂无</p>'

  h += '<h3>六、特殊要求</h3>'
  h += (d.reqRows && d.reqRows.length)
    ? '<table><tr><th>产品</th><th>要求</th></tr>' +
      d.reqRows.map(r => `<tr><td>${esc(r['产品'])}</td><td>${esc(r['要求'])}</td></tr>`).join('') + '</table>'
    : '<p class="k">暂无</p>'

  h += '<h3>七、审批</h3><table>'
  h += td('审批状态', statusLabel(d.approvalStatus)) + td('审批人', d.approver)
  h += td('审批日期', d.approvalDate) + td('审批意见', d.approvalOpinion)
  h += '</table>'
  h += '<div class="sign"><div class="line">项目经理</div><div class="line">销售总监</div><div class="line">研发中心</div></div>'
  h += '</body></html>'

  const w = window.open('', '_blank')
  if (!w) { ElMessage.warning('浏览器拦截了新窗口，请允许弹窗'); return }
  w.document.write(h)
  w.document.close()
}
const advanceDialog = reactive({ visible: false })
const advanceForm = reactive({ reviewer: '', opinion: '', result: '通过' })

const stageIndex = computed(() => {
  const i = stageOrder.indexOf(detail.value.workflowStage)
  return i < 0 ? 0 : i
})
const nextStage = computed(() => {
  const i = stageOrder.indexOf(detail.value.workflowStage)
  return i >= 0 && i < stageOrder.length - 1 ? stageOrder[i + 1] : ''
})
const canAdvance = computed(() =>
  detail.value.id && detail.value.workflowStage !== 'rejected'
  && detail.value.approvalStatus !== 'approved'
  && stageOrder.indexOf(detail.value.workflowStage) >= 0
  && stageOrder.indexOf(detail.value.workflowStage) < stageOrder.length - 1)
const canReject = computed(() =>
  detail.value.id && detail.value.approvalStatus !== 'approved'
  && stageOrder.indexOf(detail.value.workflowStage) >= 1 && detail.value.workflowStage !== 'rejected')
const canApproveToProject = computed(() =>
  detail.value.id && (detail.value.workflowStage === 'gm'
    || detail.value.workflowStage === 'execute' || detail.value.approvalStatus === 'approved'))

function stepIndexOf(code) { return stageOrder.indexOf(code) }

async function loadData() {
  loading.value = true
  try {
    const res = await pageInitiation(query)
    tableData.value = res.data.records
    total.value = res.data.total
    const st = await initiationStats()
    stats.value = st.data || {}
  } finally { loading.value = false }
}
async function resetQuery() { Object.assign(query, { pageNum: 1, keyword: '', status: '', stage: '' }); loadData() }

function parseJson(s, fallback) {
  try { const v = JSON.parse(s); return Array.isArray(v) ? v : fallback } catch { return fallback }
}

function openForm(row) {
  Object.keys(form).forEach(k => delete form[k])
  if (row) {
    Object.assign(form, JSON.parse(JSON.stringify(row)))
    specRows.value = parseJson(row.productSpecs, [])
    feasRows.value = parseJson(row.feasibility, [])
    forecastRows.value = parseJson(row.salesForecast, [])
    reqRows.value = parseJson(row.specialReqs, [])
  } else {
    Object.assign(form, { projectType: '客制', department: '研发中心', startDate: new Date().toISOString().slice(0, 10) })
    specRows.value = [{ 规格项: '功率', 产品1: '', 产品2: '' }]
    feasRows.value = [{ 类别: '设计标准', 评估项: '', 结果: '', 关联项: '', 备注: '' }]
    forecastRows.value = [{ 周期: '6个月', 产品型号: '', 数量: '', 金额: '' }]
    reqRows.value = [{ 产品: '', 要求: '' }]
  }
  formDrawer.visible = true
}

async function submitForm() {
  if (!form.projectName) { ElMessage.warning('请输入项目名称'); return }
  submitting.value = true
  try {
    const payload = {
      ...form,
      productSpecs: JSON.stringify(specRows.value.filter(r => Object.values(r).some(v => v))),
      feasibility: JSON.stringify(feasRows.value.filter(r => Object.values(r).some(v => v))),
      salesForecast: JSON.stringify(forecastRows.value.filter(r => Object.values(r).some(v => v))),
      specialReqs: JSON.stringify(reqRows.value.filter(r => Object.values(r).some(v => v)))
    }
    if (form.id) { await updateInitiation(payload); ElMessage.success('已保存') }
    else { await createInitiation(payload); ElMessage.success('立项申请书已创建') }
    formDrawer.visible = false
    loadData()
  } finally { submitting.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`删除立项申请书 [${row.initNo}]?`, '警告', { type: 'warning' })
  await deleteInitiation(row.id); ElMessage.success('已删除'); loadData()
}

async function openDetail(row) {
  const res = await getInitiation(row.id)
  const d = res.data
  d.specRows = parseJson(d.productSpecs, [])
  d.feasRows = parseJson(d.feasibility, [])
  d.reqRows = parseJson(d.specialReqs, [])
  d.approvalRows = parseJson(d.approvalSigns, [])
  // 销售预测可能是对象 {cols,rows,extra} 或数组
  d.forecastCols = []; d.forecastRows = []; d.forecastExtra = []
  try {
    const sf = JSON.parse(d.salesForecast || 'null')
    if (sf && !Array.isArray(sf) && Array.isArray(sf.rows)) {
      d.forecastCols = sf.cols || []
      d.forecastRows = sf.rows
      d.forecastExtra = sf.extra || []
    } else if (Array.isArray(sf)) {
      d.forecastRows = sf
    }
  } catch (e) { /* ignore */ }
  detail.value = d
  detailDrawer.visible = true
}

function openAdvance() {
  advanceForm.reviewer = ''
  advanceForm.opinion = ''
  advanceForm.result = '通过'
  advanceDialog.visible = true
}

async function submitAdvance() {
  submitting.value = true
  try {
    const res = await advanceInitiation(detail.value.id, {
      fromStage: detail.value.workflowStage,
      reviewer: advanceForm.reviewer,
      opinion: advanceForm.opinion,
      result: advanceForm.result
    })
    ElMessage.success('已推进至: ' + stageLabel(res.data.workflowStage))
    advanceDialog.visible = false
    await openDetail(res.data)
    loadData()
  } finally { submitting.value = false }
}

async function handleReject() {
  const { value } = await ElMessageBox.prompt('请输入驳回意见', '驳回立项申请', { inputType: 'textarea' }).catch(() => ({ value: null }))
  if (value === null) return
  await rejectInitiation(detail.value.id, value)
  ElMessage.success('已驳回')
  await openDetail({ id: detail.value.id })
  loadData()
}

async function handleApproveToProject() {
  if (detail.value.projectId) {
    ElMessage.info('该项目已创建: #' + detail.value.projectId)
    return
  }
  await ElMessageBox.confirm('批准该立项申请并自动创建研发项目(含 G0 概念立项)?', '批准转项目', { type: 'success' })
  const res = await approveToProject(detail.value.id, {})
  ElMessage.success('已创建研发项目 #' + res.data.projectId)
  await openDetail({ id: detail.value.id })
  loadData()
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.stat-line { margin-left: 14px; font-size: 13px; color: #606266;
  b { color: #409eff; }
}
.stage-steps { margin-bottom: 8px; }
.stage-actions { display: flex; align-items: center; gap: 10px; margin: 14px 0 6px; }
</style>
