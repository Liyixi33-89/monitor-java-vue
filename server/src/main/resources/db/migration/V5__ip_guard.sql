-- V5: IP 访问防护 —— 外部 IP 访问统计（按小时窗口聚合）+ 封禁记录
CREATE TABLE ip_visit_stats (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ip TEXT NOT NULL,
  hour_window TEXT NOT NULL,              -- 'yyyy-MM-dd HH:00' 聚合窗口
  total_requests INTEGER DEFAULT 0,
  count_4xx INTEGER DEFAULT 0,
  count_5xx INTEGER DEFAULT 0,
  sample_paths TEXT DEFAULT '',           -- 最多 5 条样例请求行
  updated_at DATETIME,
  UNIQUE(ip, hour_window)
);
CREATE INDEX idx_ip_stats_window ON ip_visit_stats(hour_window DESC);
CREATE INDEX idx_ip_stats_ip ON ip_visit_stats(ip);

CREATE TABLE ip_bans (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ip TEXT NOT NULL,
  reason TEXT DEFAULT '',
  source TEXT DEFAULT 'auto',             -- auto(自动判定) | manual(手动)
  banned_at DATETIME,
  expires_at DATETIME,                    -- 到期自动解封；NULL = 永久
  status TEXT DEFAULT 'active',           -- active | lifted
  lifted_at DATETIME,
  request_count INTEGER DEFAULT 0         -- 触发时的窗口请求数
);
CREATE INDEX idx_ip_bans_ip ON ip_bans(ip);
CREATE INDEX idx_ip_bans_status ON ip_bans(status);
