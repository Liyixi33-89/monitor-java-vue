package com.monitor.service.notify;

import com.monitor.config.TargetServersProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 邮件通知渠道：告警触发/恢复时发送到配置的收件邮箱
 * SMTP 参数见 application.yml 的 monitor.notify.email.*（spring.mail.* 不使用，避免与业务配置耦合）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotifyChannel implements NotifyChannel {

    private final JavaMailSender mailSender;
    private final TargetServersProperties properties;

    @Override
    public String getChannelKey() {
        return "email";
    }

    @Override
    public void send(String message) {
        TargetServersProperties.Notify notify = properties.getNotify();
        String from = notify.getEmailFrom();
        String to = notify.getEmailTo();
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            log.warn("Email notify not configured (from/to missing), skip: {}", message);
            return;
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to.split(","));
            helper.setSubject(subjectOf(message));
            helper.setText(message, false);
            mailSender.send(mime);
            log.info("Alert email sent to {}: {}", to, subjectOf(message));
        } catch (Exception e) {
            log.error("Email notify failed: {}", e.getMessage());
        }
    }

    /** 从消息中提取主题：去掉 emoji 前缀，取第一行，超出长度截断 */
    private String subjectOf(String message) {
        String first = message.lines().findFirst().orElse("监控告警");
        String s = first.replaceAll("^[🔴✅🟡]\\s*", "").trim();
        return s.length() > 60 ? s.substring(0, 60) : s;
    }
}
