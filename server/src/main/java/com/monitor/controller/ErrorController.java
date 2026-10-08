package com.monitor.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.monitor.config.TargetServersProperties;
import com.monitor.entity.ErrorEvent;
import com.monitor.entity.Project;
import com.monitor.mapper.ErrorEventMapper;
import com.monitor.service.ErrorEventService;
import com.monitor.service.ProjectService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 错误事件相关接口：
 *  - POST /api/error/report：前端 SDK 上报入口（免鉴权，按 IP Bucket4j 限流：60次/分钟 + Origin 白名单）
 *  - GET  /api/errors：后台查询（鉴权）
 *  - PATCH /api/errors/{id}：标记已处理
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ErrorController {

    private final ErrorEventService errorEventService;
    private final ErrorEventMapper errorEventMapper;
    private final ProjectService projectService;
    private final TargetServersProperties properties;

    // 每 IP 每分钟 60 次上报限流
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public record ErrorReportDto(Long projectId, String type, String message,
                                 String stack, String url, String userAgent, Long timestamp) {}

    @PostMapping("/error/report")
    public ResponseEntity<?> report(@RequestBody ErrorReportDto dto, HttpServletRequest request) {
        if (dto.projectId() == null || dto.type() == null || dto.type().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "projectId and type are required"));
        }

        // Origin 白名单校验：仅允许被监控的两个前端站点上报
        if (!isOriginAllowed(request)) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "error", "来源不被允许"));
        }

        String clientIp = currentClientIp(request);
        ConsumptionProbe probe = buckets.computeIfAbsent(clientIp, k -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(60).refillGreedy(60, Duration.ofMinutes(1)).build())
                .build())
                .tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            return ResponseEntity.status(429).body(Map.of("ok", false, "error", "上报过于频繁"));
        }

        ErrorEvent evt = ErrorEvent.builder()
                .projectId(dto.projectId())
                .projectType("frontend")
                .type(dto.type())
                .message(dto.message())
                .stack(dto.stack())
                .url(dto.url())
                .build();
        var result = errorEventService.upsert(evt);
        return ResponseEntity.ok(Map.of("ok", true, "fingerprint", result.fingerprint(),
                "isNew", result.isNew(), "count", result.count()));
    }

    @GetMapping("/errors")
    public ResponseEntity<?> list(@RequestParam(required = false) Long projectId,
                                  @RequestParam(required = false) String type,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "100") int limit) {
        var query = Wrappers.<ErrorEvent>lambdaQuery()
                .orderByDesc(ErrorEvent::getLastSeenAt)
                .last("LIMIT " + Math.min(limit, 500));
        if (projectId != null) query.eq(ErrorEvent::getProjectId, projectId);
        if (type != null && !type.isBlank()) query.eq(ErrorEvent::getType, type);
        if (status != null && !status.isBlank()) query.eq(ErrorEvent::getStatus, status);
        List<ErrorEvent> list = errorEventMapper.selectList(query);

        // 附带项目名称，便于前端直接展示
        Map<Long, String> projectNames = new ConcurrentHashMap<>();
        projectService.listAll().forEach(p -> projectNames.put(p.getId(), p.getName()));
        List<Map<String, Object>> enriched = list.stream().map(e -> {
            Map<String, Object> m = new ConcurrentHashMap<>();
            m.put("id", e.getId());
            m.put("projectId", e.getProjectId());
            m.put("projectName", projectNames.getOrDefault(e.getProjectId(), "未知项目"));
            m.put("projectType", e.getProjectType());
            m.put("fingerprint", e.getFingerprint());
            m.put("type", e.getType());
            m.put("message", e.getMessage());
            m.put("stack", e.getStack());
            m.put("url", e.getUrl());
            m.put("firstSeenAt", e.getFirstSeenAt());
            m.put("lastSeenAt", e.getLastSeenAt());
            m.put("count", e.getCount());
            m.put("status", e.getStatus());
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("ok", true, "data", enriched));
    }

    @GetMapping("/errors/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        ErrorEvent evt = errorEventMapper.selectById(id);
        if (evt == null) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "error", "错误事件不存在"));
        }
        return ResponseEntity.ok(Map.of("ok", true, "data", evt));
    }

    @PatchMapping("/errors/{id}")
    public ResponseEntity<?> resolve(@PathVariable Long id) {
        errorEventService.resolve(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * 解析客户端真实 IP：优先 X-Forwarded-For 首段（经 Nginx 反代），否则取 RemoteAddr。
     * 注意：X-Forwarded-For 可被伪造，仅用于限流分组；生产建议在 Nginx 层覆写该头。
     */
    private String currentClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            String ip = (comma > 0 ? xff.substring(0, comma) : xff).trim();
            if (!ip.isEmpty()) return ip;
        }
        return request.getRemoteAddr();
    }

    /** Origin 白名单：与 CORS 配置共用 monitor.cors.allowed-origins；无 Origin 头（非浏览器/同源）时放行 */
    private boolean isOriginAllowed(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) return true;
        List<String> allowed = properties.getCors().getAllowedOrigins();
        return allowed != null && allowed.contains(origin);
    }
}
