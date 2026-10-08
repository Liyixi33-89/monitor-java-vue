-- 服务器
CREATE TABLE servers (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  host TEXT NOT NULL,
  ssh_port INTEGER DEFAULT 22,
  ssh_user TEXT DEFAULT 'root',
  auth_type TEXT DEFAULT 'key',
  auth_secret TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 项目（按服务粒度拆分，前后端各一行）
CREATE TABLE projects (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  group_name TEXT NOT NULL,
  name TEXT NOT NULL,
  type TEXT NOT NULL CHECK(type IN ('frontend','backend')),
  server_id INTEGER REFERENCES servers(id),
  runtime TEXT,
  access_url TEXT,
  proc_name TEXT,
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
  status TEXT,
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
  status_code INTEGER, latency_ms INTEGER, success INTEGER
);
CREATE INDEX idx_health_checks_ts ON health_checks(project_id, timestamp);

-- 错误事件
CREATE TABLE error_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id INTEGER NOT NULL,
  project_type TEXT NOT NULL,
  fingerprint TEXT NOT NULL,
  type TEXT NOT NULL,
  message TEXT, stack TEXT, url TEXT,
  first_seen_at DATETIME, last_seen_at DATETIME,
  count INTEGER DEFAULT 1,
  status TEXT DEFAULT 'open'
);
CREATE UNIQUE INDEX idx_error_fingerprint ON error_events(project_id, fingerprint);

-- 告警规则
CREATE TABLE alert_rules (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id INTEGER,
  metric_type TEXT NOT NULL,
  condition TEXT NOT NULL,
  threshold REAL,
  duration_sec INTEGER DEFAULT 0,
  notify_channel TEXT,
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
  level TEXT,
  message TEXT,
  triggered_at DATETIME,
  resolved_at DATETIME
);

-- 用户
CREATE TABLE users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 初始数据：服务器
INSERT INTO servers (id, name, host, ssh_port, ssh_user, auth_type) VALUES
  (1, '线上服务器-1', '111.231.57.177', 22, 'root', 'key');

-- 初始数据：4 个被监控项目
INSERT INTO projects (id, group_name, name, type, server_id, runtime, access_url, proc_name, log_path, health_check_url, owner) VALUES
  (1, 'md-viewer', 'MD Viewer 前端', 'frontend', 1, 'static', 'http://111.231.57.177:8081/', NULL, '/var/log/nginx/access.log', 'http://111.231.57.177:8081/', NULL),
  (2, 'md-viewer', 'MD Viewer 后端', 'backend', 1, 'springboot', 'http://111.231.57.177:8081/api/', 'md-viewer', NULL, 'http://111.231.57.177:8081/api/portal/version', NULL),
  (3, 'taskmanager', 'TaskManager 前端', 'frontend', 1, 'static', 'http://111.231.57.177/', NULL, '/var/log/nginx/taskmanager_error.log', 'http://111.231.57.177/', NULL),
  (4, 'taskmanager', 'TaskManager 后端', 'backend', 1, 'node-pm2', 'http://111.231.57.177/api/', 'attendance-backend', '/var/www/taskmanager/backend/logs/err.log', 'http://111.231.57.177/health', NULL);

-- 初始数据：默认管理员账号 admin / admin123（BCrypt 哈希，启动后请立即修改密码）
INSERT INTO users (username, password_hash) VALUES
  ('admin', '$2b$10$Kttck7onVVGxgCVKYAStAeYgevM86kRedbswk6ONClaWtkDLI/yhC');
