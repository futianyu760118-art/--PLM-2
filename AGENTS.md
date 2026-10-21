# AGENTS.md — 项目约定与安全须知

## 部署铁律（防数据丢失）

1. **绝不要用 `docker compose up` 重建 postgres**。给 `docker-compose.yml` 的 postgres 服务增加/修改 bind mount 后，`docker compose up` 会重建 postgres 容器，历史上有过一次把 `plm-2_postgres_data` 数据卷重置成种子数据的严重事故（丢失 161 个项目）。

2. **部署后端/前端时只用**：
   ```powershell
   docker compose up -d --no-build --no-deps backend
   docker compose up -d --no-build --no-deps frontend
   ```
   `--no-deps` 保证不碰 postgres / redis。

3. **任何涉及 postgres / 数据操作的部署前，先备份**：
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\backup-db.ps1
   ```

4. 修改 `docker-compose.yml` 后，确认 postgres 服务的 `volumes`（`postgres_data:/var/lib/postgresql/data`）不被移除/改名，否则数据卷会脱离。

## 数据库备份

- 脚本：`scripts/backup-db.ps1`（`pg_dump -Fc` 到 `database/backups/plm_v4_<时间戳>.dump`，保留最近 14 份）。
- 已注册 Windows 计划任务 **`PLM-DB-Backup`**，每日 02:00 自动备份。
- 手动备份命令见上；恢复用 `pg_restore`。
- `database/backups/` 已加入 `.gitignore`，备份文件不进仓库。

## 编码安全

- **禁止用 PowerShell `Set-Content` / `Get-Content -Raw` 改写含中文的源码文件**（会写出 GBK/mojibake，破坏 UTF-8）。改文件用编辑工具（UTF-8 安全）。
- 误改后用 `git checkout -- <file>` 还原。

## 账号密码（本机环境，勿提交）

- PLM 后端：`admin / admin@123`（种子默认；数据库重置后恢复为该值）。
- EBMS（`ebms-hj`，端口 5000）：`admin / admin123`（PBKDF2-HMAC-SHA256，100000 轮）。
- DeepSeek 密钥在 `D:\PLM-2\.env`（已 gitignore），绝不可提交。

## 常见运维命令

- 后端测试：`C:\apache-maven-3.9.6\bin\mvn.cmd -o test`（在 `plm-backend` 下）。
- 前端构建：`npm run build`（在 `plm-frontend` 下）。
- 数据库连接：`docker exec plm-postgres psql -U plm -d plm_v4`。

## 已知数据缺口

- 上次数据重置后恢复了 151/161 个项目；6 个已补齐（HJ.26036~26041）。若再有项目缺漏，用 EBMS `rd_project_progress.json` 补。
- EBMS → PLM 同步接口：`POST /v1/integration/ebms-track-sync`。
