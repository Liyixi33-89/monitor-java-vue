package com.monitor.job;

import com.monitor.service.HealthCheckService;
import com.monitor.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 健康检查任务：每 60s 对所有配置了 health_check_url 的项目探测一次 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthCheckJob {

    private final HealthCheckService healthCheckService;
    private final ProjectService projectService;

    @Scheduled(fixedRate = 60_000, initialDelay = 15_000)
    public void run() {
        projectService.listAll().forEach(project -> {
            if (project.getHealthCheckUrl() != null && !project.getHealthCheckUrl().isBlank()) {
                healthCheckService.check(project);
            }
        });
    }
}
