package com.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitor.entity.AlertRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AlertRuleMapper extends BaseMapper<AlertRule> {

    @Select("SELECT * FROM alert_rules WHERE enabled = 1")
    List<AlertRule> findEnabled();
}
