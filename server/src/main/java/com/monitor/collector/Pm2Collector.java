package com.monitor.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitor.config.TargetServersProperties;
import com.monitor.entity.ErrorEvent;
import com.monitor.entity.MetricsProcess;
import com.monitor.entity.Project;
import com.monitor.mapper.MetricsProcessMapper;
import com.monitor.service.ErrorEventService;
import com.monitor.util.ByteOffsetTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * TaskManager 后端（attendance-backend）采集器 —— 基于 PM2。
 *
 * 职责：
 *  1. 采集进程状态/CPU/内存/重启次数 -> metrics_process
 *  2. restart_time 环比增加 -> 产出一条 error_events（process_crash）
 *  3. tail err.log 增量新内容，匹配关键字 -> 产出 error_events（exception/api_error）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Pm2Collector {

    private static final String PM2_BIN_DIR = "/root/.nvm/versions/node/v18.20.8/bin";
    private static final String PM2_HOME = "/root/.pm2";
    private static final Pattern ERROR_KEYWORDS =
            Pattern.compile("error|exception|fail|ECONNREFUSED|unhandledrejection", Pattern.CASE_INSENSITIVE);

    private final SshExecutor sshExecutor;
    private final MetricsProcessMapper metricsProcessMapper;
    private final ErrorEventService errorEventService;
    private final ByteOffsetTracker offsetTracker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 记录上一次采集到的 restart_time，用于环比判断进程是否发生了新的崩溃重启：key = projectId:pmId
    private final Map<String, Integer> lastRestartTime = new ConcurrentHashMap<>();

    public void collect(TargetServersProperties.ServerConfig serverCfg, Long serverId, Project project) {
        try {
            String cmd = String.format(
                    "export PATH=%s:$PATH; export PM2_HOME=%s; pm2 jlist", PM2_BIN_DIR, PM2_HOME);
            SshExecutor.ExecResult result = sshExecutor.exec(serverCfg, cmd);
            String stdout = result.getStdout().replace("\uFEFF", "").trim();
            if (stdout.isEmpty()) {
                recordOffline(project, serverId);
                return;
            }

            JsonNode root = objectMapper.readTree(stdout);
            boolean found = false;
            for (JsonNode p : root) {
                if (!project.getProcName().equals(p.get("name").asText())) continue;
                found = true;
                JsonNode pm2Env = p.get("pm2_env");
                String status = pm2Env.get("status").asText();
                int restartTime = pm2Env.get("restart_time").asInt();
                int pmId = p.get("pm_id").asInt();
                double cpu = p.get("monit").get("cpu").asDouble();
                long mem = p.get("monit").get("memory").asLong();
                int pid = p.has("pid") ? p.get("pid").asInt() : -1;

                recordMetric(project, serverId, status, cpu, mem, pid, restartTime);

                String key = project.getId() + ":" + pmId;
                Integer prev = lastRestartTime.get(key);
                if (prev != null && restartTime > prev) {
                    errorEventService.upsert(ErrorEvent.builder()
                            .projectId(project.getId())
                            .projectType("backend")
                            .type("process_crash")
                            .message(String.format("进程 %s(pm_id=%d) 发生异常重启，restart_time: %d -> %d",
                                    project.getProcName(), pmId, prev, restartTime))
                            .stack("")
                            .url("")
                            .build());
                }
                lastRestartTime.put(key, restartTime);
            }
            if (!found) {
                recordOffline(project, serverId);
            }

            if (project.getLogPath() != null && !project.getLogPath().isBlank()) {
                tailErrorLog(serverCfg, project);
            }
        } catch (Exception e) {
            log.error("Pm2Collector failed for project {}: {}", project.getName(), e.getMessage());
        }
    }

    private void recordMetric(Project project, Long serverId, String status, double cpu, long mem, int pid, int restartTime) {
        MetricsProcess m = new MetricsProcess();
        m.setProjectId(project.getId());
        m.setServerId(serverId);
        m.setTimestamp(LocalDateTime.now());
        m.setProcName(project.getProcName());
        m.setPid(pid);
        m.setCpu(cpu);
        m.setMem(mem);
        m.setStatus(status);
        m.setRestartTime(restartTime);
        metricsProcessMapper.insert(m);
    }

    private void recordOffline(Project project, Long serverId) {
        MetricsProcess m = new MetricsProcess();
        m.setProjectId(project.getId());
        m.setServerId(serverId);
        m.setTimestamp(LocalDateTime.now());
        m.setProcName(project.getProcName());
        m.setStatus("offline");
        metricsProcessMapper.insert(m);
    }

    /**
     * 增量读取错误日志新增内容：
     *  1. 获取文件当前大小
     *  2. 若比上次记录的 offset 大，用 tail -c +<offset+1> 读取新增部分
     *  3. 按行扫描关键字，命中则调用 ErrorEventService.upsert 聚合
     */
    private void tailErrorLog(TargetServersProperties.ServerConfig serverCfg, Project project) throws Exception {
        String logPath = project.getLogPath();
        SshExecutor.ExecResult sizeResult = sshExecutor.exec(serverCfg, "stat -c %s " + logPath + " 2>/dev/null || echo 0");
        long currentSize = parseLongSafe(sizeResult.getStdout().trim());
        long offset = offsetTracker.normalizeOffset(logPath, currentSize);

        if (currentSize <= offset) {
            offsetTracker.set(logPath, currentSize);
            return;
        }

        SshExecutor.ExecResult tailResult = sshExecutor.exec(
                serverCfg, String.format("tail -c +%d %s | head -c 200000", offset + 1, logPath));
        offsetTracker.set(logPath, currentSize);

        for (String line : tailResult.getStdout().split("\n")) {
            if (line.isBlank()) continue;
            if (ERROR_KEYWORDS.matcher(line).find()) {
                String type = line.contains("ECONNREFUSED") ? "api_error" : "exception";
                errorEventService.upsert(ErrorEvent.builder()
                        .projectId(project.getId())
                        .projectType("backend")
                        .type(type)
                        .message(line.length() > 500 ? line.substring(0, 500) : line)
                        .stack("")
                        .url("")
                        .build());
            }
        }
    }

    private long parseLongSafe(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0L;
        }
    }
}
