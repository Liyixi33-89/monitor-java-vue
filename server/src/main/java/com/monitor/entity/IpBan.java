package com.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** IP 封禁记录：自动判定（超阈值）或手动封禁，到期自动解封 */
@Data
@Builder
@TableName("ip_bans")
public class IpBan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ip;
    private String reason;
    /** auto(自动判定) | manual(手动) */
    private String source;
    private LocalDateTime bannedAt;
    /** 到期自动解封；NULL = 永久 */
    private LocalDateTime expiresAt;
    /** active | lifted */
    private String status;
    private LocalDateTime liftedAt;
    /** 触发封禁时的窗口请求数 */
    private Integer requestCount;
}
