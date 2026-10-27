package com.monitor.job;

import com.monitor.service.IpGuardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * IP 防护定时任务：
 *  - 每 5 分钟检查过期封禁并自动解封
 *  - 服务启动完成后恢复 active 封禁的 iptables 规则（重启后规则不丢）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IpGuardJob {

    private final IpGuardService ipGuardService;

    @Scheduled(fixedRate = 300_000, initialDelay = 120_000)
    public void liftExpiredBans() {
        try {
            ipGuardService.liftExpired();
        } catch (Exception e) {
            log.error("IpGuard liftExpired failed: {}", e.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreOnStartup() {
        try {
            ipGuardService.restoreActiveBans();
        } catch (Exception e) {
            log.error("IpGuard restoreOnStartup failed: {}", e.getMessage());
        }
    }
}
