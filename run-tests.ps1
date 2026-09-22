#!/usr/bin/env powershell
<#
.SYNOPSIS
  PLM V4.0 统一测试运行器 + 自动修复检测
.DESCRIPTION
  1. 运行后端 Java 单元测试
  2. 运行 Python 算法服务测试
  3. 运行前端 Vitest 测试
  4. 执行自动修复检测 (扫描常见问题)
  5. 汇总报告
#>

$ROOT = "D:\PLM-2"
$JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
$env:JAVA_HOME = $JAVA_HOME
$env:PATH = "$JAVA_HOME\bin;C:\apache-maven-3.9.6\bin;$env:PATH"

$results = @()
$totalPass = 0
$totalFail = 0

function Add-Result($name, $status, $detail) {
    $icon = if ($status -eq 'PASS') { '[PASS]' } elseif ($status -eq 'FAIL') { '[FAIL]' } else { '[SKIP]' }
    $color = if ($status -eq 'PASS') { 'Green' } elseif ($status -eq 'FAIL') { 'Red' } else { 'Yellow' }
    Write-Host "$icon $name" -ForegroundColor $color
    if ($detail) { Write-Host "     $detail" -ForegroundColor DarkGray }
    $script:results += [PSCustomObject]@{ Module=$name; Status=$status; Detail=$detail }
    if ($status -eq 'PASS') { $script:totalPass++ }
    elseif ($status -eq 'FAIL') { $script:totalFail++ }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  PLM V4.0 自动化测试 + 修复检测" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# ========== 1. 后端 Java 测试 ==========
Write-Host "--- [1/4] 后端 Java 单元测试 ---" -ForegroundColor Yellow
$backendTestDir = "$ROOT\plm-backend"
if (Test-Path "$backendTestDir\pom.xml") {
    Push-Location $backendTestDir
    # 必须 clean: 增量编译会复用未失效的 .class，掩盖存量编译错误(如 Step4 WIP)
    $mvnOutput = & mvn clean test -Dsurefire.useFile=false 2>&1
    $mvnExit = $LASTEXITCODE
    Pop-Location
    
    $testRun = ($mvnOutput | Select-String "Tests run:" | Select-Object -Last 1).ToString()
    if ($mvnExit -eq 0) {
        Add-Result "后端 Java 测试" "PASS" $testRun
    } else {
        $failLine = ($mvnOutput | Select-String "Tests run:.*Failures: [1-9]|FAILURE|ERROR.*Test" | Select-Object -First 3) -join "; "
        Add-Result "后端 Java 测试" "FAIL" $failLine
    }
} else {
    Add-Result "后端 Java 测试" "SKIP" "pom.xml 不存在"
}

Write-Host ""

# ========== 2. Python 测试 ==========
Write-Host "--- [2/4] Python 算法服务测试 ---" -ForegroundColor Yellow
$algoDir = "$ROOT\plm-algorithm"
if (Test-Path "$algoDir\tests") {
    Push-Location $algoDir
    $pyOutput = python -m pytest tests/ -v --tb=short 2>&1
    $pyExit = $LASTEXITCODE
    Pop-Location
    
    if ($pyExit -eq 0) {
        $passLine = ($pyOutput | Select-String "passed" | Select-Object -Last 1).ToString()
        Add-Result "Python 算法测试" "PASS" $passLine
    } else {
        $failLine = ($pyOutput | Select-String "FAILED|ERROR" | Select-Object -First 3) -join "; "
        Add-Result "Python 算法测试" "FAIL" $failLine
    }
} else {
    Add-Result "Python 算法测试" "SKIP" "tests/ 不存在"
}

Write-Host ""

# ========== 3. 前端 Vitest ==========
Write-Host "--- [3/4] 前端 Vitest 测试 ---" -ForegroundColor Yellow
$frontendDir = "$ROOT\plm-frontend"
if (Test-Path "$frontendDir\node_modules\.bin\vitest") {
    Push-Location $frontendDir
    $feOutput = npx vitest run 2>&1
    $feExit = $LASTEXITCODE
    Pop-Location
    
    if ($feExit -eq 0) {
        $passLine = ($feOutput | Select-String "passed" | Select-Object -Last 1).ToString()
        Add-Result "前端 Vitest" "PASS" $passLine
    } else {
        $failLine = ($feOutput | Select-String "FAIL|Error" | Select-Object -First 3) -join "; "
        Add-Result "前端 Vitest" "FAIL" $failLine
    }
} else {
    Add-Result "前端 Vitest" "SKIP" "vitest 未安装 (需 npm install)"
}

Write-Host ""

# ========== 4. 自动修复检测 ==========
Write-Host "--- [4/4] 自动修复检测 (扫描已知问题模式) ---" -ForegroundColor Yellow

# 检测 4.1: @EnumValue 误用在 String 字段
$enumValueIssues = @()
Get-ChildItem "$ROOT\plm-backend\src\main\java" -Recurse -Filter "*.java" | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    $matches = [regex]::Matches($content, '@EnumValue\s*\n\s*private\s+String\s+(\w+)')
    foreach ($m in $matches) {
        $enumValueIssues += "$($_.Name): $($m.Groups[1].Value)"
    }
}
if ($enumValueIssues.Count -eq 0) {
    Add-Result "检测: @EnumValue 误用" "PASS" "未发现 String 字段上的 @EnumValue"
} else {
    Add-Result "检测: @EnumValue 误用" "FAIL" ($enumValueIssues -join ", ")
}

# 检测 4.2: 重复 import
$dupImportIssues = @()
Get-ChildItem "$ROOT\plm-backend\src\main\java" -Recurse -Filter "*.java" | ForEach-Object {
    $lines = Get-Content $_.FullName
    $imports = $lines | Where-Object { $_ -match '^import ' } | Sort-Object
    $dups = $imports | Group-Object | Where-Object { $_.Count -gt 1 }
    if ($dups) {
        $dupImportIssues += "$($_.Name): 重复 $($dups.Count) 处"
    }
}
if ($dupImportIssues.Count -eq 0) {
    Add-Result "检测: 重复 import" "PASS" "无重复导入"
} else {
    Add-Result "检测: 重复 import" "FAIL" ($dupImportIssues -join ", ")
}

# 检测 4.3: BaseEntity 子类缺少 deleted 列 (检查数据库)
$env:PGPASSWORD = "postgres"
$pgBin = "C:\Program Files\PostgreSQL\15\bin"
if (Test-Path $pgBin) {
    try {
        $missingTables = @()
        $tables = @("sys_user","sys_role","plm_material","plm_bom","plm_ecn")
        foreach ($t in $tables) {
            $hasDeleted = & "$pgBin\psql.exe" -U postgres -h localhost -d plm_v4 -t -c "
                SELECT column_name FROM information_schema.columns 
                WHERE table_name='$t' AND column_name='deleted';" 2>$null
            if ([string]::IsNullOrWhiteSpace($hasDeleted)) {
                $missingTables += $t
            }
        }
        if ($missingTables.Count -eq 0) {
            Add-Result "检测: deleted 列完整性" "PASS" "所有 BaseEntity 表均有 deleted 列"
        } else {
            Add-Result "检测: deleted 列完整性" "FAIL" "缺失: $($missingTables -join ', ')"
        }
    } catch {
        Add-Result "检测: deleted 列完整性" "SKIP" "无法连接数据库"
    }
} else {
    Add-Result "检测: deleted 列完整性" "SKIP" "PostgreSQL 未安装"
}

# 检测 4.4: 前端路由参数使用 props: true (已知 bug 模式)
$propsIssues = @()
Get-ChildItem "$ROOT\plm-frontend\src" -Recurse -Filter "*.vue" | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    if ($content -match 'defineProps.*bomId' -and $content -match 'props\.bomId') {
        $propsIssues += "$($_.Name): 使用 props.bomId (应改用 useRoute)"
    }
}
if ($propsIssues.Count -eq 0) {
    Add-Result "检测: 路由参数安全" "PASS" "未发现 props 路由参数风险"
} else {
    Add-Result "检测: 路由参数安全" "FAIL" ($propsIssues -join ", ")
}

# 检测 4.5: Python 算法服务端口占用
$port8001 = netstat -ano | Select-String ":8001.*LISTENING"
if ($port8001) {
    Add-Result "检测: 算法服务端口" "PASS" "8001 端口有服务监听"
} else {
    Add-Result "检测: 算法服务端口" "FAIL" "8001 端口无服务 (需启动 Python)"
}

Write-Host ""

# ========== 汇总报告 ==========
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  测试汇总报告" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
$results | Format-Table -AutoSize
Write-Host ""
$color = if ($totalFail -eq 0) { 'Green' } else { 'Red' }
Write-Host "通过: $totalPass | 失败: $totalFail | 总计: $($results.Count)" -ForegroundColor $color
Write-Host ""

if ($totalFail -gt 0) {
    Write-Host "[!] 发现 $totalFail 个问题, 请检查上述 FAIL 项" -ForegroundColor Red
    exit 1
} else {
    Write-Host "[v] 全部测试通过!" -ForegroundColor Green
    exit 0
}
