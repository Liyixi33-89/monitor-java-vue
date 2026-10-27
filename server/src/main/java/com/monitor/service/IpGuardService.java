package com.monitor.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.monitor.collector.SshExecutor;
import com.monitor.config.IpGuardProperties;
import com.monitor.config.TargetServersProperties;
import com.monitor.entity.ErrorEvent;
import com.monitor.entity.IpBan;
import com.monitor.entity.IpVisitStat;
import com.monitor.mapper.IpBanMapper;
import com.monitor.mapper.IpVisitStatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IP 访问防护：
 *  - 消费 NginxLogCollector 已 tail 的原始日志行，按小时窗口聚合外部 IP 访问统计
 *  - 超 banThreshold 的 IP 自动执行 iptables 封禁（仅 web 端口，保留 SSH）
 *  - 到期自动解封；服务重启后从 DB 恢复 active 封禁的 iptables 规则
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IpGuardService {

    private static final Pattern IP_HEAD = Pattern.compile("^(\\d{1,3}(?:\\.\\d{1,3}){3})\\s");
    private static final Pattern STATUS_REGEX = Pattern.compile("\"\\s(\\d{3})\\s\\d+");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");

    private final SshExecutor sshExecutor;
    private final IpGuardProperties props;
    private final IpVisitStatMapper statMapper;
    private final IpBanMapper banMapper;
    private final ErrorEventService errorEventService;
    private final TargetServersProperties properties;

    /** 本进程已执行过 iptables 封禁的 IP（避免重复下发规则） */
    private final Set<String> enforcedIps = new HashSet<>();

    /** 判断是否外部 IP：本机/内网/白名单不算 */
    public boolean isExternalIp(String ip) {
        if (ip == null || ip.isBlank()) return false;
        if (ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")
                || ip.equals("::1")) return false;
        // 172.16-31.x.x 私网段
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length > 1) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) return false;
                } catch (NumberFormatException ignored) {}
            }
        }
        // 服务器自身（nginx 反代日志里会出现本机外网 IP）
        String selfHost = properties.getTargetServers().get(0).getHost();
        if (ip.equals(selfHost)) return false;
        return !inWhitelist(ip);
    }

    private boolean inWhitelist(String ip) {
        for (String w : props.getWhitelist()) {
            if (w.isBlank()) continue;
            if (w.endsWith("*") && ip.startsWith(w.substring(0, w.length() - 1))) return true;
            if (w.equals(ip)) return true;
        }
        return false;
    }

    /**
     * 聚合一批日志行到 ip_visit_stats（按 ip+hourWindow 累加）。
     * 只统计外部 IP；返回统计后的各 IP 当前窗口计数（供封禁判定）。
     */
    public Map<String, Integer> aggregateLines(List<String> lines) {
        String hourWindow = LocalDateTime.now().format(HOUR_FMT);
        Map<String, int[]> delta = new LinkedHashMap<>();
        Map<String, List<String>> samples = new HashMap<>();

        for (String line : lines) {
            if (line.isBlank()) continue;
            Matcher ipM = IP_HEAD.matcher(line);
            if (!ipM.find()) continue;
            String ip = ipM.group(1);
            if (!isExternalIp(ip)) continue;

            int status = 0;
            Matcher stM = STATUS_REGEX.matcher(line);
            if (stM.find()) status = Integer.parseInt(stM.group(1));

            int[] counters = delta.computeIfAbsent(ip, k -> new int[3]);
            counters[0]++; // total
            if (status >= 400 && status < 500) counters[1]++;
            if (status >= 500) counters[2]++;

            samples.computeIfAbsent(ip, k -> new ArrayList<>()).add(line.length() > 300 ? line.substring(0, 300) : line);
        }
        if (delta.isEmpty()) return Map.of();

        LocalDateTime now = LocalDateTime.now();
        Map<String, Integer> windowTotals = new HashMap<>();
        for (var entry : delta.entrySet()) {
            String ip = entry.getKey();
            int[] c = entry.getValue();

            IpVisitStat existing = statMapper.selectOne(Wrappers.<IpVisitStat>lambdaQuery()
                    .eq(IpVisitStat::getIp, ip).eq(IpVisitStat::getHourWindow, hourWindow));
            if (existing == null) {
                statMapper.insert(IpVisitStat.builder()
                        .ip(ip).hourWindow(hourWindow)
                        .totalRequests(c[0]).count4xx(c[1]).count5xx(c[2])
                        .samplePaths(joinSamples(samples.get(ip)))
                        .updatedAt(now)
                        .build());
                windowTotals.put(ip, c[0]);
            } else {
                existing.setTotalRequests(existing.getTotalRequests() + c[0]);
                existing.setCount4xx(existing.getCount4xx() + c[1]);
                existing.setCount5xx(existing.getCount5xx() + c[2]);
                existing.setUpdatedAt(now);
                statMapper.updateById(existing);
                windowTotals.put(ip, existing.getTotalRequests());
            }
        }
        return windowTotals;
    }

    /** 超阈值的 IP 自动封禁（在窗口统计刷新后调用） */
    public void enforceBanIfNeeded(Map<String, Integer> windowTotals) {
        if (!props.isEnabled()) return;
        for (var entry : windowTotals.entrySet()) {
            String ip = entry.getKey();
            int total = entry.getValue();
            if (total < props.getBanThreshold()) continue;
            if (hasActiveBan(ip)) continue;

            ban(ip, "auto", String.format("单小时请求数 %d 超过阈值 %d", total, props.getBanThreshold()), total);
        }
    }

    /** 执行封禁：写 DB + SSH 下发 iptables 规则 + 产出错误事件 */
    public void ban(String ip, String source, String reason, Integer requestCount) {
        if (!isExternalIp(ip)) {
            log.info("IP {} 属于内网/白名单，跳过封禁", ip);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = props.getBanHours() > 0 ? now.plusHours(props.getBanHours()) : null;

        banMapper.insert(IpBan.builder()
                .ip(ip).reason(reason == null ? "" : reason)
                .source(source).bannedAt(now)
                .expiresAt(expires).status("active")
                .requestCount(requestCount == null ? 0 : requestCount)
                .build());

        boolean enforced = applyIptables(ip);
        if (!enforced) {
            log.error("IP {} 封禁记录已入库，但 iptables 规则下发失败", ip);
        }

        errorEventService.upsert(ErrorEvent.builder()
                .projectId(1L).projectType("frontend")
                .type("ip_banned")
                .message(String.format("IP %s 已被封禁（%s）：%s", ip, source, reason))
                .stack("").url("")
                .build());
        log.warn("IP 已封禁: {} source={} reason={} count={}", ip, source, reason, requestCount);
    }

    /** 解封：更新 DB 状态 + 移除 iptables 规则 */
    public void liftBan(String ip) {
        List<IpBan> actives = banMapper.selectList(Wrappers.<IpBan>lambdaQuery()
                .eq(IpBan::getIp, ip).eq(IpBan::getStatus, "active"));
        for (IpBan b : actives) {
            b.setStatus("lifted");
            b.setLiftedAt(LocalDateTime.now());
            banMapper.updateById(b);
        }
        removeIptables(ip);
        enforcedIps.remove(ip);
        log.info("IP 已解封: {}", ip);
    }

    /** 到期自动解封（定时任务调用） */
    public void liftExpired() {
        List<IpBan> expired = banMapper.selectList(Wrappers.<IpBan>lambdaQuery()
                .eq(IpBan::getStatus, "active")
                .isNotNull(IpBan::getExpiresAt)
                .le(IpBan::getExpiresAt, LocalDateTime.now())
                .last("LIMIT 50"));
        for (IpBan b : expired) {
            log.info("IP {} 封禁到期，自动解封", b.getIp());
            liftBan(b.getIp());
        }
    }

    /** 服务重启后恢复所有 active 封禁的 iptables 规则（幂等） */
    public void restoreActiveBans() {
        if (!props.isEnabled()) return;
        List<IpBan> actives = banMapper.selectList(Wrappers.<IpBan>lambdaQuery()
                .eq(IpBan::getStatus, "active"));
        int restored = 0;
        for (IpBan b : actives) {
            if (applyIptables(b.getIp())) restored++;
        }
        log.info("IP 封禁规则恢复完成: {} 条（DB active {} 条）", restored, actives.size());
    }

    public boolean hasActiveBan(String ip) {
        return banMapper.selectCount(Wrappers.<IpBan>lambdaQuery()
                .eq(IpBan::getIp, ip).eq(IpBan::getStatus, "active")) > 0;
    }

    /** SSH 下发 iptables 封禁规则：每个 web 端口一条（重复插入会被 -C 检查跳过） */
    private boolean applyIptables(String ip) {
        if (enforcedIps.contains(ip)) return true;
        try {
            var serverCfg = properties.getTargetServers().get(0);
            StringBuilder cmd = new StringBuilder();
            for (int port : props.getBlockPorts()) {
                cmd.append(String.format("iptables -C INPUT -s %s -p tcp --dport %d -j DROP 2>/dev/null || iptables -I INPUT -s %s -p tcp --dport %d -j DROP; ", ip, port, ip, port));
            }
            sshExecutor.exec(serverCfg, cmd.toString());
            enforcedIps.add(ip);
            return true;
        } catch (Exception e) {
            log.error("iptables 封禁失败 ip={}: {}", ip, e.getMessage());
            return false;
        }
    }

    private void removeIptables(String ip) {
        try {
            var serverCfg = properties.getTargetServers().get(0);
            StringBuilder cmd = new StringBuilder();
            for (int port : props.getBlockPorts()) {
                cmd.append(String.format("while iptables -C INPUT -s %s -p tcp --dport %d -j DROP 2>/dev/null; do iptables -D INPUT -s %s -p tcp --dport %d -j DROP; done; ", ip, port, ip, port));
            }
            sshExecutor.exec(serverCfg, cmd.toString());
        } catch (Exception e) {
            log.error("iptables 解封失败 ip={}: {}", ip, e.getMessage());
        }
    }

    private String joinSamples(List<String> lines) {
        if (lines == null || lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }
}
