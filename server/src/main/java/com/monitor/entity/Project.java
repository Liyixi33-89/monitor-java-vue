package com.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目实体 —— 按服务粒度拆分，前后端各一行
 * type: frontend | backend
 */
@Data
@TableName("projects")
public class Project {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String groupName;      // 'md-viewer' | 'taskmanager'
    private String name;           // 'MD Viewer 前端' / 'MD Viewer 后端' ...
    private String type;           // frontend | backend
    private Long serverId;
    private String runtime;        // static | springboot | node-pm2 | node-koa
    private String accessUrl;
    private String procName;       // pm2 进程名 / systemd 服务名
    private String logPath;
    private String healthCheckUrl;
    private String owner;
    private LocalDateTime createdAt;
}
