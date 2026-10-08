package com.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitor.entity.ErrorEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ErrorEventMapper extends BaseMapper<ErrorEvent> {

    @Select("SELECT * FROM error_events WHERE project_id = #{projectId} AND fingerprint = #{fingerprint}")
    ErrorEvent findByFingerprint(@Param("projectId") Long projectId, @Param("fingerprint") String fingerprint);

    @Update("UPDATE error_events SET count = count + 1, last_seen_at = #{now}, status = 'open' WHERE id = #{id}")
    int incrementCount(@Param("id") Long id, @Param("now") String now);

    @Select("SELECT COUNT(*) FROM error_events WHERE project_id = #{projectId} AND fingerprint = #{fingerprint} AND last_seen_at >= #{since}")
    int countRecent(@Param("projectId") Long projectId, @Param("fingerprint") String fingerprint, @Param("since") String since);

    /** 统计项目最近窗口内错误事件的累计发生次数（可选按类型过滤）；窗口按 last_seen_at 判定 */
    @Select("<script>" +
            "SELECT COALESCE(SUM(count), 0) FROM error_events WHERE project_id = #{projectId} AND last_seen_at >= #{since} AND status = 'open'" +
            "<if test='types != null and types.size() > 0'> AND type IN " +
            "<foreach collection='types' item='t' open='(' separator=',' close=')'>#{t}</foreach>" +
            "</if>" +
            "</script>")
    int sumRecentCount(@Param("projectId") Long projectId, @Param("since") String since,
                       @Param("types") java.util.List<String> types);
}
