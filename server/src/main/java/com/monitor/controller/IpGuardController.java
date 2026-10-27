package com.monitor.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitor.entity.IpBan;
import com.monitor.entity.IpVisitStat;
import com.monitor.mapper.IpBanMapper;
import com.monitor.mapper.IpVisitStatMapper;
import com.monitor.service.IpGuardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IP 访问防护 —— 外部 IP 访问统计查询 + 封禁管理（手动封禁/解封）
 */
@RestController
@RequestMapping("/api/ip-guard")
@RequiredArgsConstructor
public class IpGuardController {

    private final IpVisitStatMapper statMapper;
    private final IpBanMapper banMapper;
    private final IpGuardService ipGuardService;

    /** 访问统计分页（默认当前小时窗口，可查全部） */
    @GetMapping("/visits")
    public ResponseEntity<?> visits(@RequestParam(required = false) String ip,
                                    @RequestParam(required = false) String hourWindow,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        var query = Wrappers.<IpVisitStat>lambdaQuery().orderByDesc(IpVisitStat::getTotalRequests);
        if (ip != null && !ip.isBlank()) query.eq(IpVisitStat::getIp, ip);
        if (hourWindow != null && !hourWindow.isBlank()) query.eq(IpVisitStat::getHourWindow, hourWindow);
        Page<IpVisitStat> result = statMapper.selectPage(new Page<>(Math.max(page, 1), size), query);
        return ResponseEntity.ok(Map.of("ok", true, "data", result.getRecords(),
                "total", result.getTotal(), "page", page, "pageSize", size));
    }

    /** 当前窗口 Top N 访问 IP（活跃监控） */
    @GetMapping("/visits/top")
    public ResponseEntity<?> topVisits(@RequestParam(defaultValue = "10") int limit,
                                       @RequestParam(required = false) String hourWindow) {
        String window = (hourWindow == null || hourWindow.isBlank())
                ? LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00"))
                : hourWindow;
        List<IpVisitStat> top = statMapper.selectList(Wrappers.<IpVisitStat>lambdaQuery()
                .eq(IpVisitStat::getHourWindow, window)
                .orderByDesc(IpVisitStat::getTotalRequests)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 100)));
        return ResponseEntity.ok(Map.of("ok", true, "data", top, "hourWindow", window));
    }

    /** 封禁记录列表（active 优先，可查全部） */
    @GetMapping("/bans")
    public ResponseEntity<?> bans(@RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        var query = Wrappers.<IpBan>lambdaQuery()
                .orderByDesc(IpBan::getBannedAt);
        if (status != null && !status.isBlank()) query.eq(IpBan::getStatus, status);
        Page<IpBan> result = banMapper.selectPage(new Page<>(Math.max(page, 1), size), query);
        return ResponseEntity.ok(Map.of("ok", true, "data", result.getRecords(),
                "total", result.getTotal(), "page", page, "pageSize", size));
    }

    /** 手动封禁（默认按配置时长；hours=0 永久） */
    @PostMapping("/bans")
    public ResponseEntity<?> ban(@RequestBody Map<String, Object> body) {
        String ip = (String) body.get("ip");
        if (ip == null || ip.isBlank() || !ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "IP 格式不正确"));
        }
        if (ipGuardService.hasActiveBan(ip)) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "该 IP 已在封禁中"));
        }
        String reason = (String) body.getOrDefault("reason", "手动封禁");
        ipGuardService.ban(ip, "manual", reason, null);
        return ResponseEntity.ok(Map.of("ok", true, "data", Map.of("ip", ip, "status", "banned")));
    }

    /** 手动解封 */
    @DeleteMapping("/bans/{ip}")
    public ResponseEntity<?> lift(@PathVariable String ip) {
        ipGuardService.liftBan(ip);
        return ResponseEntity.ok(Map.of("ok", true, "data", Map.of("ip", ip, "status", "lifted")));
    }
}
