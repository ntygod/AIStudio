package com.inkflow.module.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * JWT认证过滤器
 * 
 * 从请求头中提取JWT令牌，验证后设置Spring Security上下文
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    
    /**
     * Authorization请求头
     */
    private static final String AUTHORIZATION_HEADER = "Authorization";
    
    /**
     * Bearer令牌前缀
     */
    private static final String BEARER_PREFIX = "Bearer ";
    
    private final JwtService jwtService;
    
    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }
    
    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        
        try {
            // 提取JWT令牌
            String jwt = extractJwtFromRequest(request);
            
            if (StringUtils.hasText(jwt) && jwtService.validateToken(jwt)) {
                // 解析令牌获取用户信息
                UUID userId = jwtService.extractUserId(jwt);
                String username = jwtService.extractUsername(jwt);
                
                // 创建认证对象
                // 这里使用简单的角色，实际项目可能需要从数据库加载用户角色
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
                
                var authentication = new UsernamePasswordAuthenticationToken(
                    new UserPrincipal(userId, username, authorities),
                    null,
                    authorities
                );
                
                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
                );
                
                // 设置安全上下文
                SecurityContextHolder.getContext().setAuthentication(authentication);
                
                log.debug("用户认证成功: userId={}, username={}", userId, username);
            }
            
        } catch (Exception e) {
            log.debug("JWT认证失败: {}", e.getMessage());
            // 认证失败不抛出异常，让请求继续，由后续的安全配置决定是否拒绝
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * 从请求中提取JWT令牌
     * 
     * @param request HTTP请求
     * @return JWT令牌，如果不存在返回null
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        
        return null;
    }
    
}
