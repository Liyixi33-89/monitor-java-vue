package com.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 读取 application.yml 中 monitor.target-servers 配置
 * 作为被监控服务器的静态初始配置来源（实际运行时以数据库 servers 表为准，此配置主要用于首次初始化）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "monitor")
public class TargetServersProperties {

    private List<ServerConfig> targetServers;
    private Jwt jwt = new Jwt();
    private Notify notify = new Notify();
    private Cors cors = new Cors();

    @Data
    public static class ServerConfig {
        private Long id;
        private String name;
        private String host;
        private Integer sshPort = 22;
        private String sshUser = "root";
        private String sshKeyPath;
    }

    @Data
    public static class Jwt {
        private String secret;
        private long expireMinutes = 720;
    }

    @Data
    public static class Notify {
        private String wecomWebhookUrl;
        private String dingtalkWebhookUrl;
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins;
    }
}
