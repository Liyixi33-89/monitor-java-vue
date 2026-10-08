package com.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alert_rules")
public class AlertRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;        // NULL = 全局/主机级规则
    private String metricType;     // cpu|mem|disk|health|process_restart|error_rate|log_keyword
    private String condition;      // > | < | =
    private Double threshold;
    private Integer durationSec;
    private String notifyChannel;  // wecom | dingtalk | email
    private Integer enabled;
    private LocalDateTime createdAt;
}
