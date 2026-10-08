package com.monitor.service.notify;

import com.monitor.config.TargetServersProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** 企业微信机器人 Webhook 通知渠道 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeComNotifyChannel implements NotifyChannel {

    private final TargetServersProperties properties;
    private final RestClient restClient = RestClient.builder().build();

    @Override
    public String getChannelKey() {
        return "wecom";
    }

    @Override
    public void send(String message) {
        String url = properties.getNotify().getWecomWebhookUrl();
        if (url == null || url.isBlank()) {
            log.warn("WeCom webhook url not configured, skip notify: {}", message);
            return;
        }
        try {
            restClient.post()
                    .uri(url)
                    .body(Map.of("msgtype", "text", "text", Map.of("content", message)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("WeCom notify failed: {}", e.getMessage());
        }
    }
}
