# PLM AI feedback runner auto-start (idempotent + single-instance).
# Launched by scheduled task "PLM-Feedback-Runner" at logon and every 30 min.
$ErrorActionPreference = "SilentlyContinue"

$runnerDir = "D:\PLM-2\scripts\ai-feedback-runner"

# Single-instance: exit quietly if a runner already holds the lock.
if (Test-Path (Join-Path $runnerDir ".runner.lock")) {
    exit 0
}

# Ensure local config exists.
if (-not (Test-Path (Join-Path $runnerDir "config.local.json"))) {
    Copy-Item (Join-Path $runnerDir "config.example.json") (Join-Path $runnerDir "config.local.json") -Force
}

# Ensure the required toolchain is on PATH (scheduled tasks may lack the user PATH).
$env:Path = "C:\apache-maven-3.9.6\bin;D:\;C:\Users\colorful\AppData\Local\Programs\Python\Python311\Scripts;C:\Users\colorful\AppData\Local\Programs\Python\Python311;C:\Users\colorful\AppData\Roaming\npm;$env:Path"

# Launch the runner in the background with log redirection.
Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/c python runner.py > runner.log 2>&1" `
    -WorkingDirectory $runnerDir `
    -WindowStyle Hidden

exit 0
