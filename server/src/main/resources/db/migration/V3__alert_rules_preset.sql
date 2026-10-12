-- V3: 预置告警规则 —— 线上出现问题/服务挂了/出现 bug 时邮件推送
-- notify_channel=email，收件邮箱在 application-prod.yml monitor.notify.email-to 配置
-- 覆盖：4 个项目各自的服务存活(health)、全局主机资源(cpu/mem/disk)、各项目 bug 错误量

-- 1. 服务存活：健康检查连续 3 次失败（约 90 秒）即告警，"后台服务挂了"
INSERT INTO alert_rules (project_id, metric_type, condition, threshold, duration_sec, notify_channel, enabled, created_at)
VALUES (1, 'health', '=', 1, 3, 'email', 1, datetime('now', 'localtime')),
       (2, 'health', '=', 1, 3, 'email', 1, datetime('now', 'localtime')),
       (3, 'health', '=', 1, 3, 'email', 1, datetime('now', 'localtime')),
       (4, 'health', '=', 1, 3, 'email', 1, datetime('now', 'localtime'));

-- 2. 进程崩溃/异常退出："后台服务挂了"的进程级兜底（health 检测不到的进程死亡）
INSERT INTO alert_rules (project_id, metric_type, condition, threshold, duration_sec, notify_channel, enabled, created_at)
VALUES (2, 'process_restart', '=', 1, 0, 'email', 1, datetime('now', 'localtime')),
       (4, 'process_restart', '=', 1, 0, 'email', 1, datetime('now', 'localtime'));

-- 3. 主机资源（全局规则，project_id 为 NULL 遍历所有服务器）
INSERT INTO alert_rules (project_id, metric_type, condition, threshold, duration_sec, notify_channel, enabled, created_at)
VALUES (NULL, 'cpu', '>', 90, 0, 'email', 1, datetime('now', 'localtime')),
       (NULL, 'mem', '>', 90, 0, 'email', 1, datetime('now', 'localtime')),
       (NULL, 'disk', '>', 90, 0, 'email', 1, datetime('now', 'localtime'));

-- 4. bug 错误量：项目每 5 分钟内错误事件超过 10 条（前端 JS 报错/后端异常/API错误）
INSERT INTO alert_rules (project_id, metric_type, condition, threshold, duration_sec, notify_channel, enabled, created_at)
VALUES (1, 'error_rate', '>', 10, 300, 'email', 1, datetime('now', 'localtime')),
       (2, 'error_rate', '>', 10, 300, 'email', 1, datetime('now', 'localtime')),
       (3, 'error_rate', '>', 10, 300, 'email', 1, datetime('now', 'localtime')),
       (4, 'error_rate', '>', 10, 300, 'email', 1, datetime('now', 'localtime'));

-- 5. 日志错误关键字（log_error/process_crash/exception）：进程崩溃与严重 bug 的日志级信号
INSERT INTO alert_rules (project_id, metric_type, condition, threshold, duration_sec, notify_channel, enabled, created_at)
VALUES (2, 'log_keyword', '>', 5, 300, 'email', 1, datetime('now', 'localtime')),
       (4, 'log_keyword', '>', 5, 300, 'email', 1, datetime('now', 'localtime'));
