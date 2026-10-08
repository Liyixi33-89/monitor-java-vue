package com.monitor.controller;

import com.monitor.entity.Server;
import com.monitor.mapper.ServerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ServerController {

    private final ServerMapper serverMapper;

    @GetMapping("/servers")
    public ResponseEntity<?> listServers() {
        List<Server> servers = serverMapper.selectList(null);
        // 过滤敏感字段：凭证信息不进入任何 API 响应
        servers.forEach(s -> s.setAuthSecret(null));
        return ResponseEntity.ok(Map.of("ok", true, "data", servers));
    }
}
