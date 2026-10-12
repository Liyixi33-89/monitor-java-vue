package com.monitor.collector;

import com.monitor.config.TargetServersProperties;
import com.monitor.entity.ErrorEvent;
import com.monitor.entity.MetricsProcess;
import com.monitor.entity.Project;
import com.monitor.mapper.MetricsProcessMapper;
import com.monitor.service.ErrorEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * systemd 托管的后端项目采集器（如 MD Viewer 后端）。
 *
 * 职责：
 *  1. systemctl is-active / show -> 进程存活状态 metrics_process
 *  2. journalctl --since -60s 增量读取日志，匹配关键字 -> error_events（log_error）
 *     journalctl 本身支持按时间窗口过滤，无需像文件日志那样手动维护字节偏移；
 *     采集周期(30s) < --since 窗口(60s) 做兜底重叠，避免漏读；重叠部分通过滑动去重 Set 避免重复计数。
 *
 * 服务名取 projects.proc_name（回退 group_name），支持任意 systemd 项目，不再硬编码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemdCollector {

    /** Spring Boot 默认日志行级别标记，如 " INFO "、" ERROR "、" WARN "（三空格对齐时含前导空格） */
    private static final Pattern LOG_LEVEL = Pattern.compile("\\s(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s");
    private static final int MAX_SEEN = 2000;

    private final SshExecutor sshExecutor;
    private final MetricsProcessMapper metricsProcessMapper;
    private final ErrorEventService errorEventService;

    // 滑动去重：记录最近处理过的日志行前缀，避免 --since 时间窗口重叠造成重复计数
    // 按服务名隔离，避免多 systemd 项目之间误判重复
    private final Map<String, Set<String>> seenLinesByService = new ConcurrentHashMap<>();

    public synchronized void collect(TargetServersProperties.ServerConfig serverCfg, Long serverId, Project project) {
        // 服务名取项目配置的 proc_name，未配置时回退项目组标识（兼容旧数据）
        String serviceName = (project.getProcName() != null && !project.getProcName().isBlank())
                ? project.getProcName() : project.getGroupName();
        try {
            String statusCmd = String.format(
                    "systemctl is-active %s 2>/dev/null; systemctl show %s -p MainPID,NRestarts --value 2>/dev/null",
                    serviceName, serviceName);
            SshExecutor.ExecResult result = sshExecutor.exec(serverCfg, statusCmd);
            String[] lines = result.getStdout().trim().split("\n");
            String activeState = lines.length > 0 ? lines[0].trim() : "unknown";
            Integer mainPid = lines.length > 1 ? parseIntSafe(lines[1]) : null;
            Integer nRestarts = lines.length > 2 ? parseIntSafe(lines[2]) : 0;

            String status = "active".equals(activeState) ? "online" : "errored";

            MetricsProcess m = new MetricsProcess();
            m.setProjectId(project.getId());
            m.setServerId(serverId);
            m.setTimestamp(LocalDateTime.now());
            m.setProcName(serviceName);
            m.setPid(mainPid);
            m.setStatus(status);
            m.setRestartTime(nRestarts);
            metricsProcessMapper.insert(m);

            if (!"online".equals(status)) {
                errorEventService.upsert(ErrorEvent.builder()
                        .projectId(project.getId())
                        .projectType("backend")
                        .type("process_crash")
                        .message("systemd 服务 " + serviceName + " 状态异常: " + activeState)
                        .stack("")
                        .url("")
                        .build());
            }

            collectJournalErrors(serverCfg, project, serviceName);
        } catch (Exception e) {
            log.error("SystemdCollector failed for project {}: {}", project.getName(), e.getMessage());
        }
    }

    private void collectJournalErrors(TargetServersProperties.ServerConfig serverCfg, Project project, String serviceName) throws Exception {
        String cmd = String.format(
                "journalctl -u %s --since \"-60s\" --no-pager -o short-iso 2>/dev/null", serviceName);
        SshExecutor.ExecResult result = sshExecutor.exec(serverCfg, cmd);

        Set<String> seenLines = seenLinesByService.computeIfAbsent(serviceName, k -> new LinkedHashSet<>());
        for (String line : result.getStdout().split("\n")) {
            if (line.isBlank()) continue;
            // 解析 Spring Boot 日志级别标记（" ERROR "/" WARN "）：避免 INFO 行里
            // 含 Exception 类名/关键字（如 DefaultSecurityFilterChain 启动日志）造成的误报
            var levelMatcher = LOG_LEVEL.matcher(line);
            if (!levelMatcher.find() || !isErrorLevel(levelMatcher.group(1))) continue;

            String lineKey = line.length() > 150 ? line.substring(0, 150) : line;
            if (seenLines.contains(lineKey)) continue;
            seenLines.add(lineKey);
            if (seenLines.size() > MAX_SEEN) {
                // 简单清理：保留最近一半，避免内存无限增长
                var it = seenLines.iterator();
                int toRemove = seenLines.size() - MAX_SEEN / 2;
                for (int i = 0; i < toRemove && it.hasNext(); i++) {
                    it.next();
                    it.remove();
                }
            }

            errorEventService.upsert(ErrorEvent.builder()
                    .projectId(project.getId())
                    .projectType("backend")
                    .type("log_error")
                    .message(line.length() > 500 ? line.substring(0, 500) : line)
                    .stack("")
                    .url("")
                    .build());
        }
    }

    private Integer parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** ERROR/FATAL 记为 log_error；WARN 记为 warn（供日志关键字告警参考，不算错误级别） */
    private boolean isErrorLevel(String level) {
        return "ERROR".equals(level) || "FATAL".equals(level);
    }
}
