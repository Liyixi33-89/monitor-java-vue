package com.monitor.collector;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.monitor.config.TargetServersProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * SSH 执行器 —— 连接池封装 + 超时 + 指数退避重试（最多 3 次：1s/2s/4s）
 * 对应需求文档"采集方式"：后端通过 SSH 定时连接服务器执行采集脚本，无需在目标机上部署 Agent。
 */
@Slf4j
@Component
public class SshExecutor {

    private final Map<Long, Session> pool = new ConcurrentHashMap<>();
    private final JSch jsch = new JSch();

    public ExecResult exec(TargetServersProperties.ServerConfig cfg, String command) throws Exception {
        return exec(cfg, command, 10_000);
    }

    public ExecResult exec(TargetServersProperties.ServerConfig cfg, String command, long timeoutMs) throws Exception {
        int attempts = 0;
        Exception lastErr = null;
        while (attempts < 3) {
            try {
                return doExec(cfg, command, timeoutMs);
            } catch (Exception e) {
                lastErr = e;
                pool.remove(cfg.getId());
                attempts++;
                log.warn("SSH exec failed (attempt {}/3) on {}: {}", attempts, cfg.getHost(), e.getMessage());
                if (attempts < 3) {
                    Thread.sleep((long) Math.pow(2, attempts) * 1000);
                }
            }
        }
        throw lastErr;
    }

    private ExecResult doExec(TargetServersProperties.ServerConfig cfg, String command, long timeoutMs) throws Exception {
        Session session = getSession(cfg);
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        channel.setOutputStream(out);
        channel.setErrStream(err);
        channel.connect((int) timeoutMs);

        long start = System.currentTimeMillis();
        try {
            while (!channel.isClosed()) {
                if (System.currentTimeMillis() - start > timeoutMs) {
                    throw new TimeoutException("SSH command timeout after " + timeoutMs + "ms: " + command);
                }
                Thread.sleep(50);
            }
        } finally {
            channel.disconnect();
        }
        return new ExecResult(
                out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8),
                channel.getExitStatus());
    }

    private Session getSession(TargetServersProperties.ServerConfig cfg) throws Exception {
        Session existing = pool.get(cfg.getId());
        if (existing != null && existing.isConnected()) {
            return existing;
        }
        Session session = jsch.getSession(cfg.getSshUser(), cfg.getHost(), cfg.getSshPort());
        jsch.addIdentity(cfg.getSshKeyPath());
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(10_000);
        pool.put(cfg.getId(), session);
        log.info("SSH connected: {}@{}:{}", cfg.getSshUser(), cfg.getHost(), cfg.getSshPort());
        return session;
    }

    public void closeAll() {
        pool.values().forEach(Session::disconnect);
        pool.clear();
    }

    @Data
    public static class ExecResult {
        private final String stdout;
        private final String stderr;
        private final int exitCode;
    }
}
