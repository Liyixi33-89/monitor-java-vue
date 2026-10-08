package com.monitor.job;

import com.monitor.collector.NginxLogCollector;
import com.monitor.collector.Pm2Collector;
import com.monitor.collector.SystemdCollector;
import com.monitor.config.TargetServersProperties;
import com.monitor.entity.Project;
import com.monitor.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 进程/项目级采集任务：每 30s 一次。
 * 按项目的 runtime 分发到不同采集器：
 *   - node-pm2      -> Pm2Collector    （TaskManager 后端）
 *   - springboot    -> SystemdCollector（MD Viewer 后端）
 *   - static        -> NginxLogCollector（两个前端项目的 Nginx 日志）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessMetricsJob {

    private final Pm2Collector pm2Collector;
    private final SystemdCollector systemdCollector;
    private final NginxLogCollector nginxLogCollector;
    private final ProjectService projectService;
    private final TargetServersProperties properties;

    @Scheduled(fixedRate = 30_000, initialDelay = 10_000)
    public void run() {
        var serverCfg = properties.getTargetServers().get(0); // MVP：单服务器
        for (Project project : projectService.listAll()) {
            switch (project.getRuntime() == null ? "" : project.getRuntime()) {
                case "node-pm2" -> pm2Collector.collect(serverCfg, project.getServerId(), project);
                case "springboot" -> systemdCollector.collect(serverCfg, project.getServerId(), project);
                case "static" -> nginxLogCollector.collect(serverCfg, project);
                default -> log.debug("项目 {} 无匹配采集器，runtime={}", project.getName(), project.getRuntime());
            }
        }
    }
}
