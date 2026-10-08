package com.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("health_checks")
public class HealthCheck {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long serverId;
    private String name;
    private String url;
    private LocalDateTime timestamp;
    private Integer statusCode;
    private Integer latencyMs;
    private Integer success; // 0/1
}
