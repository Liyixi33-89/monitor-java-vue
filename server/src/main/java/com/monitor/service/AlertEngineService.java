package com.monitor.service;

import com.monitor.entity.AlertLog;
import com.monitor.entity.AlertRule;
import com.monitor.entity.MetricsHost;
import com.monitor.entity.MetricsProcess;
import com.monitor.entity.Project;
import com.monitor.mapper.AlertLogMapper;
import com.monitor.mapper.AlertRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警规则引擎：
 *  1. 每轮扫描 enabled=1 的规则
 *  2. 按 metric_type 判定是否触发
 *  3. 触发且无未恢复记录 -> 插入 alert_logs + 发送通知（消息含项目名称+类型）
 *  4. 不触发且存在未恢复记录 -> 标记 resolved_at + 发送恢复通知（去重静默）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEngineService {

    private final AlertRuleMapper alertRuleMapper;
    private final AlertLogMapper alertLogMapper;
    private final MetricsService metricsService;
    private final HealthCheckService healthCheckService;
    private final ErrorEventService errorEventService;
    private final ProjectService projectService;
    private final NotifyService notifyService;

    public void evaluateAll() {
        List<AlertRule> rules = alertRuleMapper.findEnabled();
        for (AlertRule rule : rules) {
            try {
                evaluateOne(rule);
            } catch (Exception e) {
                log.error("AlertRule evaluation failed, ruleId={}: {}", rule.getId(), e.getMessage());
            }
        }
    }

    private void evaluateOne(AlertRule rule) {
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
            notifyService.send(rule.getNotifyChannel(), buildTriggerMessage(rule, log));
        } else if (!triggered && openLog != null) {
            openLog.setResolvedAt(LocalDateTime.now());
            alertLogMapper.updateById(openLog);
            notifyService.send(rule.getNotifyChannel(), buildResolvedMessage(rule, openLog));
        }
    }

    private boolean checkHostMetricThreshold(AlertRule rule) {
        // 支持主机级规则：project_id 为空时遍历所有服务器，任一命中即触发
        if (rule.getProjectId() == null) {
            for (MetricsHost latest : metricsService.getLatestHostMetricsAll()) {
                if (matchHostMetric(latest, rule)) return true;
            }
            return false;
        }
        Project project = projectService.getById(rule.getProjectId());
        if (project == null) return false;
        MetricsHost latest = metricsService.getLatestHostMetrics(project.getServerId());
        if (latest == null) return false;
        return matchHostMetric(latest, rule);
    }

    private boolean matchHostMetric(MetricsHost latest, AlertRule rule) {
        Double value = switch (rule.getMetricType()) {
            case "cpu" -> latest.getCpu();
            case "mem" -> latest.getMemTotal() != null && latest.getMemTotal() > 0
                    ? (latest.getMemUsed() * 100.0 / latest.getMemTotal()) : null;
            case "disk" -> latest.getDiskTotal() != null && latest.getDiskTotal() > 0
                    ? (latest.getDiskUsed() * 100.0 / latest.getDiskTotal()) : null;
            default -> null;
        };
        if (value == null) return false;
        return compare(value, rule.getCondition(), rule.getThreshold());
    }

    private boolean checkProcessRestartIncrease(AlertRule rule) {
        if (rule.getProjectId() == null) return false;
        MetricsProcess latest = metricsService.getLatestProcessMetrics(rule.getProjectId());
        if (latest == null) return false;
        return "errored".equals(latest.getStatus()) || "offline".equals(latest.getStatus());
    }

    private boolean checkHealthCheckFailStreak(AlertRule rule) {
        if (rule.getProjectId() == null) return false;
        int n = rule.getDurationSec() != null && rule.getDurationSec() > 0 ? rule.getDurationSec() : 3;
        return healthCheckService.isConsecutiveFailing(rule.getProjectId(), n);
    }

    private boolean checkErrorRateIncrease(AlertRule rule) {
        // 统计该项目最近 N 分钟（duration_sec，默认 300s）内 open 状态错误事件的累计次数，
        // 达到阈值即触发（阈值 threshold 表示次数上限，condition 通常为 >）
        Long projectId = rule.getProjectId();
        if (projectId == null) return false;
        int windowSec = rule.getDurationSec() != null && rule.getDurationSec() > 0 ? rule.getDurationSec() : 300;
        int recentCount = errorEventService.getRecentErrorCount(projectId, windowSec);
        Double threshold = rule.getThreshold();
        if (threshold == null) return false;
        return compare(recentCount, rule.getCondition(), threshold);
    }

    private boolean checkRecentLogErrorCount(AlertRule rule) {
        // 日志关键字类错误（log_error / process_crash / exception）最近 N 分钟累计次数达到阈值即触发
        Long projectId = rule.getProjectId();
        if (projectId == null) return false;
        int windowSec = rule.getDurationSec() != null && rule.getDurationSec() > 0 ? rule.getDurationSec() : 300;
        int recentCount = errorEventService.getRecentErrorCount(projectId, windowSec,
                "log_error", "process_crash", "exception", "api_error");
        Double threshold = rule.getThreshold();
        if (threshold == null) return false;
        return compare(recentCount, rule.getCondition(), threshold);
    }

    private boolean compare(double value, String condition, Double threshold) {
        if (threshold == null) return false;
        return switch (condition) {
            case ">" -> value > threshold;
            case "<" -> value < threshold;
            case "=" -> Math.abs(value - threshold) < 0.001;
            default -> false;
        };
    }

    private AlertLog buildAlertLog(AlertRule rule) {
        Project project = rule.getProjectId() != null ? projectService.getById(rule.getProjectId()) : null;
        AlertLog log = new AlertLog();
        log.setRuleId(rule.getId());
        log.setProjectId(rule.getProjectId());
        log.setProjectType(project != null ? project.getType() : null);
        log.setServerId(project != null ? project.getServerId() : null);
        log.setLevel("critical");
        log.setMessage(buildTriggerMessage(rule, null));
        log.setTriggeredAt(LocalDateTime.now());
        return log;
    }

    private String buildTriggerMessage(AlertRule rule, AlertLog logEntity) {
        Project project = rule.getProjectId() != null ? projectService.getById(rule.getProjectId()) : null;
        String projectDesc = project != null
                ? String.format("[%s/%s]", project.getName(), "frontend".equals(project.getType()) ? "前端" : "后端")
                : "[主机级]";
        return String.format("🔴 告警触发 %s 指标=%s 条件=%s%.2f", projectDesc, rule.getMetricType(), rule.getCondition(), rule.getThreshold());
    }

    private String buildResolvedMessage(AlertRule rule, AlertLog logEntity) {
        Project project = rule.getProjectId() != null ? projectService.getById(rule.getProjectId()) : null;
        String projectDesc = project != null
                ? String.format("[%s/%s]", project.getName(), "frontend".equals(project.getType()) ? "前端" : "后端")
                : "[主机级]";
        return String.format("✅ 告警恢复 %s 指标=%s", projectDesc, rule.getMetricType());
    }
}
