package com.hjgd.plm.auth.security;

import com.hjgd.plm.system.entity.SysUser;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Data
public class LoginUser implements UserDetails {

    private SysUser user;
    private List<String> roles;
    private List<String> permissions;
    private Long loginTime;
    private String token;
    private String ip;

    public LoginUser(SysUser user, List<String> roles, List<String> permissions) {
        this.user = user;
        this.roles = roles;
        this.permissions = permissions;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<SimpleGrantedAuthority> auths = new ArrayList<>();
        if (permissions != null) {
            permissions.stream().map(SimpleGrantedAuthority::new).forEach(auths::add);
        }
        if (roles != null) {
            roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).forEach(auths::add);
        }
        return auths;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
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
        return user.getStatus() != null && user.getStatus() == 1;
    }

    public Long getUserId() {
        return user.getId();
    }

    public String getRealName() {
        return user.getRealName();
    }

    public String getPrimaryRole() {
        return (roles != null && !roles.isEmpty()) ? roles.get(0) : null;
    }
}
