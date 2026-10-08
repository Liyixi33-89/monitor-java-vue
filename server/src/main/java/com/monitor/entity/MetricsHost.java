package com.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("metrics_host")
public class MetricsHost {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long serverId;
    private LocalDateTime timestamp;
    private Double cpu;
    private Long memUsed;
    private Long memTotal;
    private Long diskUsed;
    private Long diskTotal;
    private Long netIn;
    private Long netOut;
    private Double load1;
    private Double load5;
    private Double load15;
}
