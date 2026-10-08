package com.monitor.util;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 日志增量 tail 的字节偏移量管理（内存 Map，进程重启后从 0 开始，可接受）。
 * 用于避免每次采集重复扫描整个日志文件。
 */
@Component
public class ByteOffsetTracker {

    private final Map<String, Long> offsets = new ConcurrentHashMap<>();

    public long get(String logPath) {
        return offsets.getOrDefault(logPath, 0L);
    }

    public void set(String logPath, long offset) {
        offsets.put(logPath, offset);
    }

    /** 若当前文件大小小于已记录偏移（文件被截断/轮转），重置为 0 */
    public long normalizeOffset(String logPath, long currentSize) {
        long offset = get(logPath);
        if (currentSize < offset) {
            offset = 0;
            set(logPath, 0);
        }
        return offset;
    }
}
