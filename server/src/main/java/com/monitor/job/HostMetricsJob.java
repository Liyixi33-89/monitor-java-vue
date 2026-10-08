package com.monitor.job;

import com.monitor.collector.HostMetricsCollector;
import com.monitor.config.TargetServersProperties;
import com.monitor.mapper.ServerMapper;
import com.monitor.ws.WsSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 主机资源指标采集任务：每 30s 一次 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HostMetricsJob {

    private final HostMetricsCollector collector;
    private final TargetServersProperties properties;
    private final ServerMapper serverMapper;
    private final WsSessionRegistry wsRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Scheduled(fixedRate = 30_000, initialDelay = 5_000)
    public void run() {
        properties.getTargetServers().forEach(cfg -> {
            collector.collect(cfg, cfg.getId());
        });
        // 采集完成后推送最新主机指标到 WS 客户端
        try {
            wsRegistry.broadcast(objectMapper.writeValueAsString(Map.of(
                    "type", "metrics_host_refreshed",
                    "data", Map.of("serverId", 1))));
        } catch (Exception ignored) {}
    }
}
