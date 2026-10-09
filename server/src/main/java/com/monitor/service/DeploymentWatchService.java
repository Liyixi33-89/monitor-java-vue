package com.monitor.service;

import com.monitor.config.DeployWatchProperties;
import com.monitor.entity.Deployment;
import com.monitor.mapper.DeploymentMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * 发布自动监听 —— 监控本机 4 个被监控项目的部署产物文件变化，自动写入发布记录
 *
 * 原理：WatchService 监听产物所在目录的文件修改事件；
 * 前端项目监听 index.html（构建产物变化 = 发布），后端项目监听 jar/app.js（源码或构建产物变化 = 发布）。
 * 同一项目 5 分钟内多次变化合并为一条，避免 CI 中间步骤产生多条记录。
 *
 * 配置示例（application.yml）：
 *   monitor:
 *     deploy-watch:
 *       enabled: true
 *       watches:
 *         - project-id: 1
 *           path: /opt/mdviewer/frontend/index.html      # MD Viewer 前端
 *         - project-id: 2
 *           path: /opt/mdviewer/md-viewer-server.jar     # MD Viewer 后端
 *         - project-id: 3
 *           path: /var/www/taskmanager/frontend/dist/index.html  # TaskManager 前端
 *         - project-id: 4
 *           path: /var/www/taskmanager/backend/src/app.js        # TaskManager 后端
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentWatchService {

    private final DeploymentMapper deploymentMapper;
    private final DeployWatchProperties watchProperties;

    /** 监听项列表（由 DeployWatchProperties 绑定注入） */
    private List<String> watchSpecs = new ArrayList<>();

    /** 同项目合并窗口：5 分钟内的多次变化只记一条 */
    private static final Duration MERGE_WINDOW = Duration.ofMinutes(5);

    private WatchService watchService;
    private final Map<WatchKey, List<WatchTarget>> keyToTargets = new ConcurrentHashMap<>();
    private final Map<Long, LocalDateTime> lastRecordTime = new ConcurrentHashMap<>();
    private Thread watchThread;

    /** 一个监听目标：某个项目的某个文件/目录 */
    private record WatchTarget(Long projectId, Path watchedDir, String fileName) {
        boolean matches(Path changed) {
            return fileName == null || changed.getFileName().toString().equals(fileName);
        }
    }

    @PostConstruct
    public void start() {
        watchSpecs = watchProperties.getWatches();
        if (!watchProperties.isEnabled()) {
            log.info("Deploy watch disabled (monitor.deploy-watch.enabled=false)");
            return;
        }
        List<WatchTarget> targets = parseSpecs();
        if (targets.isEmpty()) {
            log.warn("Deploy watch enabled but no valid watches configured");
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
            // 按目录分组注册（同一目录可对应多个项目文件）
            Map<Path, List<WatchTarget>> byDir = new LinkedHashMap<>();
            for (WatchTarget t : targets) {
                byDir.computeIfAbsent(t.watchedDir(), k -> new ArrayList<>()).add(t);
            }
            for (var entry : byDir.entrySet()) {
                WatchKey key = entry.getKey().register(watchService, ENTRY_MODIFY, ENTRY_CREATE);
                keyToTargets.put(key, entry.getValue());
                log.info("Deploy watch registered: {} -> projects {}", entry.getKey(),
                        entry.getValue().stream().map(t -> "P" + t.projectId() + (t.fileName() != null ? "/" + t.fileName() : "/*")).toList());
            }
            watchThread = new Thread(this::runLoop, "deploy-watch");
            watchThread.setDaemon(true);
            watchThread.start();
            log.info("Deploy watch started with {} target(s)", targets.size());
        } catch (IOException | UnsupportedOperationException e) {
            log.error("Deploy watch failed to start: {}", e.getMessage());
        }
    }

    /** 解析 "1:/path/a,2:/path/b" 形式配置 */
    private List<WatchTarget> parseSpecs() {
        List<WatchTarget> targets = new ArrayList<>();
        for (String spec : watchSpecs) {
            if (spec == null || spec.isBlank()) continue;
            int idx = spec.indexOf(':');
            if (idx <= 0) {
                log.warn("Invalid deploy-watch spec (expect projectId:path): {}", spec);
                continue;
            }
            try {
                Long projectId = Long.valueOf(spec.substring(0, idx).trim());
                Path p = Paths.get(spec.substring(idx + 1).trim());
                if (Files.isDirectory(p)) {
                    targets.add(new WatchTarget(projectId, p, null));
                } else if (Files.isRegularFile(p)) {
                    targets.add(new WatchTarget(projectId, p.getParent(), p.getFileName().toString()));
                } else {
                    // 文件/目录尚不存在（如首次部署前）：注册其父目录，仍监听文件名
                    targets.add(new WatchTarget(projectId, p.getParent(), p.getFileName().toString()));
                    log.warn("Deploy watch target not exists yet, watching parent dir: {}", p);
                }
            } catch (Exception e) {
                log.warn("Invalid deploy-watch spec '{}': {}", spec, e.getMessage());
            }
        }
        return targets;
    }

    private void runLoop() {
        while (watchThread != null && !watchThread.isInterrupted()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                break;
            }
            List<WatchTarget> targets = keyToTargets.get(key);
            if (targets == null) {
                key.cancel();
                continue;
            }
            // 收集本轮事件命中的项目（同一轮多事件去重）
            Set<Long> hitProjects = new LinkedHashSet<>();
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == OVERFLOW) continue;
                Path changed = (Path) event.context();
                if (changed == null) continue;
                for (WatchTarget t : targets) {
                    if (t.matches(changed)) hitProjects.add(t.projectId());
                }
            }
            boolean valid = key.reset();
            if (!valid) {
                keyToTargets.remove(key);
                continue;
            }
            hitProjects.forEach(this::recordDeployment);
        }
    }

    /** 写入一条发布记录（带合并窗口去重） */
    private void recordDeployment(Long projectId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last = lastRecordTime.get(projectId);
        if (last != null && Duration.between(last, now).compareTo(MERGE_WINDOW) < 0) {
            log.debug("Deploy event for project {} within merge window, skipped", projectId);
            return;
        }
        lastRecordTime.put(projectId, now);
        Deployment d = new Deployment();
        d.setProjectId(projectId);
        d.setVersion(null);
        d.setOperator("auto");
        d.setRemark("监听到部署产物变化，自动登记");
        d.setResult("success");
        d.setDeployedAt(now);
        d.setCreatedAt(now);
        deploymentMapper.insert(d);
        log.info("Auto-recorded deployment for project {} at {}", projectId, now);
    }

    @PreDestroy
    public void stop() {
        if (watchThread != null) watchThread.interrupt();
        try {
            if (watchService != null) watchService.close();
        } catch (IOException ignored) {
        }
    }
}
