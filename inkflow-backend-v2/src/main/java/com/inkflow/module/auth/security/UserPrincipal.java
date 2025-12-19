package com.inkflow.module.auth.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 用户主体
 * 
 * 用于Spring Security认证上下文中的用户信息
 */
public class UserPrincipal implements UserDetails {
    
    private final UUID id;
    private final String username;
    private final Collection<? extends GrantedAuthority> authorities;
    
    public UserPrincipal(UUID id, String username) {
        this(id, username, List.of());
    }
    
    public UserPrincipal(UUID id, String username, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.authorities = authorities;
    }
    
    public UUID getId() {
        return id;
    }
    
    @Override
    public String getUsername() {
        return username;
    }
    
    @Override
    public String getPassword() {
        return null;
    }
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
}
