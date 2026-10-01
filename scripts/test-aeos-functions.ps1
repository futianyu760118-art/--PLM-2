# PLM-2 关键业务流程 端到端功能测试 (可逆, 自动清理)
$ErrorActionPreference = "Continue"
$Plm = "http://localhost"
$pass = 0; $fail = 0
function Check($area, $name, $cond, $detail) {
  $st = if ($cond) { $script:pass++; "PASS" } else { $script:fail++; "FAIL" }
  Write-Host ("[{0}] {1} | {2} | {3}" -f $st, $area, $name, $detail) -ForegroundColor $(if ($cond) { 'Green' } else { 'Red' })
}
function PostJson($url, $headers, $obj) {
  $bytes = [System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Depth 8 -Compress))
  return Invoke-RestMethod -Uri $url -Method POST -Headers $headers -Body $bytes -ContentType "application/json; charset=utf-8" -TimeoutSec 30
}
function PutJson($url, $headers, $obj) {
  $bytes = [System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Depth 8 -Compress))
  return Invoke-RestMethod -Uri $url -Method PUT -Headers $headers -Body $bytes -ContentType "application/json; charset=utf-8" -TimeoutSec 30
}
function RemoveUrl($url, $headers) { try { Invoke-RestMethod -Uri $url -Method DELETE -Headers $headers -TimeoutSec 20 | Out-Null; return $true } catch { return $false } }

$KAIMO = -join @(0x5F00,0x6A21,0x8BC4,0x5BA1 | % { [char]$_ })
$TG = -join @(0x901A,0x8FC7 | % { [char]$_ })

$login = PostJson "$Plm/api/auth/login" @{} @{ username='admin'; password='admin@123' }
$h = @{ Authorization = "Bearer $($login.data.token)" }
Check "认证" "登录" ($login.code -eq 200) ""

# A. 基础数据 CRUD
$mk = 'AEOS-' + (Get-Date -Format 'HHmmss')
$a = PostJson "$Plm/api/v1/system/base/customer" $h @{ code=$mk; name=$mk; level='A' }
$id = $a.data.id
$g = Invoke-RestMethod "$Plm/api/v1/system/base/customer/$id" -Headers $h -TimeoutSec 20
Check "基础数据" "新增+详情" ($g.data.code -eq $mk) "id=$id"
$u = PutJson "$Plm/api/v1/system/base/customer" $h @{ id=$id; code=$mk; name=($mk + '-2'); level='B' }
$g2 = Invoke-RestMethod "$Plm/api/v1/system/base/customer/$id" -Headers $h -TimeoutSec 20
Check "基础数据" "修改" ($g2.data.name -eq ($mk + '-2')) "name=$($g2.data.name)"
Check "基础数据" "删除" (RemoveUrl "$Plm/api/v1/system/base/customer/$id" $h) ""

# B. 物料 建档->发布->删除
$m = PostJson "$Plm/api/v1/parts" $h @{ materialName='AEOS-MAT'; materialType='STANDARD'; unit='PCS'; specification='spec-x' }
$mid = $m.data.id
Check "物料" "建档(自动编号)" ($m.data.partNo -ne $null) "partNo=$($m.data.partNo)"
$sub = PostJson "$Plm/api/v1/parts/$mid/actions/submit-review" $h @{}
Check "物料" "提交评审(草稿→评审中)" ($sub.code -eq 200) "code=$($sub.code)"
$rel = PostJson "$Plm/api/v1/parts/$mid/actions/release" $h @{}
Check "物料" "发布(评审中→正式发布,含DQ门禁)" ($rel.code -eq 200) "code=$($rel.code) msg=$($rel.message)"
Check "物料" "删除" (RemoveUrl "$Plm/api/v1/parts/$mid" $h) ""

# C. 工作表 -> 自动汇总 -> 节点 (AEOS 闭环)
$t = Invoke-RestMethod "$Plm/api/v1/rd/tracking?pageSize=1" -Headers $h -TimeoutSec 20
$projId = $t.data.records[0].id; $projNo = $t.data.records[0].projectNo
Invoke-RestMethod "$Plm/api/project/$projId/nodes/MOLD_REVIEW" -Method PUT -Headers $h -Body ([System.Text.Encoding]::UTF8.GetBytes('{"status":"NOT_SET","remark":""}')) -TimeoutSec 20 | Out-Null
$rev = PostJson "$Plm/api/v1/rd/sheets/review" $h @{ project_no=$projNo; review_no='AEOS-REV-1'; review_type=$KAIMO; conclusion=$TG }
Invoke-RestMethod "$Plm/api/v1/rd/tracking/$projId/rollup" -Method POST -Headers $h -TimeoutSec 30 | Out-Null
$nm = Invoke-RestMethod "$Plm/api/project/$projId/nodes" -Headers $h -TimeoutSec 20
$mr = ($nm.data.nodes | ? { $_.nodeCode -eq 'MOLD_REVIEW' }).status
Check "工作表闭环" "评审单通过→节点完成(完成判定)" ($mr -eq 'DONE') "MOLD_REVIEW=$mr"
Check "工作表闭环" "清理评审单" (RemoveUrl "$Plm/api/v1/rd/sheets/review/$($rev.data.id)" $h) ""

# D. 立项 创建->推进(apply->dept->review) -> 删除
$ini = PostJson "$Plm/api/initiation" $h @{ projectName='AEOS-INIT'; projectType='研发'; customerNo='C001'; owner='AEOS' }
$iid = $ini.data.id
Check "立项" "创建" ($iid -ne $null) "id=$iid initNo=$($ini.data.initNo)"
$s1 = PostJson "$Plm/api/initiation/$iid/advance" $h @{ fromStage='apply'; reviewer='AEOS'; opinion='ok' }
$s2 = PostJson "$Plm/api/initiation/$iid/advance" $h @{ fromStage='dept'; reviewer='AEOS'; opinion='ok' }
$s3 = PostJson "$Plm/api/initiation/$iid/advance" $h @{ fromStage='review'; reviewer='AEOS'; opinion='ok' }
Check "立项" "五阶段审批推进(apply→dept→review)" ($s3.data.workflowStage -eq 'gm' -and $s3.data.approvalStatus -eq 'submitted') "stage=$($s3.data.workflowStage) status=$($s3.data.approvalStatus)"
Check "立项" "删除(未批准可删)" (RemoveUrl "$Plm/api/initiation/$iid" $h) ""

# E. 编码器 预览(不占号)
$cg = PostJson "$Plm/api/v1/codegen/preview" $h @{ objectType='PART'; context=@{ materialType='STANDARD' } }
Check "编码器" "预览编号" ($cg.code -eq 200) "code=$($cg.code)"

# F. 分享列表
$sh = Invoke-RestMethod "$Plm/api/share/list" -Headers $h -TimeoutSec 20
Check "分享" "分享列表" ($sh.code -eq 200) "total=$($sh.data.Count)"

# G. 系统自检
$chk = Invoke-RestMethod "$Plm/api/system/check/run" -Headers $h -TimeoutSec 60
Check "系统自检" "一键自检" ($chk.code -eq 200) ""

Write-Host ""
Write-Host ("===== FUNC RESULT: PASS={0} FAIL={1} =====" -f $pass, $fail) -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
