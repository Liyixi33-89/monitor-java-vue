-- V4: 项目级告知人邮箱 —— 告警按项目路由给对应负责人
-- notify_emails 为逗号分隔的邮箱列表，空则回退到全局 EMAIL_TO（monitor.notify.email-to）
ALTER TABLE projects ADD COLUMN notify_emails TEXT DEFAULT '';
