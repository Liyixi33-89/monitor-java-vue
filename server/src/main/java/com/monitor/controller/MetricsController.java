package com.monitor.controller;

import com.monitor.entity.HealthCheck;
import com.monitor.entity.MetricsHost;
import com.monitor.entity.MetricsProcess;
import com.monitor.service.HealthCheckService;
import com.monitor.service.MetricsService;
import com.monitor.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;
    private final HealthCheckService healthCheckService;
    private final ProjectService projectService;

    @GetMapping("/metrics/host")
    public ResponseEntity<?> hostMetrics(@RequestParam Long serverId,
                                         @RequestParam(defaultValue = "1h") String range) {
        List<MetricsHost> list = metricsService.getHostMetrics(serverId, range);
        return ResponseEntity.ok(Map.of("ok", true, "data", list));
    }

    @GetMapping("/metrics/host/latest")
    public ResponseEntity<?> latestHostMetrics(@RequestParam Long serverId) {
        return ResponseEntity.ok(Map.of("ok", true, "data", metricsService.getLatestHostMetrics(serverId)));
    }

    @GetMapping("/metrics/process")
    public ResponseEntity<?> processMetrics(@RequestParam Long projectId,
                                            @RequestParam(defaultValue = "1h") String range) {
        List<MetricsProcess> list = metricsService.getProcessMetrics(projectId, range);
        return ResponseEntity.ok(Map.of("ok", true, "data", list));
    }

    @GetMapping("/metrics/process/latest")
    public ResponseEntity<?> latestProcessMetrics(@RequestParam Long projectId) {
        var latest = metricsService.getLatestProcessMetrics(projectId);
        // 非 PM2 项目（如 systemd 托管的 MD Viewer）无进程指标，返回 data: null 而非报错
        return ResponseEntity.ok(Map.of("ok", true, "data", latest == null ? "" : latest));
    }

    @GetMapping("/health-checks")
    public ResponseEntity<?> healthChecks(@RequestParam Long projectId,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        var pageResult = healthCheckService.getRecentPaged(projectId, Math.max(page, 1), size);
        return ResponseEntity.ok(Map.of("ok", true, "data", pageResult.getRecords(),
                "total", pageResult.getTotal(), "page", page, "pageSize", size));
    }

    /** 手动触发一次健康检查 */
    @PostMapping("/health-checks/trigger/{projectId}")
    public ResponseEntity<?> triggerHealthCheck(@PathVariable Long projectId) {
        var project = projectService.getById(projectId);
        if (project == null) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "error", "项目不存在"));
        }
        return ResponseEntity.ok(Map.of("ok", true, "data", healthCheckService.check(project)));
    }
}
