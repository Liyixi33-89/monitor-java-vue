package com.monitor.service;

import com.monitor.entity.MetricsHost;
import com.monitor.entity.MetricsProcess;
import com.monitor.mapper.MetricsHostMapper;
import com.monitor.mapper.MetricsProcessMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MetricsHostMapper metricsHostMapper;
    private final MetricsProcessMapper metricsProcessMapper;

    public List<MetricsHost> getHostMetrics(Long serverId, String range) {
        return metricsHostMapper.findSince(serverId, resolveSince(range));
    }

    public MetricsHost getLatestHostMetrics(Long serverId) {
        return metricsHostMapper.findLatest(serverId);
    }

    /** 所有服务器的最新一条主机指标（供主机级告警规则评估） */
    public List<MetricsHost> getLatestHostMetricsAll() {
        return metricsHostMapper.findLatestPerServer();
    }

    public List<MetricsProcess> getProcessMetrics(Long projectId, String range) {
        return metricsProcessMapper.findSince(projectId, resolveSince(range));
    }

    public MetricsProcess getLatestProcessMetrics(Long projectId) {
        return metricsProcessMapper.findLatest(projectId);
    }

    /** 支持 1h/6h/24h/7d，默认 1h */
    private LocalDateTime resolveSince(String range) {
        if (range == null) return LocalDateTime.now().minus(Duration.ofHours(1));
        return switch (range) {
            case "6h" -> LocalDateTime.now().minus(Duration.ofHours(6));
            case "24h" -> LocalDateTime.now().minus(Duration.ofHours(24));
            case "7d" -> LocalDateTime.now().minus(Duration.ofDays(7));
            default -> LocalDateTime.now().minus(Duration.ofHours(1));
        };
    }
}
