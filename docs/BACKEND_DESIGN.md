# 后端技术设计文档（Java / Spring Boot 版）

> 对应需求文档：`docs/REQUIREMENTS.md`
> 监控目标：`111.231.57.177` 上的 2 套项目 4 个服务（MD Viewer 前/后端、TaskManager 前/后端）
> 技术栈与被监控的 MD Viewer 后端（Spring Boot 3.2.5 / Java 17）保持一致，便于团队统一维护。
>
> **端口规划（2026-10-08 实测确认，部署架构）**：
> - 目标服务器已占用端口：22(SSH)、80(TaskManager前端)、8081(MD Viewer前端)、3000(TaskManager后端)、8090(MD Viewer后端)、27017(MongoDB)
> - 生产部署架构：**Nginx 监听 4000**（托管监控前端静态文件 + `/api`、`/ws` 反代到后端）；**Spring Boot 后端绑定 `127.0.0.1:48080`**（仅本机，不对外）
> - 本地开发环境：后端同样用 48080（`--server.port=48080`，因本机 4000 被占）；`web/vite.config.js` 的代理指向 48080，与生产端口一致
> - 线上前端 SDK（已注入两项目 index.html）的 `data-report-url` 指向 `http://111.231.57.177:4000/api/error/report`，
>   由 Nginx 反代到后端 48080，无需改动线上 SDK

## 1. 技术选型

| 模块 | 选型 | 说明 |
|---|---|---|
| 运行时 | **Java 17** | 与线上 MD Viewer 后端版本一致 |
| 框架 | **Spring Boot 3.2.x** | 内置 Web/Scheduler/WebSocket/Security，生态成熟 |
| 持久层 | **MyBatis-Plus** + `sqlite-jdbc` | SQLite 轻量零运维；MyBatis 对手写 SQL/动态查询更友好（指标聚合、时间范围查询较多） |
| SSH 客户端 | **JSch**（`com.github.mwiede:jsch`）| 纯 Java SSH 实现，支持密钥登录、命令执行，社区活跃分支 |
| 定时任务 | **Spring `@Scheduled` + `TaskScheduler`** | 替代 node-cron，支持 cron 表达式与固定频率 |
| 实时推送 | **Spring WebSocket**（原生 `TextWebSocketHandler`，不用 STOMP，降低复杂度） | 对接前端 `/ws/realtime` |
| 鉴权 | **Spring Security + JJWT** | 账号密码登录 + JWT 过滤器 |
| HTTP 客户端 | **Spring `RestClient`**（或 `WebClient`） | 调用健康检查 URL、Webhook 通知 |
| 连接池 | **HikariCP**（Spring Boot 默认） | SQLite 连接池（注意需配置单连接/串行化写，SQLite 并发写较弱） |
| 构建工具 | **Maven** | 与 MD Viewer 项目（Maven JAR Plugin）保持一致 |
| 部署方式 | **systemd**（与 MD Viewer 完全相同的托管方式） | 运维方式统一，不再额外引入 PM2 |

## 2. 目录结构（Maven 标准布局）

```
server/
├── pom.xml
├── src/main/java/com/monitor/
│   ├── MonitorApplication.java              # 启动类
│   ├── config/
│   │   ├── SecurityConfig.java                # Spring Security + JWT 过滤器链（内含 CORS 配置）
│   │   ├── WebSocketConfig.java               # 注册 /ws/realtime handler
│   │   └── TargetServersProperties.java       # @ConfigurationProperties 读取被监控服务器/项目静态配置
│   │   # 注：无独立 SchedulerConfig —— @EnableScheduling 直接标注在启动类，
│   │   # 使用默认单线程调度器。SQLite 池 maximum-pool-size=1 时多线程调度只会
│   │   # 在数据库写入处排队，且并发 SSH 采集目标机反而增加负载，故刻意保持单线程。
│   ├── entity/                                # 对应数据库表的 POJO
│   │   ├── Server.java
│   │   ├── Project.java                       # type: FRONTEND | BACKEND（enum）
│   │   ├── MetricsHost.java
│   │   ├── MetricsProcess.java
│   │   ├── HealthCheck.java
│   │   ├── ErrorEvent.java
│   │   ├── AlertRule.java
│   │   ├── AlertLog.java
│   │   └── User.java
│   ├── mapper/                                 # MyBatis Mapper 接口 + XML
│   │   ├── ServerMapper.java / .xml
│   │   ├── ProjectMapper.java / .xml
│   │   ├── MetricsHostMapper.java / .xml
│   │   ├── MetricsProcessMapper.java / .xml
│   │   ├── HealthCheckMapper.java / .xml
│   │   ├── ErrorEventMapper.java / .xml
│   │   ├── AlertRuleMapper.java / .xml
│   │   ├── AlertLogMapper.java / .xml
│   │   └── UserMapper.java / .xml
│   ├── collector/                              # 采集器（核心）
│   │   ├── SshExecutor.java                    # JSch 封装：连接池 + 超时 + 重试
│   │   ├── HostMetricsCollector.java           # CPU/内存/磁盘/网络采集
│   │   ├── Pm2Collector.java                   # TaskManager 后端：pm2 jlist 采集
│   │   ├── SystemdCollector.java               # MD Viewer 后端：systemctl + journalctl
│   │   └── NginxLogCollector.java              # 前端 Nginx 日志增量 tail + 4xx/5xx 统计
│   ├── service/
│   │   ├── MetricsService.java
│   │   ├── ProjectService.java
│   │   ├── ErrorEventService.java              # 错误事件聚合（指纹计算与 upsert）
│   │   ├── AlertEngineService.java             # 告警规则评估 + 去重静默
│   │   ├── HealthCheckService.java
│   │   └── notify/
│   │       ├── NotifyChannel.java              # 通知渠道接口
│   │       ├── WeComNotifyChannel.java
│   │       └── DingTalkNotifyChannel.java
│   ├── controller/
│   │   ├── AuthController.java
│   │   ├── ServerController.java
│   │   ├── ProjectController.java
│   │   ├── MetricsController.java
│   │   ├── ErrorController.java                 # POST /api/error/report（SDK 上报，免鉴权，限流+Origin 白名单）
│   │   └── AlertController.java                 # 告警规则/记录 CRUD
│   ├── job/                                      # @Scheduled 定时任务
│   │   ├── HostMetricsJob.java                  # 15~30s
│   │   ├── ProcessMetricsJob.java               # 15~30s（内部按项目分发到 Pm2Collector / SystemdCollector）
│   │   ├── NginxLogJob.java                     # 60s
│   │   ├── HealthCheckJob.java                  # 30~60s
│   │   ├── AlertEvaluateJob.java                # 30s
│   │   └── CleanupJob.java                      # 每日 1 次，清理过期明细
│   ├── ws/
│   │   ├── RealtimeWebSocketHandler.java        # 广播最新指标/新错误事件
│   │   └── WsSessionRegistry.java               # 管理在线连接
│   ├── security/
│   │   ├── JwtTokenProvider.java
│   │   └── JwtAuthFilter.java
│   ├── util/
│   │   ├── FingerprintUtil.java                 # 错误指纹计算
│   │   └── ByteOffsetTracker.java               # 日志增量 tail 的偏移量管理（内存 Map，支持持久化扩展）
│   └── exception/
│       └── GlobalExceptionHandler.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-prod.yml
│   ├── db/migration/                             # Flyway 迁移脚本（建表 DDL）
│   │   └── V1__init.sql
│   └── mapper/*.xml                              # MyBatis XML（如不用注解方式）
├── monitor-server.service                        # systemd unit 文件
└── src/test/java/...
```

## 3. 数据库表结构（SQLite DDL，Flyway `V1__init.sql`）

```sql
-- 服务器
CREATE TABLE servers (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  host TEXT NOT NULL,
  ssh_port INTEGER DEFAULT 22,
  ssh_user TEXT DEFAULT 'root',
  auth_type TEXT DEFAULT 'key',          -- key | password
  auth_secret TEXT,                       -- 私钥路径或加密后的密码（AES-256-GCM），不进入任何 API 响应
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 项目（按服务粒度拆分，前后端各一行）
CREATE TABLE projects (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  group_name TEXT NOT NULL,               -- 'md-viewer' | 'taskmanager'
  name TEXT NOT NULL,                     -- 'MD Viewer 前端' / 'MD Viewer 后端' ...
  type TEXT NOT NULL CHECK(type IN ('frontend','backend')),
  server_id INTEGER REFERENCES servers(id),
  runtime TEXT,                           -- 'static' | 'springboot' | 'node-pm2' | 'node-koa'
  access_url TEXT,
  proc_name TEXT,                         -- pm2 进程名 / systemd 服务名
  log_path TEXT,
  health_check_url TEXT,
  owner TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 主机资源指标
CREATE TABLE metrics_host (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  server_id INTEGER NOT NULL,
  timestamp DATETIME NOT NULL,
  cpu REAL, mem_used INTEGER, mem_total INTEGER,
  disk_used INTEGER, disk_total INTEGER,
  net_in INTEGER, net_out INTEGER,
  load1 REAL, load5 REAL, load15 REAL
);
CREATE INDEX idx_metrics_host_ts ON metrics_host(server_id, timestamp);

-- 进程指标
CREATE TABLE metrics_process (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id INTEGER NOT NULL,
  server_id INTEGER NOT NULL,
  timestamp DATETIME NOT NULL,
  proc_name TEXT, pid INTEGER, cpu REAL, mem INTEGER,
  status TEXT,                            -- online | offline | errored
  restart_time INTEGER
);
CREATE INDEX idx_metrics_process_ts ON metrics_process(project_id, timestamp);

-- 健康检查
CREATE TABLE health_checks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id INTEGER NOT NULL,
  server_id INTEGER NOT NULL,
  name TEXT, url TEXT,
  timestamp DATETIME NOT NULL,
  status_code INTEGER, latency_ms INTEGER, success INTEGER   -- 0/1
);
CREATE INDEX idx_health_checks_ts ON health_checks(project_id, timestamp);

-- 错误事件（前端 JS 错误 + 后端异常/日志关键字 + 5xx）
CREATE TABLE error_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id INTEGER NOT NULL,
  project_type TEXT NOT NULL,             -- frontend | backend
  fingerprint TEXT NOT NULL,
  type TEXT NOT NULL,                     -- js_error|unhandledrejection|resource_error|api_error|exception|process_crash|http_5xx|log_error
  message TEXT, stack TEXT, url TEXT,
  first_seen_at DATETIME, last_seen_at DATETIME,
  count INTEGER DEFAULT 1,
  status TEXT DEFAULT 'open'              -- open | resolved
);
CREATE UNIQUE INDEX idx_error_fingerprint ON error_events(project_id, fingerprint);

-- 告警规则
CREATE TABLE alert_rules (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id INTEGER,                     -- NULL = 全局/主机级规则
  metric_type TEXT NOT NULL,              -- cpu|mem|disk|health|process_restart|error_rate|log_keyword
  condition TEXT NOT NULL,                -- > | < | =
  threshold REAL,
  duration_sec INTEGER DEFAULT 0,
  notify_channel TEXT,                    -- wecom | dingtalk | email
  enabled INTEGER DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 告警记录
CREATE TABLE alert_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  rule_id INTEGER,
  server_id INTEGER,
  project_id INTEGER,
  project_type TEXT,
  level TEXT,                             -- warning | critical
  message TEXT,
  triggered_at DATETIME,
  resolved_at DATETIME
);

-- 用户（登录鉴权）
CREATE TABLE users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

> SQLite 并发写较弱：MyBatis 数据源配置 `hikari.maximum-pool-size=1` 并开启 `PRAGMA journal_mode=WAL;`，
> 写操作统一走 Service 层做简单串行化，避免 `SQLITE_BUSY`。数据量增长后可无痛切换到 MySQL（表结构基本兼容）。

## 4. 采集模块设计

### 4.1 SSH 执行器（`SshExecutor.java`）

```java
@Component
public class SshExecutor {
    private final Map<Long, Session> pool = new ConcurrentHashMap<>();
    private final JSch jsch = new JSch();

    public ExecResult exec(ServerConfig cfg, String command, long timeoutMs) throws Exception {
        int attempts = 0;
        Exception lastErr = null;
        while (attempts < 3) {
            try {
                Session session = getSession(cfg);
                ChannelExec channel = (ChannelExec) session.openChannel("exec");
                channel.setCommand(command);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ByteArrayOutputStream err = new ByteArrayOutputStream();
                channel.setOutputStream(out);
                channel.setErrStream(err);
                channel.connect((int) timeoutMs);
                long start = System.currentTimeMillis();
                while (!channel.isClosed()) {
                    if (System.currentTimeMillis() - start > timeoutMs) {
                        channel.disconnect();
                        throw new TimeoutException("SSH command timeout: " + command);
                    }
                    Thread.sleep(50);
                }
                return new ExecResult(out.toString(StandardCharsets.UTF_8),
                        err.toString(StandardCharsets.UTF_8), channel.getExitStatus());
            } catch (Exception e) {
                lastErr = e;
                pool.remove(cfg.getId());
                attempts++;
                if (attempts < 3) Thread.sleep((long) Math.pow(2, attempts) * 1000);
            }
        }
        throw lastErr;
    }

    private Session getSession(ServerConfig cfg) throws Exception {
        Session s = pool.get(cfg.getId());
        if (s != null && s.isConnected()) return s;
        Session session = jsch.getSession(cfg.getSshUser(), cfg.getHost(), cfg.getSshPort());
        jsch.addIdentity(cfg.getSshKeyPath());
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(10_000);
        pool.put(cfg.getId(), session);
        return session;
    }
}
```

### 4.2 主机指标采集（`HostMetricsCollector.java`）
同 Node 版方案，一次 SSH 往返执行组合命令，减少连接开销：
```bash
echo '##CPU##'; top -bn1 | grep 'Cpu(s)'
echo '##MEM##'; free -b
echo '##DISK##'; df -B1 /
echo '##NET##'; cat /proc/net/dev
echo '##LOAD##'; cat /proc/loadavg
```
用 `String.split("##\\w+##")` 分段解析，正则提取数值后调用 `MetricsService.saveHost(...)`。

### 4.3 TaskManager 后端采集（`Pm2Collector.java`）
```bash
export PATH=/root/.nvm/versions/node/v18.20.8/bin:$PATH
export PM2_HOME=/root/.pm2
pm2 jlist
```
用 Jackson `ObjectMapper` 解析 JSON 数组（注意 pm2 输出可能带 BOM，先 `trim()` + 去除 `\uFEFF`）：
```java
JsonNode root = objectMapper.readTree(stdout.replace("\uFEFF", "").trim());
for (JsonNode p : root) {
    String name = p.get("name").asText();
    String status = p.get("pm2_env").get("status").asText();
    int restartTime = p.get("pm2_env").get("restart_time").asInt();
    double cpu = p.get("monit").get("cpu").asDouble();
    long mem = p.get("monit").get("memory").asLong();
    // 环比上次 restartTime，增加则产出 process_crash 错误事件
}
```
日志增量 tail（`err.log`）复用 `ByteOffsetTracker`：
```bash
stat -c %s /var/www/taskmanager/backend/logs/err.log
tail -c +<offset+1> /var/www/taskmanager/backend/logs/err.log | head -c 200000
```
按行用正则 `(?i)error|exception|fail|ECONNREFUSED` 匹配，命中则调用 `ErrorEventService.upsert(...)`。

### 4.4 MD Viewer 后端采集（`SystemdCollector.java`）
```bash
systemctl is-active md-viewer 2>/dev/null
systemctl show md-viewer -p NRestarts --value 2>/dev/null
journalctl -u md-viewer --since "-60s" --no-pager -o short-iso 2>/dev/null
```
`is-active` 非 `active` → 记录 `metrics_process.status = errored` 并产出 `process_crash` 事件；
journalctl 输出按行匹配 `(?i)error|exception|fail|fatal`，用 `LinkedHashSet` 维护最近 2000 行指纹做滑动去重，避免 `--since -60s` 时间窗口重叠造成重复计数。

### 4.5 Nginx 日志采集（`NginxLogCollector.java`）
增量 tail 原理同上，正则 `"\\s(\\d{3})\\s\\d+` 提取状态码，统计 4xx/5xx 数量；
MD Viewer 走 8081 对应 access/error log，TaskManager 走 `/var/log/nginx/taskmanager_error.log`。
单周期 5xx ≥5 条 → 产出 `http_5xx` 类型的 `error_events`。

### 4.6 健康检查（`HealthCheckService.java`）
用 Spring `RestClient` 发起 GET，记录状态码与耗时：

| 项目 | 检查方式 |
|---|---|
| MD Viewer 前端 | `GET http://111.231.57.177:8081/` 期望 200 |
| MD Viewer 后端 | `GET http://111.231.57.177:8081/api/portal/version` 期望 200 |
| TaskManager 前端 | `GET http://111.231.57.177/` 期望 200 |
| TaskManager 后端 | `GET http://111.231.57.177/health` 期望 200 + body `healthy` |

### 4.7 前端错误上报接收（`ErrorController.java`）
```java
@PostMapping("/api/error/report")
public ResponseEntity<?> report(@RequestBody @Valid ErrorReportDto dto) {
    ErrorEvent saved = errorEventService.upsert(ErrorEvent.builder()
            .projectId(dto.getProjectId())
            .projectType("frontend")
            .type(dto.getType())
            .message(dto.getMessage())
            .stack(dto.getStack())
            .url(dto.getUrl())
            .build());
    return ResponseEntity.ok(Map.of("ok", true, "fingerprint", saved.getFingerprint()));
}
```
接口免鉴权（Spring Security 配置 `permitAll()`），用 `Bucket4j` 做限流（每 IP 60 次/分钟），
并校验 `Origin` 头是否在白名单（`111.231.57.177` 对应的两个前端域名/端口）内。

## 5. 错误指纹聚合逻辑（`FingerprintUtil.java` + `ErrorEventService.java`）

```java
public static String compute(String projectId, String type, String message, String stack) {
    String normMsg = normalize(message);
    String normStack = normalize(stackHead(stack, 3));
    String key = projectId + "::" + type + "::" + normMsg + "::" + normStack;
    return DigestUtils.sha1Hex(key);
}

private static String normalize(String text) {
    if (text == null) return "";
    return text.replaceAll("\\d+", "#")        // 数字占位，忽略行号/ID/时间戳
               .replaceAll("0x[0-9a-fA-F]+", "#")
               .replaceAll("\\s+", " ")
               .trim();
    // 截断至 500 字符
}
```

`ErrorEventService.upsert(ErrorEvent evt)` 逻辑：
1. 计算 `fingerprint`
2. `SELECT id, count FROM error_events WHERE project_id=? AND fingerprint=?`
3. 存在 → `UPDATE ... SET count=count+1, last_seen_at=now, status='open'`（即使之前 resolved，复发自动重开）
4. 不存在 → `INSERT`，`first_seen_at=last_seen_at=now, count=1`
5. 返回结果供告警引擎判断是否需要触发（如最近 5 分钟 `count` 增量 ≥ 阈值）

## 6. 告警引擎设计（`AlertEngineService.java` + `AlertEvaluateJob.java`）

```java
@Scheduled(fixedRate = 30_000)
public void evaluate() {
    List<AlertRule> rules = alertRuleMapper.findEnabled();
    for (AlertRule rule : rules) {
        boolean triggered = switch (rule.getMetricType()) {
            case "cpu", "mem", "disk" -> checkHostMetricThreshold(rule);
            case "process_restart" -> checkProcessRestartIncrease(rule);
            case "health" -> checkHealthCheckFailStreak(rule);
            case "error_rate" -> checkErrorRateIncrease(rule);
            case "log_keyword" -> checkRecentLogErrorCount(rule);
            default -> false;
        };
        AlertLog openLog = alertLogMapper.findOpenByRule(rule.getId());
        if (triggered && openLog == null) {
            AlertLog log = buildAlertLog(rule);
            alertLogMapper.insert(log);
            notifyService.send(rule.getNotifyChannel(), buildMessage(rule, log)); // 消息含项目名称+类型(前端/后端)
        } else if (!triggered && openLog != null) {
            openLog.setResolvedAt(LocalDateTime.now());
            alertLogMapper.update(openLog);
            notifyService.sendResolved(rule.getNotifyChannel(), buildResolvedMessage(rule, openLog));
        }
    }
}
```
去重静默：同一规则存在 `resolved_at IS NULL` 的未恢复记录时不重复发送；恢复后自动发恢复通知。

各判定函数的实现约定（与代码一致）：
- `checkHostMetricThreshold`：`project_id` 为空时视为**主机级规则**，遍历所有服务器的最新一条 `metrics_host`（`MetricsHostMapper.findLatestPerServer()`），任一命中即触发；非空时按项目所属 server_id 取最新指标比对。
- `checkErrorRateIncrease`：统计该项目最近 `duration_sec`（默认 300s）内 open 状态错误事件的 `SUM(count)`，与 `threshold`（次数上限）按 `condition` 比对。
- `checkRecentLogErrorCount`：同上，但仅统计日志类类型（`log_error`/`process_crash`/`exception`/`api_error`）。
- `checkProcessRestartIncrease`：最新一条 `metrics_process.status` 为 `errored`/`offline` 即触发。
- `checkHealthCheckFailStreak`：最近 N 次（`duration_sec`，默认 3）健康检查连续失败即触发。
- `duration_sec` 持续时长判定（如"CPU>85% 持续 5 分钟"）暂未实现，当前按最新值即时判定，后续迭代可在 Service 层加时间窗口聚合。

## 7. REST API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 账号密码登录，返回 JWT |
| GET | `/api/servers` | 服务器列表 |
| GET/POST/PUT/DELETE | `/api/projects` | 项目 CRUD（含 type 字段） |
| GET | `/api/metrics/host?serverId=&range=` | 主机历史指标 |
| GET | `/api/metrics/process?projectId=&range=` | 进程历史指标 |
| GET | `/api/health-checks?projectId=&range=` | 健康检查历史 |
| GET | `/api/errors?projectId=&type=&status=` | 错误事件列表 |
| GET | `/api/errors/{id}` | 错误事件详情（含 stack） |
| PATCH | `/api/errors/{id}` | 标记已处理（status=resolved） |
| POST | `/api/error/report` | **前端 SDK 上报入口（免鉴权，按 IP 限流 + Origin 白名单）** |
| GET/POST/PUT/DELETE | `/api/alert-rules` | 告警规则 CRUD |
| GET | `/api/alert-logs` | 告警历史 |
| WS | `/ws/realtime` | 实时推送最新指标/新错误事件 |

## 8. 安全与可靠性

- SSH 凭证存库策略（当前实现）：`servers.auth_secret` 字段仅存私钥**路径**（`auth_type='key'`），私钥本体不落库，不做加密；`ServerController` 返回前统一置 `auth_secret=null`。密码型凭证的 AES-256-GCM/Jasypt 加密列为后续迭代项。
- `/api/error/report` 用 `Bucket4j` 按真实 IP 限流（解析 `X-Forwarded-For` 首段，回退 `RemoteAddr`；每 IP 60 次/分钟）+ Origin 白名单校验（复用 `monitor.cors.allowed-origins`，非法来源 403；无 Origin 头的请求放行）。
- `SshExecutor` 内置超时（10s）与指数退避重试（最多 3 次），连接失效自动从池中移除重建，避免目标机抖动拖垮采集任务。
- SQLite 开启 `WAL` 模式；`CleanupJob` 每日清理 7 天前明细（保留聚合汇总表，视数据量后续增加）。
- Spring Boot Actuator 暴露 `/actuator/health`，供自身存活监控（避免监控系统本身故障无人知晓）。
- 日志用 `Logback` + 按天滚动，关键异常（采集失败、通知发送失败）单独记录，便于排查监控系统自身问题。

## 9. 部署说明

与 MD Viewer 后端完全相同的 systemd 托管方式，运维统一：

```ini
# /etc/systemd/system/monitor-server.service
[Unit]
Description=Monitor Server (Spring Boot)
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/monitor
EnvironmentFile=/opt/monitor/monitor.env
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar /opt/monitor/monitor-server.jar --spring.profiles.active=prod
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
cd server
mvn clean package -DskipTests
scp target/monitor-server.jar root@<monitor-host>:/opt/monitor/
systemctl restart monitor-server
```

`application-prod.yml` 关键配置（实际文件见 `server/src/main/resources/application-prod.yml`）：
```yaml
server:
  port: 48080                    # 后端仅绑本机，对外由 Nginx 4000 反代
spring:
  datasource:
    url: jdbc:sqlite:${MONITOR_DB_PATH:/opt/monitor/data/monitor.sqlite}
    hikari:
      maximum-pool-size: 1
monitor:
  target-servers:
    - id: 1
      host: 111.231.57.177
      ssh-port: 22
      ssh-user: root
      ssh-key-path: /root/.ssh/id_rsa
  jwt:
    secret: ${JWT_SECRET}          # 生产强制环境变量注入，无默认值
  notify:
    wecom-webhook-url: ${WECOM_WEBHOOK_URL:}
    dingtalk-webhook-url: ${DINGTALK_WEBHOOK_URL:}
  cors:
    allowed-origins:                # 同时作为 /api/error/report 的 Origin 白名单
      - "http://111.231.57.177"
      - "http://111.231.57.177:8081"
```

> 注：部署采用「Nginx 4000 对外（静态前端 + 反代 `/api`、`/ws`）+ 后端 48080 仅本机」架构，
> 已实测避开线上全部占用端口（22/80/8081/3000/8090/27017）。
> 部署配套文件均已入库：`server/monitor-server.service`（unit）、`server/monitor.env.example`（环境变量模板）。
