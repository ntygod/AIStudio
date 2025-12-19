package com.inkflow.module.auth.controller;

import com.inkflow.module.auth.dto.LoginRequest;
import com.inkflow.module.auth.dto.RefreshTokenRequest;
import com.inkflow.module.auth.dto.RegisterRequest;
import com.inkflow.module.auth.dto.TokenResponse;
import com.inkflow.module.auth.dto.UpdateProfileRequest;
import com.inkflow.module.auth.dto.UserDto;
import com.inkflow.module.auth.security.UserPrincipal;
import com.inkflow.module.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 
 * 提供用户认证相关的REST API：
 * - POST /api/auth/register - 用户注册
 * - POST /api/auth/login - 用户登录
 * - POST /api/auth/refresh - 刷新令牌
 * - POST /api/auth/logout - 登出
 * - POST /api/auth/logout-all - 登出所有设备
 * - GET /api/auth/me - 获取当前用户信息
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private final AuthService authService;
    
    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    
    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        TokenResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        String ipAddress = getClientIpAddress(httpRequest);
        TokenResponse response = authService.login(request, ipAddress);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 刷新令牌
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(
        @Valid @RequestBody RefreshTokenRequest request,
        HttpServletRequest httpRequest
    ) {
        String ipAddress = getClientIpAddress(httpRequest);
        TokenResponse response = authService.refreshToken(request, ipAddress);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 登出
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
    
    /**
     * 登出所有设备
     */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAllDevices(@AuthenticationPrincipal UserPrincipal user) {
        authService.logoutAllDevices(user.getId());
        return ResponseEntity.noContent().build();
    }
    
    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(@AuthenticationPrincipal UserPrincipal user) {
        UserDto userDto = authService.getCurrentUser(user.getId());
        return ResponseEntity.ok(userDto);
    }
    
    /**
     * 更新用户资料
     */
    @PutMapping("/profile")
    public ResponseEntity<UserDto> updateProfile(
        @AuthenticationPrincipal UserPrincipal user,
        @Valid @RequestBody UpdateProfileRequest request
    ) {
        UserDto userDto = authService.updateProfile(user.getId(), request);
        return ResponseEntity.ok(userDto);
    }
    
    /**
     * 获取客户端IP地址
     * 支持代理服务器场景
     */
    private String getClientIpAddress(HttpServletRequest request) {
        // 检查常见的代理头
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For可能包含多个IP，取第一个
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
}
