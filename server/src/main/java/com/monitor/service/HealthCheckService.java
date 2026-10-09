package com.monitor.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitor.entity.HealthCheck;
import com.monitor.entity.Project;
import com.monitor.mapper.HealthCheckMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 健康检查服务：对各项目配置的 health_check_url 发起 GET 请求，记录状态码与耗时。
 *
 * | 项目 | 检查方式 |
 * | MD Viewer 前端 | GET http://111.231.57.177:8081/ 期望 200 |
 * | MD Viewer 后端 | GET http://111.231.57.177:8081/api/portal/version 期望 200 |
 * | TaskManager 前端 | GET http://111.231.57.177/ 期望 200 |
 * | TaskManager 后端 | GET http://111.231.57.177/health 期望 200 + body healthy |
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCheckService {

    private final HealthCheckMapper healthCheckMapper;
    private final RestClient restClient = RestClient.builder().build();

    public HealthCheck check(Project project) {
        HealthCheck hc = new HealthCheck();
        hc.setProjectId(project.getId());
        hc.setServerId(project.getServerId());
        hc.setName(project.getName());
        hc.setUrl(project.getHealthCheckUrl());
        hc.setTimestamp(LocalDateTime.now());

        if (project.getHealthCheckUrl() == null || project.getHealthCheckUrl().isBlank()) {
            hc.setSuccess(0);
            healthCheckMapper.insert(hc);
            return hc;
        }

        long start = System.currentTimeMillis();
        try {
            var response = restClient.get()
                    .uri(project.getHealthCheckUrl())
                    .retrieve()
                    .toEntity(String.class);
            long latency = System.currentTimeMillis() - start;
            hc.setStatusCode(response.getStatusCode().value());
            hc.setLatencyMs((int) latency);
            hc.setSuccess(response.getStatusCode().is2xxSuccessful() ? 1 : 0);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            hc.setStatusCode(0);
            hc.setLatencyMs((int) latency);
            hc.setSuccess(0);
            log.warn("Health check failed for {}: {}", project.getName(), e.getMessage());
        }

        healthCheckMapper.insert(hc);
        return hc;
    }

    public List<HealthCheck> getRecent(Long projectId, int limit) {
        return healthCheckMapper.findRecent(projectId, limit);
    }

    /** 分页查询健康检查历史 */
    public Page<HealthCheck> getRecentPaged(Long projectId, int page, int pageSize) {
        Page<HealthCheck> p = new Page<>(page, pageSize);
        return healthCheckMapper.selectPage(p,
                Wrappers.<HealthCheck>lambdaQuery()
                        .eq(HealthCheck::getProjectId, projectId)
                        .orderByDesc(HealthCheck::getTimestamp));
    }

    /** 最近 n 次是否连续失败（供告警规则判定） */
    public boolean isConsecutiveFailing(Long projectId, int n) {
        List<Integer> flags = healthCheckMapper.findRecentSuccessFlags(projectId, n);
        if (flags.size() < n) return false;
        return flags.stream().allMatch(f -> f == 0);
    }
}
