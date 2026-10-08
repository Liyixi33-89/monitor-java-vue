package com.monitor.job;

import com.monitor.service.AlertEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 告警规则评估任务：每 30s 扫描一次 enabled 规则 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEvaluateJob {

    private final AlertEngineService alertEngineService;

    @Scheduled(fixedRate = 30_000, initialDelay = 20_000)
    public void run() {
        alertEngineService.evaluateAll();
    }
}
