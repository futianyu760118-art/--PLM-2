@echo off
chcp 65001 >nul
title PLM V4.0 Docker Deployment

echo ========================================
echo  恒剑光电 PLM V4.0 Docker 部署
echo ========================================
echo.

echo [1/5] Checking Docker...
where docker >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Docker not found. Please install Docker Desktop first.
    pause
    exit /b 1
)
echo Docker found: 
docker --version
echo.

echo [2/5] Checking Docker Compose...
docker compose version >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo Using docker compose plugin
    set COMPOSE_CMD=docker compose
) else (
    docker-compose --version >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        echo Using docker-compose
        set COMPOSE_CMD=docker-compose
    ) else (
        echo [ERROR] Docker Compose not found.
        pause
        exit /b 1
    )
)
echo.

echo [3/5] Building and Starting all services...
echo This will take 5-15 minutes on first run.
echo Building: Backend (Java+Maven) + Frontend (Node+Npm+Vite+Nginx) + Algorithm (Python)
echo.
%COMPOSE_CMD% build 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed. Check errors above.
    pause
    exit /b 1
)
echo All images built successfully.
echo.

echo [4/5] Starting services...
%COMPOSE_CMD% up -d
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to start services.
    pause
    exit /b 1
)
echo.

echo [5/5] Waiting for services to be ready...
timeout /t 20 /nobreak >nul

echo.
echo ========================================
echo  Deployment Complete!
echo ========================================
echo.
echo  Frontend:   http://localhost:80
echo  Backend:    http://localhost:8080/api
echo  Algorithm:  http://localhost:8001
echo  PostgreSQL: localhost:5432
echo  Redis:      localhost:6379
echo.
echo  Login:      admin / admin@123
echo.
echo  To stop:    %COMPOSE_CMD% down
echo  To logs:    %COMPOSE_CMD% logs -f
echo  To rebuild: %COMPOSE_CMD% build --no-cache
echo.
pause
