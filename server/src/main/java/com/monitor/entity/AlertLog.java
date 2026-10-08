package com.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alert_logs")
public class AlertLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ruleId;
    private Long serverId;
    private Long projectId;
    private String projectType;
    private String level;          // warning | critical
    private String message;
    private LocalDateTime triggeredAt;
    private LocalDateTime resolvedAt;
}
