package com.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitor.entity.AlertLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AlertLogMapper extends BaseMapper<AlertLog> {

    @Select("SELECT * FROM alert_logs WHERE rule_id = #{ruleId} AND resolved_at IS NULL ORDER BY triggered_at DESC LIMIT 1")
    AlertLog findOpenByRule(@Param("ruleId") Long ruleId);
}
