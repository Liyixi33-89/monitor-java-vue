package com.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("metrics_process")
public class MetricsProcess {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long serverId;
    private LocalDateTime timestamp;
    private String procName;
    private Integer pid;
    private Double cpu;
    private Long mem;
    private String status;         // online | offline | errored
    private Integer restartTime;
}
