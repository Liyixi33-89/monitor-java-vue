package com.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitor.entity.HealthCheck;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HealthCheckMapper extends BaseMapper<HealthCheck> {

    @Select("SELECT * FROM health_checks WHERE project_id = #{projectId} ORDER BY timestamp DESC LIMIT #{limit}")
    List<HealthCheck> findRecent(@Param("projectId") Long projectId, @Param("limit") int limit);

    @Select("SELECT success FROM health_checks WHERE project_id = #{projectId} ORDER BY timestamp DESC LIMIT #{n}")
    List<Integer> findRecentSuccessFlags(@Param("projectId") Long projectId, @Param("n") int n);
}
