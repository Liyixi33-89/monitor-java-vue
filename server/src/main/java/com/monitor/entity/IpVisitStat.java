package com.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 外部 IP 按小时窗口的访问统计（nginx access log 聚合） */
@Data
@Builder
@TableName("ip_visit_stats")
public class IpVisitStat {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ip;
    /** 'yyyy-MM-dd HH:00' 聚合窗口 */
    private String hourWindow;
    private Integer totalRequests;
    /** 驼峰转下划线对 count4xx 不生效（数字前无边界），显式映射 */
    @TableField("count_4xx")
    private Integer count4xx;
    @TableField("count_5xx")
    private Integer count5xx;
    private String samplePaths;
    private LocalDateTime updatedAt;
}
