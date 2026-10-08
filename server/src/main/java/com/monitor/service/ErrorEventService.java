package com.monitor.service;

import com.monitor.entity.ErrorEvent;
import com.monitor.mapper.ErrorEventMapper;
import com.monitor.util.FingerprintUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 错误事件聚合服务。
 * 所有采集来源（前端 SDK 上报 / 后端日志 tail / pm2 进程重启 / nginx 5xx 统计）最终都通过 upsert 写入，
 * 统一走指纹聚合逻辑：
 *   - 指纹已存在（同 project_id + fingerprint）：count+1，更新 last_seen_at，若之前 resolved 则重新打开为 open
 *   - 指纹不存在：插入新记录，first_seen_at = last_seen_at = now
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorEventService {

    private final ErrorEventMapper errorEventMapper;

    public UpsertResult upsert(ErrorEvent evt) {
        String fingerprint = FingerprintUtil.compute(evt.getProjectId(), evt.getType(), evt.getMessage(), evt.getStack());
        LocalDateTime now = LocalDateTime.now();

        ErrorEvent existing = errorEventMapper.findByFingerprint(evt.getProjectId(), fingerprint);
        if (existing != null) {
            errorEventMapper.incrementCount(existing.getId(), now.toString());
            log.debug("ErrorEvent incremented: projectId={}, fingerprint={}, newCount={}",
                    evt.getProjectId(), fingerprint, existing.getCount() + 1);
            return new UpsertResult(fingerprint, false, existing.getCount() + 1, existing.getId());
        }

        ErrorEvent toInsert = ErrorEvent.builder()
                .projectId(evt.getProjectId())
                .projectType(evt.getProjectType())
                .fingerprint(fingerprint)
                .type(evt.getType())
                .message(evt.getMessage())
                .stack(evt.getStack())
                .url(evt.getUrl())
                .firstSeenAt(now)
                .lastSeenAt(now)
                .count(1)
                .status("open")
                .build();
        errorEventMapper.insert(toInsert);
        log.info("New ErrorEvent created: projectId={}, type={}, fingerprint={}",
                evt.getProjectId(), evt.getType(), fingerprint);
        return new UpsertResult(fingerprint, true, 1, toInsert.getId());
    }

    /** 查询某指纹最近 N 分钟内的发生次数（供告警规则判定"5 分钟内同一错误 >=N 次"） */
    public int getRecentCount(Long projectId, String fingerprint, int minutes) {
        String since = LocalDateTime.now().minusMinutes(minutes).toString();
        return errorEventMapper.countRecent(projectId, fingerprint, since);
    }

    /**
     * 统计项目最近 windowSec 秒内错误事件的累计发生次数（sum(count)，聚合后的总次数）。
     * types 为空时统计全部类型；供 error_rate / log_keyword 告警规则判定。
     */
    public int getRecentErrorCount(Long projectId, int windowSec, String... types) {
        String since = LocalDateTime.now().minusSeconds(windowSec).toString();
        java.util.List<String> typeList = (types == null || types.length == 0) ? null : java.util.Arrays.asList(types);
        return errorEventMapper.sumRecentCount(projectId, since, typeList);
    }

    public void resolve(Long id) {
        ErrorEvent evt = errorEventMapper.selectById(id);
        if (evt != null) {
            evt.setStatus("resolved");
            errorEventMapper.updateById(evt);
        }
    }

    public record UpsertResult(String fingerprint, boolean isNew, int count, Long id) {}
}
