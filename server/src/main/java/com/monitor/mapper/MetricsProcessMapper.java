package com.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitor.entity.MetricsProcess;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MetricsProcessMapper extends BaseMapper<MetricsProcess> {

    @Select("SELECT * FROM metrics_process WHERE project_id = #{projectId} AND timestamp >= #{since} ORDER BY timestamp ASC")
    List<MetricsProcess> findSince(@Param("projectId") Long projectId, @Param("since") LocalDateTime since);

    @Select("SELECT * FROM metrics_process WHERE project_id = #{projectId} ORDER BY timestamp DESC LIMIT 1")
    MetricsProcess findLatest(@Param("projectId") Long projectId);
}
