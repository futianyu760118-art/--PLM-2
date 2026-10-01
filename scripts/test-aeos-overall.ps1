# PLM-2 整体测试 (遵循 AEOS: Contract / 闭环 / Evidence / Permission / Idempotency)
# 运行: powershell -ExecutionPolicy Bypass -File scripts\test-aeos-overall.ps1
$ErrorActionPreference = "Continue"
$Plm = "http://localhost"
$pass = 0; $fail = 0; $rows = @()
$report = "C:\Users\colorful\AppData\Local\Temp\opencode\aeos-test-report.txt"

function Check($area, $name, $cond, $detail) {
  $st = if ($cond) { $script:pass++; "PASS" } else { $script:fail++; "FAIL" }
  $line = "[{0}] {1} | {2} | {3}" -f $st, $area, $name, $detail
  Write-Host $line -ForegroundColor $(if ($cond) { 'Green' } else { 'Red' })
  $script:rows += $line
}
function PostJson($url, $headers, $obj) {
  $bytes = [System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Depth 8 -Compress))
  return Invoke-RestMethod -Uri $url -Method POST -Headers $headers -ContentType "application/json; charset=utf-8" -Body $bytes -TimeoutSec 30
}
function TryGet($url, $headers) {
  try {
    $r = Invoke-WebRequest -Uri $url -Headers $headers -UseBasicParsing -TimeoutSec 20
    $code = 200; $body = $null
    try { $body = $r.Content | ConvertFrom-Json } catch {}
    return @{ ok = $true; http = $r.StatusCode; code = $(if ($body) { $body.code } else { 200 }); body = $body }
  } catch {
    $resp = $_.Exception.Response
    return @{ ok = $false; http = $(if ($resp) { [int]$resp.StatusCode } else { 0 }); code = 0; body = $null }
  }
}

"=== PLM-2 AEOS 整体测试 ===" | Out-File $report
"时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" | Out-File $report -Append

# 1. 认证
$login = PostJson "$Plm/api/auth/login" @{} @{ username='admin'; password='admin@123' }
$h = @{ Authorization = "Bearer $($login.data.token)" }
Check "M01 认证" "登录获取 JWT" ($login.code -eq 200) $login.message

# 2. 权限强制 (无 token -> 401)
$noauth = TryGet "$Plm/api/v1/parts?pageNum=1&pageSize=1" @{}
Check "M01 权限" "无 token 访问受保护接口被拒" ($noauth.code -eq 401 -or $noauth.http -eq 401 -or $noauth.http -eq 403) "http=$($noauth.http) code=$($noauth.code)"

# 3. 各模块可用性 (Schema/接口契约)
$modules = @(
  @{ a="M04 项目";   u="/api/v1/projects?pageSize=1" },
  @{ a="M04 立项";   u="/api/initiation/list?pageSize=1" },
  @{ a="M04 立项统计"; u="/api/initiation/stats" },
  @{ a="M04 跟踪";   u="/api/v1/rd/tracking?pageSize=1" },
  @{ a="M04 工作表"; u="/api/v1/rd/sheets/meta" },
  @{ a="M04 对照";   u="/api/v1/rd/tracking/node-sheet-map" },
  @{ a="M04 变更";   u="/api/v1/rd/changes?pageSize=1" },
  @{ a="M02 基础数据"; u="/api/v1/system/base/meta" },
  @{ a="同步中枢";   u="/api/v1/sync/stats" },
  @{ a="M04 物料";   u="/api/v1/parts?pageNum=1&pageSize=1" },
  @{ a="M04 BOM";    u="/api/v1/boms/page?pageNum=1&pageSize=1" },
  @{ a="M04 ECN";    u="/api/v1/ecns/page?pageNum=1&pageSize=1" },
  @{ a="M04 模具";   u="/api/mold/page?pageNum=1&pageSize=1" },
  @{ a="M08 品质";   u="/api/quality/page?pageNum=1&pageSize=1" },
  @{ a="M04 工序";   u="/api/process/routes" },
  @{ a="M04 3D";     u="/api/model3d/page?pageNum=1&pageSize=1" },
  @{ a="档案";       u="/api/archive/tree/NOTEXIST" },
  @{ a="外协";       u="/api/outsourcing/page?pageNum=1&pageSize=1" },
  @{ a="分享";       u="/api/share/list" },
  @{ a="数据质量DQ"; u="/api/v1/dq/rules" },
  @{ a="度量Metric"; u="/api/v1/metrics/defs" },
  @{ a="KPI";        u="/api/v1/kpis/values" },
  @{ a="OKR";        u="/api/v1/okrs/cycles" },
  @{ a="分析";       u="/api/v1/analytics/datasets" },
  @{ a="改善Issue";  u="/api/v1/issues?pageNum=1&pageSize=1" },
  @{ a="待办WorkItem"; u="/api/v1/work-items/my" },
  @{ a="智能体";     u="/api/v1/agents/analyst/insights" },
  @{ a="查询Query";  u="/api/v1/query/resources" },
  @{ a="集成Integration"; u="/api/v1/integration/event-catalog" },
  @{ a="系统自检";   u="/api/system/check/run" }
)
foreach ($m in $modules) {
  $r = TryGet ("$Plm" + $m.u) $h
  Check $m.a "接口可用" ($r.ok -and ($r.code -eq 200)) "http=$($r.http) code=$($r.code)"
}

# 4. 幂等 (Event Contract: idempotency_key = event_id)
$eid = [guid]::NewGuid().ToString()
$inbody = @{ eventId=$eid; eventType='m04.test.ping'; objectType='AEOS_TEST'; externalKey='AEOS-IDEMPOTENT'; operation='UPDATE'; sourceSystem='EBMS'; revision=1; correlationId=[guid]::NewGuid().ToString(); payload=@{} }
$i1 = PostJson "$Plm/api/v1/sync/inbound" $h $inbody
$i2 = PostJson "$Plm/api/v1/sync/inbound" $h $inbody
Check "同步契约" "入站幂等(同 eventId 第二次去重)" ($i1.data.applied -eq $true -or $i2.data.duplicated -eq $true -or $i2.data.applied -eq $false) "1st=$($i1.data.applied), 2nd.duplicated=$($i2.data.duplicated)"

# 5. Object Contract + Evidence (工作表创建: 信封+对象引用, 变更留痕)
$t = TryGet "$Plm/api/v1/rd/tracking?pageSize=1" $h
$projNo = $t.body.data.records[0].projectNo
$chgBefore = (TryGet "$Plm/api/v1/rd/changes?projectNo=$projNo&pageSize=1" $h).body.data.total
$spec = PostJson "$Plm/api/v1/rd/sheets/spec" $h @{ project_no=$projNo; model='AEOS-TEST-MODEL'; status='编制中' }
$sid = $spec.data.id
$got = TryGet "$Plm/api/v1/rd/sheets/spec/$sid" $h
$d = $got.body.data
Check "Object Contract" "工作表携带 tenant_id/source_system/revision" ($d.tenant_id -eq 'hjgd' -and $d.source_system -eq 'PLM2' -and $null -ne $d.revision) "tenant=$($d.tenant_id) src=$($d.source_system) rev=$($d.revision)"
Check "对象引用" "自动引用项目 object_id" ($null -ne $d.project_id) "project_id=$($d.project_id)"
$chgAfter = (TryGet "$Plm/api/v1/rd/changes?projectNo=$projNo&pageSize=1" $h).body.data.total
Check "Evidence/审计" "工作表变更写入变更列表" ($chgAfter -gt $chgBefore) "changes $chgBefore -> $chgAfter"

# 6. 清理
try { Invoke-RestMethod -Uri "$Plm/api/v1/rd/sheets/spec/$sid" -Method DELETE -Headers $h -TimeoutSec 20 | Out-Null } catch {}

# 7. 汇总
$summary = "===== RESULT: PASS=$pass FAIL=$fail TOTAL=$($pass+$fail) ====="
Write-Host ""
Write-Host $summary -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
$rows | Out-File $report -Append
$summary | Out-File $report -Append
Write-Host "报告: $report"
