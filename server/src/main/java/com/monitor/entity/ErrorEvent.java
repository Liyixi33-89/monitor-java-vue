package com.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 错误事件（前端 JS 错误 + 后端异常/日志关键字 + 5xx），按 project_id+fingerprint 聚合去重
 * type: js_error|unhandledrejection|resource_error|api_error|exception|process_crash|http_5xx|log_error
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("error_events")
public class ErrorEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String projectType;    // frontend | backend
    private String fingerprint;
    private String type;
    private String message;
    private String stack;
    private String url;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private Integer count;
    private String status;         // open | resolved
}
