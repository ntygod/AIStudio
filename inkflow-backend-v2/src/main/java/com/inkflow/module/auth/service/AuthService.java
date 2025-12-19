package com.inkflow.module.auth.service;

import com.inkflow.common.exception.BusinessException;
import com.inkflow.module.auth.dto.LoginRequest;
import com.inkflow.module.auth.dto.RefreshTokenRequest;
import com.inkflow.module.auth.dto.RegisterRequest;
import com.inkflow.module.auth.dto.TokenResponse;
import com.inkflow.module.auth.dto.UpdateProfileRequest;
import com.inkflow.module.auth.dto.UserDto;
import com.inkflow.module.auth.entity.RefreshToken;
import com.inkflow.module.auth.entity.User;
import com.inkflow.module.auth.entity.UserStatus;
import com.inkflow.module.auth.repository.RefreshTokenRepository;
import com.inkflow.module.auth.repository.UserRepository;
import com.inkflow.module.auth.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 认证服务
 * 
 * 提供用户注册、登录、令牌刷新、登出等功能
 */
@Service
public class AuthService {
    
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    
    public AuthService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        JwtService jwtService,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }
    
    /**
     * 用户注册
     * 
     * @param request 注册请求
     * @return 令牌响应
     */
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException("用户名已被使用");
        }
        
        // 检查邮箱是否已存在
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("邮箱已被注册");
        }
        
        // 创建用户
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName() != null ? request.displayName() : request.username());
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(false);
        
        user = userRepository.save(user);
        
        log.info("新用户注册成功: userId={}, username={}", user.getId(), user.getUsername());
        
        // 生成令牌
        return generateTokenResponse(user, null);
    }
    
    /**
     * 用户登录
     * 
     * @param request 登录请求
     * @param ipAddress 客户端IP地址
     * @return 令牌响应
     */
    @Transactional
    public TokenResponse login(LoginRequest request, String ipAddress) {
        // 查找用户
        User user = userRepository.findByEmailOrUsername(request.identifier())
            .orElseThrow(() -> new BusinessException("用户名或密码错误"));
        
        // 检查账户状态
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("账户已被禁用");
        }
        
        // 检查账户是否被锁定
        if (user.isLocked()) {
            throw new BusinessException("账户已被锁定，请稍后再试");
        }
        
        // 验证密码
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // 记录登录失败
            user.recordLoginFailure();
            userRepository.save(user);
            
            log.warn("登录失败，密码错误: userId={}, failedAttempts={}", 
                user.getId(), user.getFailedLoginAttempts());
            
            throw new BusinessException("用户名或密码错误");
        }
        
        // 记录登录成功
        user.recordLoginSuccess();
        userRepository.save(user);
        
        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());
        
        // 生成令牌
        return generateTokenResponse(user, new DeviceInfo(request.deviceInfo(), ipAddress));
    }
    
    /**
     * 刷新令牌
     * 
     * @param request 刷新令牌请求
     * @param ipAddress 客户端IP地址
     * @return 新的令牌响应
     */
    @Transactional
    public TokenResponse refreshToken(RefreshTokenRequest request, String ipAddress) {
        // 查找刷新令牌
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.refreshToken())
            .orElseThrow(() -> new BusinessException("无效的刷新令牌"));
        
        // 验证令牌是否有效
        if (!refreshToken.isValid()) {
            throw new BusinessException("刷新令牌已过期或已被撤销");
        }
        
        // 查找用户
        User user = userRepository.findById(refreshToken.getUserId())
            .orElseThrow(() -> new BusinessException("用户不存在"));
        
        // 检查用户状态
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("账户已被禁用");
        }
        
        // 撤销旧的刷新令牌
        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);
        
        log.debug("刷新令牌成功: userId={}", user.getId());
        
        // 生成新的令牌
        return generateTokenResponse(user, new DeviceInfo(refreshToken.getDeviceInfo(), ipAddress));
    }
    
    /**
     * 登出（撤销刷新令牌）
     * 
     * @param refreshToken 刷新令牌
     */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.revokeByToken(refreshToken, LocalDateTime.now());
        log.debug("用户登出，刷新令牌已撤销");
    }
    
    /**
     * 登出所有设备
     * 
     * @param userId 用户ID
     */
    @Transactional
    public void logoutAllDevices(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
        log.info("用户登出所有设备: userId={}", userId);
    }
    
    /**
     * 获取当前用户信息
     * 
     * @param userId 用户ID
     * @return 用户信息
     */
    @Transactional(readOnly = true)
    public UserDto getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("用户不存在"));
        return UserDto.fromEntity(user);
    }
    
    /**
     * 更新用户资料
     * 
     * @param userId 用户ID
     * @param request 更新请求
     * @return 更新后的用户信息
     */
    @Transactional
    public UserDto updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("用户不存在"));
        
        // 更新非空字段
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.bio() != null) {
            user.setBio(request.bio());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl());
        }
        
        user = userRepository.save(user);
        log.info("用户资料更新成功: userId={}", userId);
        
        return UserDto.fromEntity(user);
    }
    
    /**
     * 生成令牌响应
     */
    private TokenResponse generateTokenResponse(User user, DeviceInfo deviceInfo) {
        // 生成访问令牌
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername());
        
        // 生成刷新令牌
        String refreshTokenValue = jwtService.generateRefreshTokenValue();
        LocalDateTime expiresAt = LocalDateTime.now()
            .plusSeconds(jwtService.getRefreshTokenExpirationSeconds());
        
        RefreshToken refreshToken = new RefreshToken(user.getId(), refreshTokenValue, expiresAt);
        if (deviceInfo != null) {
            refreshToken.setDeviceInfo(deviceInfo.deviceInfo());
            refreshToken.setIpAddress(deviceInfo.ipAddress());
        }
        refreshTokenRepository.save(refreshToken);
        
        return TokenResponse.bearer(
            accessToken,
            refreshTokenValue,
            jwtService.getAccessTokenExpirationSeconds(),
            UserDto.fromEntity(user)
        );
    }
    
    /**
     * 设备信息记录
     */
    private record DeviceInfo(String deviceInfo, String ipAddress) {}
}
