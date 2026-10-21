# =============================================================================
# PLM 数据库备份脚本
# 用法: powershell -ExecutionPolicy Bypass -File scripts\backup-db.ps1
# 功能: pg_dump 备份 plm_v4，保存到 database/backups/，默认保留最近 14 份
# =============================================================================
param([int]$Keep = 14)

$BACKUP_DIR = "D:\PLM-2\database\backups"
$CONTAINER = "plm-postgres"
$DB = "plm_v4"
$USER = "plm"
$TS = Get-Date -Format "yyyyMMdd_HHmmss"
$TMP = "/tmp/plm_backup_$TS.dump"
$OUT = Join-Path $BACKUP_DIR "plm_v4_$TS.dump"

if (-not (Test-Path $BACKUP_DIR)) { New-Item -ItemType Directory -Path $BACKUP_DIR | Out-Null }
Write-Host "[backup] start: $DB -> $OUT"

docker exec $CONTAINER pg_dump -U $USER -d $DB -Fc -f $TMP | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Error "[backup] pg_dump failed (code $LASTEXITCODE)"; exit 1 }

docker cp "${CONTAINER}:${TMP}" $OUT | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Error "[backup] docker cp failed (code $LASTEXITCODE)"; exit 1 }

docker exec $CONTAINER rm -f $TMP | Out-Null

if (-not (Test-Path $OUT)) { Write-Error "[backup] file not created"; exit 1 }
$sz = (Get-Item $OUT).Length
if ($sz -lt 1024) { Write-Error "[backup] file too small: $sz bytes"; exit 1 }
$szMB = [math]::Round($sz / 1048576, 2)
Write-Host "[backup] ok: $OUT ($szMB MB)"

$files = @(Get-ChildItem $BACKUP_DIR -Filter "plm_v4_*.dump" | Sort-Object LastWriteTime -Descending)
if ($files.Count -gt $Keep) {
    $files | Select-Object -Skip $Keep | ForEach-Object {
        Write-Host "[backup] remove old: $($_.Name)"
        Remove-Item $_.FullName -Force
    }
}
Write-Host "[backup] done"
