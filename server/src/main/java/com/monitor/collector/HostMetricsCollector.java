package com.monitor.collector;

import com.monitor.config.TargetServersProperties;
import com.monitor.entity.MetricsHost;
import com.monitor.mapper.MetricsHostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 主机资源指标采集器：CPU / 内存 / 磁盘 / 网络 / 负载。
 * 一次 SSH 往返执行组合命令，减少连接开销。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HostMetricsCollector {

    private final SshExecutor sshExecutor;
    private final MetricsHostMapper metricsHostMapper;

    private static final String CMD =
            "echo '##CPU##'; top -bn1 | grep 'Cpu(s)'; " +
            "echo '##MEM##'; free -b; " +
            "echo '##DISK##'; df -B1 /; " +
            "echo '##NET##'; cat /proc/net/dev; " +
            "echo '##LOAD##'; cat /proc/loadavg";

    private static final Pattern CPU_IDLE_PATTERN = Pattern.compile("([\\d.]+)\\s*%?\\s*id");

    public void collect(TargetServersProperties.ServerConfig serverCfg, Long serverId) {
        try {
            SshExecutor.ExecResult result = sshExecutor.exec(serverCfg, CMD);
            String[] sections = result.getStdout().split("##\\w+##");
            // sections[0] 为空串（命令前的 echo 标记之前），sections[1]=CPU, [2]=MEM, [3]=DISK, [4]=NET, [5]=LOAD
            MetricsHost m = new MetricsHost();
            m.setServerId(serverId);
            m.setTimestamp(LocalDateTime.now());

            if (sections.length > 1) parseCpu(sections[1], m);
            if (sections.length > 2) parseMem(sections[2], m);
            if (sections.length > 3) parseDisk(sections[3], m);
            if (sections.length > 4) parseNet(sections[4], m);
            if (sections.length > 5) parseLoad(sections[5], m);

            metricsHostMapper.insert(m);
        } catch (Exception e) {
            log.error("HostMetricsCollector failed for server {}: {}", serverCfg.getHost(), e.getMessage());
        }
    }

    private void parseCpu(String section, MetricsHost m) {
        // 示例: %Cpu(s):  3.2 us,  1.1 sy,  0.0 ni, 95.4 id, ...
        Matcher mm = CPU_IDLE_PATTERN.matcher(section);
        if (mm.find()) {
            double idle = Double.parseDouble(mm.group(1));
            m.setCpu(Math.round((100 - idle) * 100) / 100.0);
        }
    }

    private void parseMem(String section, MetricsHost m) {
        // free -b 输出: Mem: total used free shared buff/cache available
        for (String line : section.split("\n")) {
            if (line.trim().startsWith("Mem:")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 3) {
                    m.setMemTotal(Long.parseLong(parts[1]));
                    m.setMemUsed(Long.parseLong(parts[2]));
                }
            }
        }
    }

    private void parseDisk(String section, MetricsHost m) {
        // df -B1 / 输出: Filesystem 1B-blocks Used Available Use% Mounted
        for (String line : section.split("\n")) {
            if (line.trim().isEmpty() || line.contains("Filesystem")) continue;
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 4) {
                try {
                    m.setDiskTotal(Long.parseLong(parts[1]));
                    m.setDiskUsed(Long.parseLong(parts[2]));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private void parseNet(String section, MetricsHost m) {
        // /proc/net/dev: 累加除 lo 外所有接口的 rx/tx bytes
        long rxTotal = 0, txTotal = 0;
        for (String line : section.split("\n")) {
            if (!line.contains(":")) continue;
            String[] colonSplit = line.split(":");
            String iface = colonSplit[0].trim();
            if (iface.equals("lo") || iface.isEmpty()) continue;
            String[] parts = colonSplit[1].trim().split("\\s+");
            if (parts.length >= 9) {
                try {
                    rxTotal += Long.parseLong(parts[0]);
                    txTotal += Long.parseLong(parts[8]);
                } catch (NumberFormatException ignored) {}
            }
        }
        m.setNetIn(rxTotal);
        m.setNetOut(txTotal);
    }

    private void parseLoad(String section, MetricsHost m) {
        // /proc/loadavg: 0.12 0.08 0.05 1/234 5678
        String[] parts = section.trim().split("\\s+");
        if (parts.length >= 3) {
            m.setLoad1(Double.parseDouble(parts[0]));
            m.setLoad5(Double.parseDouble(parts[1]));
            m.setLoad15(Double.parseDouble(parts[2]));
        }
    }
}
