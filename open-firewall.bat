@echo off
chcp 65001 >nul
title PLM-2 放行防火墙端口

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 请右键本文件 -> 以管理员身份运行
    pause
    exit /b 1
)

echo 放行 PLM-2 端口 (80 前端 / 8081 后端 / 8001 算法)...
netsh advfirewall firewall delete rule name="PLM-2 Web 80" >nul 2>&1
netsh advfirewall firewall delete rule name="PLM-2 Backend 8081" >nul 2>&1
netsh advfirewall firewall delete rule name="PLM-2 Algorithm 8001" >nul 2>&1
netsh advfirewall firewall add rule name="PLM-2 Web 80" dir=in action=allow protocol=TCP localport=80
netsh advfirewall firewall add rule name="PLM-2 Backend 8081" dir=in action=allow protocol=TCP localport=8081
netsh advfirewall firewall add rule name="PLM-2 Algorithm 8001" dir=in action=allow protocol=TCP localport=8001
echo.
echo [完成] 其他电脑现在可访问 http://本机IP/
pause
