# 线上服务器项目监控系统 —— 需求文档

## 1. 背景与目标

当前需要对线上服务器（示例：`111.231.57.177`）上部署的业务项目进行统一监控，
及时掌握服务器资源状态与项目运行状况，发现异常后能第一时间告警，避免故障扩大。

本次连通性检测结论：
- ICMP Ping 可达（约 28~30ms）
- TCP 22（SSH）、80（HTTP）、443（HTTPS）均可正常连接
- 具备通过 SSH 远程采集数据或部署 Agent 的条件

**目标**：构建一套轻量级监控系统，包含「数据采集 Agent / 后端服务 / 前端可视化面板（Vite 构建）」，
实现对服务器资源 + 项目进程 + 服务可用性的实时监控、历史查询与告警通知。
**监控面板需按项目维度组织，明确区分「前端服务」与「后端服务」两类项目，并对二者的报错（JS 异常/接口异常/
进程崩溃/日志 ERROR 等）进行采集、聚合展示与告警，出现报错时需能第一时间感知并定位到具体项目。**

## 2. 监控对象

### 2.1 服务器层（Host）
- CPU 使用率（总体 / 核心）
- 内存使用率（已用/可用/swap）
- 磁盘使用率与 I/O（各分区）
- 网络流量（上行/下行、丢包）
- 系统负载（load average）
- 系统基础信息（OS、内核版本、运行时长、开放端口）

### 2.2 项目/进程层（App）
监控面板按「项目」维度组织，每个项目需标注 **类型（前端 / 后端）** 并关联其所属服务器、进程/端口信息，
前端项目与后端项目在面板上分别展示、互不混淆，同时支持从项目卡片下钻查看详情与报错记录。

- 进程存活状态（PID、CPU%、内存占用）
- Node.js / PM2 托管进程（名称、状态、重启次数、uptime）
- Docker 容器（如使用）：容器状态、资源占用、重启次数
- Nginx / 反向代理：进程状态、配置是否生效
- 端口监听状态（如 80/443/自定义业务端口）
- 项目基础信息：项目名称、类型（前端/后端）、所属服务器、访问地址、负责人（可选）

### 2.3 服务可用性层（Service）
- HTTP(S) 健康检查（指定 URL，检测状态码/响应时间/关键字）
- TCP 端口探活
- 接口响应时间与成功率（可选，压测/巡检级别）

### 2.4 日志层（Log，可选迭代）
- 应用错误日志关键字告警（如 ERROR/Exception 关键词扫描）
- Nginx access/error 日志统计（4xx/5xx 比例）

### 2.5 错误监控层（Error，重点需求）
需同时覆盖**后端服务报错**与**前端服务报错**，二者均按所属项目归档展示：

**后端服务报错**
- 进程未捕获异常 / Promise rejection 导致的崩溃或重启
- 应用日志中 ERROR/FATAL/Exception 等关键字（tail 日志文件或接收日志上报）
- 接口 HTTP 5xx 响应（及异常比例突增）
- 进程异常退出（exit code 非 0）、PM2 重启次数突增

**前端服务报错**
- 页面 JS 运行时错误（`window.onerror`）
- 未处理的 Promise 异常（`unhandledrejection`）
- 资源加载失败（脚本/样式/图片 404 等）
- 接口请求异常（前端发出的 XHR/Fetch 请求返回 4xx/5xx 或超时）
- 白屏/首屏渲染失败（可选，结合性能指标判断）

> 前端报错需在前端项目中接入一个轻量「错误采集 SDK」，捕获后通过信标请求（beacon/fetch）上报到后端错误收集接口；
> 后端报错通过日志采集 + 进程监控 + 接口状态码监听三种方式结合采集。

## 3. 功能需求

### 3.1 数据采集
- F1：支持通过 SSH 连接目标服务器周期性采集系统指标（无需在被控机常驻 Agent 的轻量模式）
- F2：支持在被控机部署采集 Agent（Node.js 脚本 + 定时任务 / systemd 服务），定期上报数据到后端
- F3：采集频率可配置（默认 15s~30s 一次，资源类指标；健康检查默认 30s~60s 一次）
- F4：支持多服务器、多项目的采集配置（本次仅接入 `111.231.57.177`，架构需预留扩展）

### 3.2 数据存储
- F5：采集数据写入时序存储（SQLite/InfluxDB/简化为定时落盘的 JSON 或关系数据库 + 定期聚合清理）
- F6：保留明细数据（如 7 天）+ 聚合数据（如 30~90 天，用于趋势图）

### 3.3 后端服务（API）
- F7：提供 REST/WebSocket 接口供前端拉取实时数据与历史数据
- F8：提供告警规则配置接口（阈值、通知渠道）
- F9：提供服务器/项目基础信息的增删改查（后续扩展多机房支持）
- F9a：提供前端错误上报接口（接收 SDK beacon 上报的 JS 异常/资源异常/接口异常）
- F9b：提供后端日志/错误采集通道（SSH tail 日志 或 后端项目主动上报错误接口）

### 3.4 前端可视化面板（Vite 构建）
- F10：Dashboard 总览页：服务器状态卡片（CPU/内存/磁盘/网络 实时数值 + 迷你趋势图）
- F11：项目列表页：**区分「前端项目」/「后端项目」两个分组（或可切换的 Tab）**，每个项目卡片展示：
  项目名称、类型标签（前端/后端）、所属服务器、运行状态（正常/报错/离线）、最近 24h 报错数、健康检查结果；
  点击卡片可下钻查看该项目的详情页
- F11a：项目详情页：展示该项目的进程/容器信息、历史报错列表（时间、错误类型、堆栈/日志片段、出现次数）、
  健康检查历史、资源占用趋势
- F12：历史趋势页：折线图展示指定时间范围内的资源使用趋势（支持 1h/6h/24h/7d 切换）
- F13：告警记录页：展示历史告警列表（时间、级别、内容、所属项目、是否已处理）
- F13a：错误监控页：汇总展示所有项目（前端+后端）的实时报错流，支持按项目/类型/级别筛选，
  支持点击查看详情（堆栈、请求上下文、发生次数、首次/最近发生时间）
- F14：响应式布局，支持桌面与移动端查看

### 3.5 告警通知
- F15：支持阈值告警（如 CPU>85% 持续 5 分钟、内存>90%、磁盘>85%、进程异常退出、健康检查失败）
- F15a：支持错误类告警：
  - 后端：进程崩溃/重启、HTTP 5xx 比例突增、日志出现 ERROR/FATAL 关键字
  - 前端：JS 报错数突增（如 5 分钟内同一错误出现 ≥N 次）、接口请求失败率突增、资源加载失败
- F16：支持多通知渠道：企业微信/钉钉机器人 Webhook、邮件（可选 短信/Server酱）
- F17：告警去重与静默（同一告警/同一错误指纹在未恢复前不重复发送，恢复后发送恢复通知）
- F17a：告警内容需明确标注「所属项目名称 + 项目类型（前端/后端）」，便于快速定位责任方

### 3.6 系统管理
- F18：登录鉴权，防止监控面板被未授权访问（简单账号密码即可，可选 JWT）
- F19：操作日志（谁在何时修改了告警规则/服务器配置）
- F20：项目管理：支持新增/编辑/删除被监控项目，配置项目类型（前端/后端）、所属服务器、
  访问地址、日志路径或进程名、健康检查 URL

## 4. 非功能需求

| 类别 | 要求 |
|---|---|
| 性能 | 单服务器采集对目标机器资源占用 <5% CPU、<50MB 内存 |
| 可靠性 | 采集端异常断线后自动重连；后端重启后数据不丢失（持久化存储） |
| 安全性 | SSH 密钥/密码等凭证需加密存储，不写入前端代码；面板需登录鉴权 |
| 可扩展性 | 新增服务器/项目只需增加配置，无需改动核心代码 |
| 部署成本 | 优先选用轻量方案（SQLite + Java 单体后端 + 定时任务），避免引入过重的中间件 |

## 5. 技术方案建议

```
┌─────────────┐   SSH 命令采集(JSch)     ┌───────────────┐     REST/WS      ┌───────────────┐
│ 线上服务器    │ ───────────────────────▶ │  后端服务       │ ───────────────▶ │ 前端面板(Vite)  │
│ 111.231.57.177│  指标/进程/日志tail      │ Java17+        │                 │ Vue3+ElementPlus│
└─────────────┘  pm2/systemd/journalctl  │ SpringBoot3.2  │                 │ +ECharts       │
                                          │ +SQLite+MyBatis │                 └───────────────┘
                                          │ +定时任务/告警引擎│
                                          └──────┬────────┘
                                                 │ Webhook
                                                 ▼
                                        企业微信/钉钉/邮件告警
```

> **技术栈调整说明（2026-10）**：初稿建议 Node.js 后端，实施阶段改为 **Java 17 + Spring Boot 3.2.5**，
> 与被监控的 MD Viewer 后端技术栈统一，便于团队维护。采集方式不变（SSH 远程执行，无目标机 Agent）。

- **采集方式**：后端通过 JSch 定时 SSH 连接服务器执行采集脚本（`top`/`free`/`df`/`pm2 jlist`/`systemctl`/`journalctl`/日志 `tail` 并解析输出），无需在目标机上额外安装 Agent，降低接入成本（对应 F1 轻量模式；F2 常驻 Agent 保留为后续演进项）。
- **后端**：Java 17 + Spring Boot 3.2.5 + MyBatis-Plus + SQLite（Flyway 建表），定时任务 `@Scheduled`，详见 `docs/BACKEND_DESIGN.md`。
- **前端**：Vite + Vue3 + ECharts + Element Plus，详见 `docs/FRONTEND_DESIGN.md`。
- **监控后端端口**：生产 **4000**（实测避开线上已占用的 22/80/8081/3000/8090/27017）；本地开发用 48080（本机 4000 被占）。
- **告警引擎**：规则引擎模块，周期扫描最新指标 vs 阈值规则，触发后调用对应 Webhook。
- **后端报错采集**：
  1. SSH 定期 `tail` / `pm2 logs --lines` 读取被监控后端项目日志，正则匹配 `ERROR`/`Exception`/`FATAL` 等关键字；
  2. 结合 `pm2 jlist` 的 `restart_time` 字段判断进程是否异常重启；
  3. 健康检查探测到连续 5xx 时，自动归档为一次「后端错误事件」。
- **前端报错采集**：在被监控的前端项目中引入一个轻量 SDK（<5KB，可通过 `<script>` 注入或 npm 包形式集成）：
  - 监听 `window.onerror`、`window.addEventListener('unhandledrejection')` 捕获 JS 异常
  - 监听资源加载失败事件（`error` 事件 + `target.tagName` 判断）
  - 拦截/包装 `fetch`/`XMLHttpRequest`，记录失败请求（状态码、耗时、URL）
  - 捕获后通过 `navigator.sendBeacon` 或 `fetch(..., {keepalive:true})` 上报到监控后端的 `/api/error/report` 接口，
    携带 `projectId`、`type`、`message`、`stack`、`url`、`userAgent`、`timestamp` 等字段
- **错误聚合**：后端对上报的错误按「错误指纹」（如 message+stack 前几行的 hash）聚合，避免同一错误刷屏，
  记录首次/最近发生时间与累计次数，用于告警判定和前端展示。

## 6. 数据模型（草案）

- `servers`：id, name, host, ssh_port, auth_type, created_at
- `projects`：id, name, **type**（frontend/backend）, server_id, access_url, proc_name/log_path,
  health_check_url, owner, created_at —— 项目主表，前后端项目统一存储，通过 type 区分
- `metrics_host`：server_id, timestamp, cpu, mem_used, mem_total, disk_used, disk_total, net_in, net_out, load1/5/15
- `metrics_process`：project_id, server_id, timestamp, proc_name, pid, cpu, mem, status
- `health_checks`：project_id, server_id, name, url/port, timestamp, status_code, latency_ms, success
- `error_events`：id, project_id, project_type（frontend/backend）, fingerprint, type（js_error/
  unhandledrejection/resource_error/api_error/exception/process_crash/http_5xx）, message, stack,
  url, first_seen_at, last_seen_at, count, status（open/resolved）
- `alert_rules`：id, project_id（可空=全局规则）, metric_type, condition(>,<,=), threshold, duration, notify_channel
- `alert_logs`：id, rule_id, server_id, project_id, project_type, triggered_at, resolved_at, level, message

## 7. 里程碑规划

> 2026-10-08 状态：M1~M5 核心功能均已完成（本地验证），待整体部署到线上 4000 端口。

| 阶段 | 内容 | 产出 |
|---|---|---|
| M1 | 服务器连通性验证、采集脚本编写与测试 | SSH 采集 CPU/内存/磁盘/进程数据可用 ✅ |
| M2 | 后端服务搭建、数据存储、REST API | 可通过接口查询实时与历史数据 ✅ |
| M3 | 前端 Vite 面板开发：总览页+趋势图+项目列表（区分前后端） | 可视化展示服务器与项目状态 ✅ |
| M3a | 前端错误采集 SDK 开发与接入被监控前端项目；后端日志/进程错误采集 | 前后端报错可实时上报并聚合展示 ✅ |
| M4 | 告警规则引擎 + Webhook 通知（含错误类告警） | 异常/报错可自动告警到企微/钉钉 ✅ |
| M5 | 登录鉴权、多服务器/多项目扩展支持 | 系统可安全接入更多服务器/项目 ✅（JWT 已实现；操作日志 F19 未实现） |

## 8. 已探测到的线上项目清单（2026-10-08 实测，SSH 密钥免密登录成功）

服务器 `111.231.57.177` 上共部署 **2 套项目**，均为「前端 + 后端」组合，监控系统需同时接入两套：

### 8.1 项目 A：MD Viewer（文档阅读平台）

| 层 | 信息 |
|---|---|
| 类型 | 前端：静态站 ｜ 后端：Java (Spring Boot) |
| 前端部署路径 | `/opt/mdviewer/frontend`（index.html + assets/，含 hash 文件名长缓存策略） |
| 前端访问方式 | Nginx 监听 **8081** 端口（`/etc/nginx/conf.d/md-viewer.conf`），`server_name 111.231.57.177` |
| 后端技术栈 | Spring Boot 3.2.5 / Java 17，`/opt/mdviewer/md-viewer-server.jar`，Main-Class `MdViewerApplication` |
| 后端进程管理 | **systemd**：`md-viewer.service`（`Restart=on-failure`），监听 `127.0.0.1:8090`（仅本机，经 Nginx 反代） |
| API 路由 | Nginx `/api/` → `127.0.0.1:8090`；SSE 长连接 `/api/portal/events`（版本推送，`proxy_buffering off`） |
| 健康检查建议 | `curl http://111.231.57.177:8081/` （前端）；`curl http://111.231.57.177:8081/api/...`（后端经反代） |
| 日志/报错采集方式 | `journalctl -u md-viewer -n 200 --no-pager`（systemd journal，无独立错误日志文件） |
| 进程监控命令 | `systemctl is-active md-viewer`、`systemctl status md-viewer` |
| 其他 | 每日 03:30 有 cron 备份任务 `/opt/mdviewer/bin/mdv-backup.sh`；敏感配置在 `/opt/mdviewer/md-viewer.env`（JWT secret，需脱敏采集） |

### 8.2 项目 B：TaskManager / 签到签退系统（attendance）

| 层 | 信息 |
|---|---|
| 类型 | 前端：React+Vite SPA ｜ 后端：Node.js (Koa2) |
| 前端部署路径 | `/var/www/taskmanager/frontend/dist`（Vite 构建产物） |
| 前端访问方式 | Nginx 监听 **80** 端口（`/etc/nginx/conf.d/taskmanager.conf`），已配置 `/health` 返回 200 |
| 后端技术栈 | Node.js + Koa2 + Mongoose，入口 `/var/www/taskmanager/backend/src/app.js` |
| 后端进程管理 | **PM2**（`pm2 jlist`），进程名 `attendance-backend`，`exec_mode: cluster_mode`，**2 个实例**（pm_id 0/1），`max_memory_restart` 500MB，已发生过 **3 次重启**（`restart_time:3`） |
| API 路由 | Nginx `/api/` → `upstream backend 127.0.0.1:3000`（含限流 `limit_req`，登录接口单独限流 5r/m） |
| 数据库 | MongoDB（本机 `127.0.0.1:27017/attendance`，`mongod` 进程常驻） |
| 健康检查 | 已有现成 `/health` 接口（`curl http://111.231.57.177/health` → `healthy`） |
| 日志/报错采集方式 | `tail -f /var/www/taskmanager/backend/logs/err.log` + `/var/www/taskmanager/backend/logs/out.log`；Nginx 错误日志 `/var/log/nginx/taskmanager_error.log`（当前主要是前端静态资源 404） |
| 进程监控命令 | `pm2 jlist` / `pm2 describe attendance-backend`（注意：pm2 位于 `/root/.nvm/versions/node/v18.20.8/bin/pm2`，需显式加 PATH 或指定 `PM2_HOME=/root/.pm2`） |
| 已发现的历史报错 | ① Mongoose 枚举校验失败（`checkoutData.captchaResult` 非法值，业务数据问题）；
② `❌ 调用Python服务失败: connect ECONNREFUSED ::1:5001`（依赖的 Python 微服务未启动/不可达，**需重点告警**） |
| 代码仓库 | `https://github.com/Liyixi33-89/node-or-react.git` |

### 8.3 两项目在监控面板中的呈现方式

- `projects` 表录入 4 条记录（2 项目 × 前后端各 1 条，或 1 项目 1 条记录 + type 区分「fullstack」子项，推荐做法：**按进程/服务粒度拆分为 4 条**，即 `md-viewer-frontend`、`md-viewer-backend`、`taskmanager-frontend`、`taskmanager-backend`），便于独立配置健康检查与告警阈值。
- 前端面板「项目列表」页按 **项目分组**（MD Viewer / TaskManager）展示，组内再分「前端」「后端」两张卡片。
- 采集方式差异化配置：
  - MD Viewer 后端：`journalctl` 轮询 + `systemctl status`
  - TaskManager 后端：`pm2 jlist`（含 CPU/内存/restart_time/status）+ 日志 tail
  - 两者前端：Nginx access/error log 统计 4xx/5xx + 浏览器端 SDK 上报 JS 报错

## 9. 待确认事项（需用户补充）

> 2026-10-08 更新：第 2、5 项已决策落地，其余仍待确认。

1. TaskManager 后端依赖的 **Python 服务（:5001）** 是否也需要纳入监控？目前已出现 ECONNREFUSED 报错。
2. ~~两个前端项目是否方便在源码中接入错误采集 SDK~~ **已落地**：SDK 已直接注入两个线上项目的 `index.html`（未改动业务源码，report-url 指向 `http://111.231.57.177:4000/api/error/report`），后端部署后自动生效。
3. 告警通知渠道偏好：企业微信 / 钉钉 / 邮件 / 其他？（后端已实现 wecom/dingtalk 两个渠道，Webhook URL 待填）
4. 是否需要支持更多服务器/项目的扩展，还是当前仅此 2 套项目（4 个服务）即可？（架构已预留多服务器扩展）
5. ~~前端监控面板框架偏好~~ **已决策**：Vue3 + Element Plus + ECharts。
6. 是否需要监控 MongoDB 自身状态（连接数、慢查询等），还是仅监控依赖它的后端服务？
