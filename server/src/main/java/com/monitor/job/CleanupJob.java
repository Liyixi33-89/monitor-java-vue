package com.monitor.job;

import com.monitor.mapper.HealthCheckMapper;
import com.monitor.mapper.MetricsHostMapper;
import com.monitor.mapper.MetricsProcessMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 数据清理任务：每日凌晨 1 点清理 7 天前的明细数据 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupJob {

    private final JdbcTemplate jdbcTemplate;

    private static final String[] CLEANUP_SQL = {
            "DELETE FROM metrics_host WHERE timestamp < datetime('now', '-7 days')",
            "DELETE FROM metrics_process WHERE timestamp < datetime('now', '-7 days')",
            "DELETE FROM health_checks WHERE timestamp < datetime('now', '-7 days')",
            "DELETE FROM alert_logs WHERE triggered_at < datetime('now', '-30 days')"
    };

    @Scheduled(cron = "0 0 1 * * ?")
    public void run() {
        for (String sql : CLEANUP_SQL) {
            try {
                int deleted = jdbcTemplate.update(sql);
                log.info("Cleanup: {} rows deleted by [{}]", deleted, sql);
            } catch (Exception e) {
                log.error("Cleanup failed [{}]: {}", sql, e.getMessage());
            }
        }
    }
}
