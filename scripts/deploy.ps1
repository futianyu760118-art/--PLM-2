# PLM deploy script - backs up DB before deploying (prevents data loss)
# Usage:
#   powershell -File scripts\deploy.ps1                    # backup then deploy backend+frontend
#   powershell -File scripts\deploy.ps1 -Service backend
#   powershell -File scripts\deploy.ps1 -Service frontend
#   powershell -File scripts\deploy.ps1 -SkipBackup         # skip backup (dangerous)
# Safety: always uses --no-deps, never recreates postgres/redis.
param(
    [ValidateSet("backend","frontend","both")]
    [string]$Service = "both",
    [switch]$SkipBackup
)

$ErrorActionPreference = "Continue"
$root = "D:\PLM-2"

if (-not $SkipBackup) {
    Write-Host "==== [deploy] backup DB before deploy ===="
    & powershell -NoProfile -ExecutionPolicy Bypass -File "$root\scripts\backup-db.ps1"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[deploy] backup failed, aborting deploy"
        exit 1
    }
} else {
    Write-Warning "[deploy] backup skipped (dangerous)"
}

Push-Location $root
try {
    if ($Service -eq "backend" -or $Service -eq "both") {
        Write-Host "==== [deploy] build backend image ===="
        docker build --no-cache -t plm-2-backend:latest -f plm-backend\Dockerfile plm-backend
        if ($LASTEXITCODE -ne 0) { Write-Error "[deploy] backend build failed"; exit 1 }
        Write-Host "==== [deploy] deploy backend (--no-deps) ===="
        docker compose up -d --no-build --no-deps backend
        docker restart plm2-backend
    }

    if ($Service -eq "frontend" -or $Service -eq "both") {
        Write-Host "==== [deploy] build frontend ===="
        Push-Location "$root\plm-frontend"
        npm run build
        $buildCode = $LASTEXITCODE
        Pop-Location
        if ($buildCode -ne 0) { Write-Error "[deploy] frontend build failed"; exit 1 }
        docker build -t plm-2-frontend:latest -f plm-frontend\Dockerfile plm-frontend
        if ($LASTEXITCODE -ne 0) { Write-Error "[deploy] frontend image build failed"; exit 1 }
        Write-Host "==== [deploy] deploy frontend (--no-deps) ===="
        docker compose up -d --no-build --no-deps frontend
        docker restart plm2-frontend
    }
}
finally {
    Pop-Location
}

Write-Host "==== [deploy] done ===="
