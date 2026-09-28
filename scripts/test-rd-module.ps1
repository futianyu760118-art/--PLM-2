# Test today's RD module: base data, worksheets, node-sheet map, rollup auto-sync, change list
# Usage: powershell -ExecutionPolicy Bypass -File scripts\test-rd-module.ps1
$ErrorActionPreference = "Stop"
$Plm = "http://localhost"
$pass = 0; $fail = 0
function Check($name, $cond, $detail) {
  if ($cond) { $script:pass++; Write-Host ("[PASS] {0}  {1}" -f $name, $detail) -ForegroundColor Green }
  else { $script:fail++; Write-Host ("[FAIL] {0}  {1}" -f $name, $detail) -ForegroundColor Red }
}
function PostJson($url, $headers, $obj) {
  $bytes = [System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Depth 6 -Compress))
  return Invoke-RestMethod -Uri $url -Method POST -Headers $headers -ContentType "application/json; charset=utf-8" -Body $bytes -TimeoutSec 30
}

# 中文(字符码, 避免脚本编码问题)
$KAIMO   = -join @(0x5F00,0x6A21,0x8BC4,0x5BA1 | ForEach-Object { [char]$_ })  # 开模评审
$TONGGUO = -join @(0x901A,0x8FC7 | ForEach-Object { [char]$_ })                 # 通过

Write-Host "=== 1. login ===" -ForegroundColor Cyan
$login = PostJson "$Plm/api/auth/login" @{} @{ username='admin'; password='admin@123' }
$h = @{ Authorization = "Bearer $($login.data.token)"; 'Content-Type' = 'application/json; charset=utf-8' }
Check "登录" ($login.code -eq 200) "token ok"

Write-Host "`n=== 2. 服务可用性 ===" -ForegroundColor Cyan
try { $fr = Invoke-WebRequest "$Plm/" -UseBasicParsing -TimeoutSec 10; Check "前端首页" ($fr.StatusCode -eq 200) "HTTP $($fr.StatusCode)" } catch { Check "前端首页" $false $_.Exception.Message }

Write-Host "`n=== 3. 基础数据 (系统管理) ===" -ForegroundColor Cyan
$bm = Invoke-RestMethod "$Plm/api/v1/system/base/meta" -Headers $h -TimeoutSec 20
Check "基础表数量=6" ($bm.data.Count -eq 6) (($bm.data | ForEach-Object { $_.type }) -join ',')
$cust = PostJson "$Plm/api/v1/system/base/customer" $h @{ code='TEST-CUST'; name='TEST-CUST'; type='国内客户'; level='A' }
$cl = Invoke-RestMethod "$Plm/api/v1/system/base/customer" -Headers $h -TimeoutSec 20
Check "基础数据新增+列表" ($cl.data.total -ge 1) "customer total=$($cl.data.total)"
Invoke-RestMethod "$Plm/api/v1/system/base/customer/$($cl.data.records[0].id)" -Method DELETE -Headers $h -TimeoutSec 20 | Out-Null

Write-Host "`n=== 4. 项目工作表 ===" -ForegroundColor Cyan
$sm = Invoke-RestMethod "$Plm/api/v1/rd/sheets/meta" -Headers $h -TimeoutSec 20
$types = ($sm.data | ForEach-Object { $_.type }) -join ','
Check "工作表类型=9(含plan/basebom)" ($sm.data.Count -eq 9 -and $types.Contains('plan') -and $types.Contains('basebom')) $types

Write-Host "`n=== 5. 节点↔工作表对照 ===" -ForegroundColor Cyan
$map = Invoke-RestMethod "$Plm/api/v1/rd/tracking/node-sheet-map" -Headers $h -TimeoutSec 20
Check "对照表=22节点" ($map.data.Count -eq 22) "rows=$($map.data.Count)"

Write-Host "`n=== 6. 自动同步引用 + 变更列表 ===" -ForegroundColor Cyan
$projId = 298
$projNo = 'HJ.25015.R1.689'
$chgBefore = (Invoke-RestMethod "$Plm/api/v1/rd/changes?projectNo=$projNo&pageSize=1" -Headers $h -TimeoutSec 20).data.total
# 重置节点
Invoke-RestMethod "$Plm/api/project/$projId/nodes/MOLD_REVIEW" -Method PUT -Headers $h -Body ([System.Text.Encoding]::UTF8.GetBytes('{"status":"NOT_SET","remark":""}')) -TimeoutSec 20 | Out-Null
Write-Host ("  测试项目: {0} (id={1})" -f $projNo, $projId)

# 新建评审单(通过) -> 自动同步引用(汇总节点=完成) + 变更留痕(CREATE + ROLLUP)
PostJson "$Plm/api/v1/rd/sheets/review" $h @{ project_no=$projNo; review_no='PS-TEST-A'; review_type=$KAIMO; conclusion=$TONGGUO; review_date='2026-10-01' } | Out-Null
Start-Sleep -Milliseconds 500
$chgAfter = (Invoke-RestMethod "$Plm/api/v1/rd/changes?projectNo=$projNo&pageSize=1" -Headers $h -TimeoutSec 20).data.total
$delta = $chgAfter - $chgBefore
Check "工作表变更自动同步+变更留痕(增量>=2: CREATE+ROLLUP)" ($delta -ge 2) ("changes {0} -> {1} (delta={2})" -f $chgBefore, $chgAfter, $delta)
$cnt = (Invoke-RestMethod "$Plm/api/v1/rd/sheets/review?projectNo=$projNo" -Headers $h -TimeoutSec 20).data.total
Check "评审单已入库" ($cnt -ge 1) ("review rows={0}" -f $cnt)

Write-Host "`n=== 7. 清理测试数据 ===" -ForegroundColor Cyan
$rl = Invoke-RestMethod "$Plm/api/v1/rd/sheets/review?projectNo=$projNo" -Headers $h -TimeoutSec 20
foreach ($r in $rl.data.records) { Invoke-RestMethod "$Plm/api/v1/rd/sheets/review/$($r.id)" -Method DELETE -Headers $h -TimeoutSec 20 | Out-Null }
Invoke-RestMethod "$Plm/api/project/$projId/nodes/MOLD_REVIEW" -Method PUT -Headers $h -Body ([System.Text.Encoding]::UTF8.GetBytes('{"status":"NOT_SET","remark":""}')) -TimeoutSec 20 | Out-Null
Write-Host "  cleanup done"

Write-Host ""
Write-Host ("===== RESULT: PASS={0} FAIL={1} =====" -f $pass, $fail) -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
