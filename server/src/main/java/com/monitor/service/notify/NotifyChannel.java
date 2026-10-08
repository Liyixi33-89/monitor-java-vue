package com.monitor.service.notify;

/** 通知渠道统一接口 */
public interface NotifyChannel {
    String getChannelKey(); // wecom | dingtalk

    void send(String message);
}
