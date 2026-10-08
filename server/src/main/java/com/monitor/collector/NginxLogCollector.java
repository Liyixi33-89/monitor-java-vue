package com.monitor.collector;

import com.monitor.config.TargetServersProperties;
import com.monitor.entity.ErrorEvent;
import com.monitor.entity.Project;
import com.monitor.service.ErrorEventService;
import com.monitor.util.ByteOffsetTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 前端项目 Nginx 日志采集器（增量 tail + 4xx/5xx 统计）。
 * 适用于 MD Viewer 前端(8081) / TaskManager 前端(80) 两个静态站点。
 * 增量方式与 Pm2Collector.tailErrorLog 一致：记录字节偏移，避免重复扫描。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NginxLogCollector {

    // 匹配 nginx 默认 access_log 格式中的状态码，如: "GET / HTTP/1.1" 200 1234
    private static final Pattern STATUS_REGEX = Pattern.compile("\"\\s(\\d{3})\\s\\d+");

    private final SshExecutor sshExecutor;
    private final ErrorEventService errorEventService;
    private final ByteOffsetTracker offsetTracker;

    public StatusCount collect(TargetServersProperties.ServerConfig serverCfg, Project project) {
        if (project.getLogPath() == null || project.getLogPath().isBlank()) {
            return new StatusCount(0, 0);
        }
        String logPath = project.getLogPath();
        try {
            SshExecutor.ExecResult sizeResult = sshExecutor.exec(serverCfg, "stat -c %s " + logPath + " 2>/dev/null || echo 0");
            long currentSize = parseLongSafe(sizeResult.getStdout().trim());
            long offset = offsetTracker.normalizeOffset(logPath, currentSize);

            if (currentSize <= offset) {
                offsetTracker.set(logPath, currentSize);
                return new StatusCount(0, 0);
            }

            SshExecutor.ExecResult tailResult = sshExecutor.exec(
                    serverCfg, String.format("tail -c +%d %s | head -c 300000", offset + 1, logPath));
            offsetTracker.set(logPath, currentSize);

            int count4xx = 0, count5xx = 0;
            List<String> sample5xx = new ArrayList<>();

            for (String line : tailResult.getStdout().split("\n")) {
                if (line.isBlank()) continue;
                Matcher m = STATUS_REGEX.matcher(line);
                if (!m.find()) continue;
                int code = Integer.parseInt(m.group(1));
                if (code >= 500) {
                    count5xx++;
                    if (sample5xx.size() < 5) {
                        sample5xx.add(line.length() > 300 ? line.substring(0, 300) : line);
                    }
                } else if (code >= 400) {
                    count4xx++;
                }
            }

            // 单周期（约 60s）出现 >=5 条 5xx 即产出错误事件，具体阈值可在 alert_rules 中覆盖
            if (count5xx >= 5) {
                errorEventService.upsert(ErrorEvent.builder()
                        .projectId(project.getId())
                        .projectType("frontend")
                        .type("http_5xx")
                        .message(String.format("Nginx 日志检测到 %d 条 5xx 响应（采集窗口内）", count5xx))
                        .stack(String.join("\n", sample5xx))
                        .url(project.getAccessUrl())
                        .build());
            }

            return new StatusCount(count4xx, count5xx);
        } catch (Exception e) {
            log.error("NginxLogCollector failed for project {}: {}", project.getName(), e.getMessage());
            return new StatusCount(0, 0);
        }
    }

    private long parseLongSafe(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0L;
        }
    }

    public record StatusCount(int count4xx, int count5xx) {}
}
