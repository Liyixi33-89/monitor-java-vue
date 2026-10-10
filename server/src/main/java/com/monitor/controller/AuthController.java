package com.monitor.controller;

import com.monitor.entity.User;
import com.monitor.mapper.UserMapper;
import com.monitor.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public static final String COOKIE_NAME = "monitor_token";

    @Value("${monitor.jwt.expire-minutes:720}")
    private long expireMinutes;

    /** Cookie 是否加 Secure（HTTPS 才能开启）；生产走 HTTP，暂关 */
    @Value("${monitor.cookie.secure:false}")
    private boolean cookieSecure;

    public record LoginRequest(String username, String password) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletResponse response) {
        User user = userMapper.findByUsername(req.username());
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "error", "用户名或密码错误"));
        }
        String token = jwtTokenProvider.generate(user.getUsername());
        // HttpOnly Cookie：前端 JS 无法读取，防 XSS 窃取；SameSite=Lax 防 CSRF
        ResponseCookieBuilder.writeLoginCookie(response, token, expireMinutes, cookieSecure, user.getUsername());
        return ResponseEntity.ok(Map.of("ok", true, "token", token, "username", user.getUsername()));
    }

    /** 登出：清空认证 Cookie，前端随后跳转登录页 */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** 登录态检查：前端路由守卫用，401 即未登录/已失效 */
    @PostMapping("/check")
    public ResponseEntity<?> check(HttpServletRequest request) {
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** 统一构建登录 Cookie，避免魔法字符串分散 */
    static class ResponseCookieBuilder {
        static void writeLoginCookie(HttpServletResponse response, String token, long expireMinutes,
                                     boolean secure, String username) {
            Cookie cookie = new Cookie(COOKIE_NAME, token);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setSecure(secure);
            cookie.setAttribute("SameSite", "Lax");
            cookie.setMaxAge((int) (expireMinutes * 60));
            response.addCookie(cookie);
        }
    }
}
