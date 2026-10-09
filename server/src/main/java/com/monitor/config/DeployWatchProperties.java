package com.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 发布自动监听配置（monitor.deploy-watch.*）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "monitor.deploy-watch")
public class DeployWatchProperties {
    private boolean enabled = false;
    /** 监听项列表，格式：projectId:绝对路径 */
    private List<String> watches = new ArrayList<>();
}
