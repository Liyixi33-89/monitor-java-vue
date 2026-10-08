package com.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitor.entity.MetricsHost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MetricsHostMapper extends BaseMapper<MetricsHost> {

    @Select("SELECT * FROM metrics_host WHERE server_id = #{serverId} AND timestamp >= #{since} ORDER BY timestamp ASC")
    List<MetricsHost> findSince(@Param("serverId") Long serverId, @Param("since") LocalDateTime since);

    @Select("SELECT * FROM metrics_host WHERE server_id = #{serverId} ORDER BY timestamp DESC LIMIT 1")
    MetricsHost findLatest(@Param("serverId") Long serverId);

    /** 每台服务器最新一条主机指标（SQLite: 只取每 server_id 的最大 timestamp 记录） */
    @Select("SELECT m.* FROM metrics_host m JOIN " +
            "(SELECT server_id, MAX(timestamp) AS max_ts FROM metrics_host GROUP BY server_id) t " +
            "ON m.server_id = t.server_id AND m.timestamp = t.max_ts")
    List<MetricsHost> findLatestPerServer();
}
