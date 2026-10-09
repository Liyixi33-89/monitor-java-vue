-- 发布记录表：手动登记每次发布，追溯发布与故障的时间线关系
CREATE TABLE deployments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id INTEGER NOT NULL,
  version TEXT,
  operator TEXT,
  remark TEXT,
  result TEXT DEFAULT 'success',          -- success | rollback | failed
  deployed_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_deployments_project ON deployments(project_id);
CREATE INDEX idx_deployments_time ON deployments(deployed_at DESC);
