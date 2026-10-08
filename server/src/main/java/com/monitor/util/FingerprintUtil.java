package com.monitor.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 错误指纹计算 —— 用于错误事件聚合去重
 *
 * fingerprint = sha1( projectId + ':' + type + ':' + normalize(message) + ':' + normalize(stack前3行) )
 *
 * normalize：数字/十六进制地址替换为占位符，忽略行号/ID/时间戳等动态值，避免同一类错误被误判为不同错误。
 */
public final class FingerprintUtil {

    private FingerprintUtil() {}

    public static String compute(Object projectId, String type, String message, String stack) {
        String key = projectId + "::" + type + "::" + normalize(message) + "::" + normalize(stackHead(stack, 3));
        return sha1Hex(key);
    }

    public static String normalize(String text) {
        if (text == null) return "";
        String s = text
                .replaceAll("0x[0-9a-fA-F]+", "#")
                .replaceAll("\\d+", "#")
                .replaceAll("\\s+", " ")
                .trim();
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    public static String stackHead(String stack, int lines) {
        if (stack == null || stack.isEmpty()) return "";
        String[] parts = stack.split("\n");
        int n = Math.min(lines, parts.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append('\n');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private static String sha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
