# Sync EBMS R&D project module -> PLM-2 Sync Hub
# Usage: powershell -ExecutionPolicy Bypass -File scripts\sync-ebms-to-plm2.ps1
param(
  [string]$EbmsBase = "http://localhost:3010",
  [string]$EbmsUser = "admin",
  [string]$EbmsPass = "admin123",
  [string]$PlmBase  = "http://localhost",
  [string]$PlmUser  = "admin",
  [string]$PlmPass  = "admin@123"
)

$ErrorActionPreference = "Stop"

function Get-JsonUtf8($url, $headers) {
  $wr = Invoke-WebRequest -Uri $url -Headers $headers -UseBasicParsing -TimeoutSec 120
  $raw = $wr.RawContentStream
  if ($null -ne $raw) {
    $raw.Position = 0
    $ms = New-Object System.IO.MemoryStream
    $raw.CopyTo($ms)
    $bytes = $ms.ToArray()
  } else {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes([string]$wr.Content)
  }
  $text = [System.Text.Encoding]::UTF8.GetString($bytes)
  return ($text | ConvertFrom-Json)
}

function Invoke-JsonPost($url, $headers, $obj) {
  $json = $obj | ConvertTo-Json -Depth 8 -Compress
  $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
  $resp = Invoke-RestMethod -Uri $url -Method POST -Headers $headers -ContentType "application/json; charset=utf-8" -Body $bytes -TimeoutSec 60
  if (($resp.PSObject.Properties.Name -contains 'code') -and ($resp.code -ne 200)) {
    throw ("PLM code={0} message={1}" -f $resp.code, $resp.message)
  }
  return $resp
}

Write-Host "[1/4] login EBMS ..."
$ebLogin = Invoke-JsonPost "$EbmsBase/api/users/login" @{} @{ username = $EbmsUser; password = $EbmsPass }
$eh = @{ Authorization = "Bearer $($ebLogin.token)" }

Write-Host "[2/4] login PLM-2 ..."
$plLogin = Invoke-JsonPost "$PlmBase/api/auth/login" @{} @{ username = $PlmUser; password = $PlmPass }
$ph = @{ Authorization = "Bearer $($plLogin.data.token)" }

Write-Host "[3/4] sync projects ..."
$projects = (Get-JsonUtf8 "$EbmsBase/api/projects?limit=9999" $eh).data
$idToNo = @{}
foreach ($p in $projects) { if ($p.id) { $idToNo[[string]$p.id] = $p.project_no } }

$projOk = 0; $nodeOk = 0; $fail = 0

foreach ($p in $projects) {
  if (-not $p.project_no) { continue }
  $payload = [ordered]@{
    project_name  = $p.project_name
    customer_name = $p.customer_name
    project_type  = $p.project_type
    project_level = $p.project_level
    owner         = $p.owner
    department    = $p.department
    start_date    = $p.start_date
    target_date   = $p.target_date
    close_date    = $p.close_date
    status        = $p.status
    urgency       = $p.urgency
    remarks       = $p.remarks
  }
  $body = [ordered]@{
    eventId       = [guid]::NewGuid().ToString()
    eventType     = "m04.project.updated"
    objectType    = "PROJECT"
    externalKey   = $p.project_no
    operation     = "UPDATE"
    sourceSystem  = "EBMS"
    revision      = 1
    correlationId = [guid]::NewGuid().ToString()
    payload       = $payload
  }
  try { Invoke-JsonPost "$PlmBase/api/v1/sync/inbound" $ph $body | Out-Null; $projOk++ }
  catch { $fail++; Write-Host ("  PROJECT fail {0}: {1}" -f $p.project_no, $_.Exception.Message) }
}
Write-Host ("  projects synced: {0}, fail: {1}" -f $projOk, $fail)

Write-Host "[4/4] sync project progress nodes ..."
$nodeKeys = @('plan','bom','spec','config','mold_drawing','mold_review','hand_sample','appearance','structure','electronics','mold','mold_sample','packaging','elec_trial','rd_trial','tech_transfer','eng_trial','prod_trial','test_report','shipment','review','other')
$progress = (Get-JsonUtf8 "$EbmsBase/api/projects/progress/list?limit=9999" $eh).data

foreach ($row in $progress) {
  $pno = $row.project_no
  if (-not $pno -and $row.project_id) { $pno = $idToNo[[string]$row.project_id] }
  if (-not $pno) { continue }
  foreach ($k in $nodeKeys) {
    $val = $row.$k
    if ($null -eq $val) { continue }
    $sv = [string]$val
    if ($sv.Trim() -eq '') { continue }
    $body = [ordered]@{
      eventId       = [guid]::NewGuid().ToString()
      eventType     = "m04.project_node.updated"
      objectType    = "PROJECT_NODE"
      externalKey   = ("{0}:{1}" -f $pno, $k.ToUpper())
      operation     = "UPDATE"
      sourceSystem  = "EBMS"
      revision      = 1
      correlationId = [guid]::NewGuid().ToString()
      payload       = @{ value = $sv }
    }
    try { Invoke-JsonPost "$PlmBase/api/v1/sync/inbound" $ph $body | Out-Null; $nodeOk++ }
    catch { $fail++; Write-Host ("  NODE fail {0}/{1}: {2}" -f $pno, $k, $_.Exception.Message) }
  }
}

Write-Host ""
Write-Host ("DONE. projects={0} nodes={1} fail={2}" -f $projOk, $nodeOk, $fail)
