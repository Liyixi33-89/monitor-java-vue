package com.monitor.service;

import com.monitor.service.notify.NotifyChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 根据 channelKey 分发到具体的通知渠道实现（wecom/dingtalk/email） */
@Slf4j
@Service
public class NotifyService {

    private final Map<String, NotifyChannel> channels;

    public NotifyService(List<NotifyChannel> channelList) {
        this.channels = channelList.stream()
                .collect(Collectors.toMap(NotifyChannel::getChannelKey, c -> c));
    }

    public void send(String channelKey, String message) {
        send(channelKey, message, null);
    }

    /** recipients 非空时邮件渠道发给指定告知人，空则走全局默认收件配置 */
    public void send(String channelKey, String message, String recipients) {
        if (channelKey == null) return;
        NotifyChannel channel = channels.get(channelKey);
        if (channel == null) {
            log.warn("Unknown notify channel: {}", channelKey);
            return;
        }
        channel.send(message, recipients);
    }
}
