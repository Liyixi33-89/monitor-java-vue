package com.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitor.entity.Server;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ServerMapper extends BaseMapper<Server> {
}
