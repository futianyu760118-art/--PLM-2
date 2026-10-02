# 智能体自治等级 L0-L4 评估测试 (遵循 AEOS autonomy_level / Guard / Evidence)
# L0 只读建议  L1 只读工具执行(真实取数)  L2 写草稿/开单(需确认)  L3 白名单写入/自动修复  L4 自主多步编排+决策+证据
# 运行: powershell -ExecutionPolicy Bypass -File scripts\test-agent-l4.ps1
$ErrorActionPreference = "Continue"
$Plm = "http://localhost"
$report = "C:\Users\colorful\AppData\Local\Temp\opencode\agent-l4-report.txt"
function PostJson($url, $headers, $obj) {
  $bytes = [System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Depth 8 -Compress))
  return Invoke-RestMethod -Uri $url -Method POST -Headers $headers -Body $bytes -ContentType "application/json; charset=utf-8" -TimeoutSec 30
}
function Ask($h, $agent, $content) {
  $s = PostJson "$Plm/api/v1/agents/$agent/sessions" $h @{}
  $r = PostJson "$Plm/api/v1/agents/sessions/$($s.data.id)/messages" $h @{ content=$content }
  return $r.data
}

$login = PostJson "$Plm/api/auth/login" @{} @{ username='admin'; password='admin@123' }
$h = @{ Authorization = "Bearer $($login.data.token)" }
$defs = (Invoke-RestMethod "$Plm/api/v1/agents/defs" -Headers $h).data
$actBefore = (Invoke-RestMethod "$Plm/api/v1/agents/actions?limit=100" -Headers $h).data.Count

# 取样: 一个真实料号 / 项目号
$partNo = $null
try { $p = (Invoke-RestMethod "$Plm/api/v1/parts?pageNum=1&pageSize=1" -Headers $h).data; if ($p.total -gt 0) { $partNo = $p.records[0].partNo } } catch {}
$projNo = $null
try { $t = (Invoke-RestMethod "$Plm/api/v1/rd/tracking?pageSize=1" -Headers $h).data; if ($t.total -gt 0) { $projNo = $t.records[0].projectNo } } catch {}

$lines = @()
$lines += "== 智能体自治等级 L0-L4 评估 =="
$lines += "时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')  样料号=$partNo  样项目=$projNo"
$lines += ""

$summary = @()
foreach ($d in $defs) {
  $act0 = (Invoke-RestMethod "$Plm/api/v1/agents/actions?limit=100" -Headers $h).data.Count
  $code = $d.code
  $L0=$false; $L1=$false; $L2=$false; $L3=$false; $L4=$false
  $note = @()

  # L0: 能响应
  $r0 = Ask $h $code "你好"
  $L0 = ($r0.assistantMessage.content -ne $null -and $r0.assistantMessage.content.Length -gt 0)
  if (-not $L0) { $note += "L0无响应" }

  # L1: 只读工具真实取数 (按智能体选择探针)
  switch ($code) {
    "AGENT_QA"      { $c1 = "check $partNo" }
    "AGENT_ECN"     { $c1 = "ecn" }
    "AGENT_ANALYST" { $c1 = "分析" }
    "AGENT_COACH"   { $c1 = "改善" }
    "AGENT_GATE"    { $c1 = "项目 $projNo" }
    "AGENT_BOM"     { $c1 = "料号 $partNo" }
    "AGENT_CODER"   { $c1 = "预览编号 PART" }
    default         { $c1 = "帮助" }
  }
  $r1 = Ask $h $code $c1
  $txt1 = [string]$r1.assistantMessage.content
  $L1 = $txt1 -match '\d'
  if (-not $L1) { $note += "L1无数据" }

  # L2: 写草稿/开单(应产生 PROPOSED 动作或草稿), 需人确认
  if ($partNo) { $c2 = "请为料号 $partNo 创建改善单" } else { $c2 = "请创建一张改善单" }
  $r2 = Ask $h $code $c2
  $actAfter2 = (Invoke-RestMethod "$Plm/api/v1/agents/actions?limit=100" -Headers $h).data.Count
  $L2 = ($null -ne $r2.pendingAction) -or ($actAfter2 -gt $act0)
  if (-not $L2) { $note += "L2不支持开单" }

  # L3: 白名单写入/自动修复(应可直接改动正式数据) —— 探针: 请求作废料号, 校验状态是否被改
  $L3 = $false
  if ($partNo) {
    $before = (Invoke-RestMethod "$Plm/api/v1/parts/$partNo" -Headers $h -ErrorAction SilentlyContinue)
    $r3 = Ask $h $code "把料号 $partNo 作废"
    $after = (Invoke-RestMethod "$Plm/api/v1/parts/$partNo" -Headers $h -ErrorAction SilentlyContinue)
    if ($before -and $after -and ($after.data.status -ne $before.data.status)) { $L3 = $true }
    else { $note += "L3无白名单写入" }
  } else { $note += "L3无样本料号" }

  # L4: 自主多步编排 + 决策 + 证据(单次输入应产出多步结果+建议+证据/动作)
  $c4 = if ($partNo) { "检查料号 $partNo 并给出改善建议、责任人和证据" } else { "给出质量改善建议与证据" }
  $r4 = Ask $h $code $c4
  $txt4 = [string]$r4.assistantMessage.content
  $L4 = ($null -ne $r4.pendingAction) -and ($txt4 -match '质量分')
  if (-not $L4) { $note += "L4未达自主编排" }

  $level = if ($L4) { "L4" } elseif ($L3) { "L3" } elseif ($L2) { "L2" } elseif ($L1) { "L1" } elseif ($L0) { "L0" } else { "—" }
  $line = "{0,-14} 当前={1}  L0={2} L1={3} L2={4} L3={5} L4={6}  {7}" -f $code, $level, $L0, $L1, $L2, $L3, $L4, ($note -join ';')
  $lines += $line
  Write-Host $line
  $summary += [pscustomobject]@{ code=$code; level=$level }
}

$lines += ""
$l4count = ($summary | ? { $_.level -eq 'L4' }).Count
$lines += ("== 结果: 智能体共 {0}, 达到 L4 的 {1} ==" -f $summary.Count, $l4count)
Write-Host ""
Write-Host ("== L4 达标: {0}/{1} ==" -f $l4count, $summary.Count) -ForegroundColor $(if ($l4count -eq $summary.Count) { 'Green' } else { 'Yellow' })
$lines | Out-File $report -Encoding UTF8
Write-Host "报告: $report"
