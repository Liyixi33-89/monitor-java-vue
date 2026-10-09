package com.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发布记录实体 —— 手动登记每次发布动作，用于追溯"发布与故障时间线"关系
 * result: success | rollback | failed
 */
@Data
@TableName("deployments")
public class Deployment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String version;        // 版本号/commit 简写
    private String operator;       // 发布人
    private String remark;         // 发布说明
    private String result;         // success | rollback | failed
    private LocalDateTime deployedAt;
    private LocalDateTime createdAt;
}
