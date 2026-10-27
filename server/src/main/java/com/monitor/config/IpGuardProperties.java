package com.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * IP 访问防护配置（monitor.ip-guard.*）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "monitor.ip-guard")
public class IpGuardProperties {
    /** 总开关：关闭后只统计不封禁 */
    private boolean enabled = true;
    /** 单 IP 单小时窗口请求数阈值，超过自动封禁 */
    private int banThreshold = 500;
    /** 自动封禁时长（小时）；0 = 永久 */
    private int banHours = 24;
    /** 封禁的 web 端口（不封 22，保留 SSH 逃生通道） */
    private List<Integer> blockPorts = new ArrayList<>(List.of(80, 443, 8081, 4000));
    /** 白名单：永不封禁（精确 IP 或 CIDR 前缀匹配） */
    private List<String> whitelist = new ArrayList<>();
}
