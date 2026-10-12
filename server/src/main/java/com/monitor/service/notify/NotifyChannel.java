package com.monitor.service.notify;

/** 通知渠道统一接口 */
public interface NotifyChannel {
    String getChannelKey(); // wecom | dingtalk | email

    void send(String message);

    /**
     * 带收件人的发送（邮件渠道用）：recipients 为空时各渠道走默认配置
     * 默认实现忽略 recipients，保持 wecom/dingtalk 兼容
     */
    default void send(String message, String recipients) {
        send(message);
    }
}
