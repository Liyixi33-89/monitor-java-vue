package com.monitor.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/** 管理在线 WebSocket 连接，支持向所有连接广播实时数据 */
@Slf4j
@Component
public class WsSessionRegistry {

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WS session registered: {}, total: {}", session.getId(), sessions.size());
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session.getId());
        log.info("WS session unregistered: {}, total: {}", session.getId(), sessions.size());
    }

    /** 广播 JSON 消息给所有在线客户端（单连接发送失败时静默移除） */
    public void broadcast(String json) {
        sessions.values().forEach(s -> {
            try {
                synchronized (s) {
                    if (s.isOpen()) {
                        s.sendMessage(new TextMessage(json));
                    }
                }
            } catch (IOException e) {
                sessions.remove(s.getId());
            }
        });
    }
}
