package com.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("servers")
public class Server {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String host;
    private Integer sshPort;
    private String sshUser;
    private String authType;
    private String authSecret;
    private LocalDateTime createdAt;
}
