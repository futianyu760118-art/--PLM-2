# PLM-2 局域网部署说明（其他电脑可访问）

> 适用：把 PLM-2 部署在一台"服务器"电脑上，局域网内其他电脑通过浏览器访问。
> 技术栈：Docker Compose（前端 Nginx + 后端 Spring Boot + PostgreSQL + Redis + Python 算法）。

---

## 一、访问地址

| 项 | 地址 |
|----|------|
| 前端（给用户） | `http://<服务器IP>/` |
| 后端 API | `http://<服务器IP>:8081/api`（一般由 Nginx 代理，无需直接访问） |
| 算法服务 | `http://<服务器IP>:8001`（内部调用） |
| 默认账号 | `admin / admin@123` |

服务器 IP 查看（Windows）：`ipconfig`，找"IPv4 地址"（示例：`192.168.0.71`）。

登录示例：`http://192.168.0.71/`

---

## 二、架构与端口

```
其他电脑 ──HTTP:80──► plm2-frontend(Nginx) ──/api──► plm2-backend(8080) ──► plm-postgres / plm-redis
                                                        └──► plm-algorithm(8001)
```

| 容器 | 宿主机端口 | 说明 |
|------|-----------|------|
| plm2-frontend | 80 | 前端静态页 + 反代 `/api` 到后端 |
| plm2-backend | 8081 → 8080 | Spring Boot（上下文路径 `/api`） |
| plm-algorithm | 8001 | Python FastAPI 3D/AI |
| plm-postgres | 5432 | PostgreSQL 15 |
| plm-redis | 6379 | Redis 7 |

前端请求用相对路径 `/api`，经 Nginx 同源反代，**无跨域问题**。

---

## 三、首次部署（在服务器电脑上）

1. 安装 **Docker Desktop for Windows**（含 Docker Compose），启动并等待 Docker 就绪。
2. 获取代码（二选一）：
   - `git clone https://github.com/futianyu760118-art/--PLM-2.git`
   - 或解压项目到 `D:\PLM-2`
3. 在项目根目录执行：
   ```bat
   deploy-docker.bat
   ```
   或手动：
   ```bat
   docker compose up -d --build
   ```
   首次构建约 5–15 分钟。
4. **放行防火墙**（关键，否则其他电脑访问不了）：
   右键 `open-firewall.bat` → **以管理员身份运行**。
   或手动（管理员 CMD）：
   ```
   netsh advfirewall firewall add rule name="PLM-2 Web 80" dir=in action=allow protocol=TCP localport=80
   ```
5. 其他电脑浏览器打开 `http://<服务器IP>/`。

> 数据库初始化：PostgreSQL 首次以空数据卷启动时，会自动执行 `database/00~26_*.sql`。
> 若使用已存在的数据卷（升级场景），新脚本**不会自动执行**，需手动补执行（见第五节）。

---

## 四、日常运维

```bat
docker compose ps                    :: 查看状态
docker compose logs -f backend       :: 后端日志
docker compose logs -f frontend      :: 前端日志
docker compose restart backend       :: 重启
docker compose down                  :: 停止(保留数据卷)
docker compose down -v               :: 停止并删除数据(谨慎!)
```

数据持久化卷：`postgres_data`、`redis_data`、`plm_files`（上传文件）。

---

## 五、代码更新后重新部署

```bat
git pull
docker compose build backend frontend
docker compose up -d backend frontend
```

若本次更新含**新增数据库脚本**（如 `database/26_*.sql`），需对已存在的库补执行：

```bat
docker cp database\26_m04_sync_hub.sql plm-postgres:/tmp/26.sql
docker exec -e PGCLIENTENCODING=UTF8 plm-postgres psql -U plm -d plm_v4 -f /tmp/26.sql
```

---

## 六、不使用 Docker 的替代方式（开发/临时）

1. 仅用 Docker 起数据库与缓存：
   ```bat
   docker compose up -d postgres redis
   ```
2. 启动后端：
   ```bat
   cd plm-backend
   mvn spring-boot:run
   ```
3. 启动前端（Vite，已配置 `/api` 代理到 8080）：
   ```bat
   cd plm-frontend
   npm install
   npm run dev -- --host
   ```
   其他电脑访问 `http://<服务器IP>:9000/`（Vite 端口 9000）。
   注意：需放行 9000 端口，且后端 8080 被 Vite 代理（仅本机 8080 即可）。

---

## 七、常见问题

| 现象 | 排查 |
|------|------|
| 本机可访问，其他电脑打不开 | 防火墙未放行 80（运行 `open-firewall.bat`）；或服务器与访问端不在同一网段/VLAN；或公司网络做了端口隔离 |
| 打开页面但接口 502/失败 | 后端未就绪或异常：`docker compose logs backend` |
| 登录后跳回登录页 | 后端 JWT/数据库连接问题，看后端日志 |
| 新增功能表缺失 | 已存在数据卷导致新脚本未执行，按第五节手动补执行 |
| 端口 80 被占用 | 修改 `docker-compose.yml` 中 frontend 的 `"80:80"` 为其他端口（如 `"8888:80"`），访问改为 `http://<IP>:8888/` |

---

## 八、安全提示（生产建议）

- 修改默认密码 `admin/admin@123`；修改 `plm.jwt.secret`（`application-prod.yml`）。
- 修改数据库默认口令 `plm@123`（同步改 compose 与 `application-prod.yml`）。
- 外网访问请置于 HTTPS 反向代理（如 Nginx 443 + 证书）之后，不要直接暴露 5432/6379。
