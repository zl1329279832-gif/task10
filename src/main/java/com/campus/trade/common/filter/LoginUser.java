package com.campus.trade.common.filter;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;
@Getter
public class LoginUser implements UserDetails {
    private final Long userId; private final String username; private final List<? extends GrantedAuthority> authorities;
    public LoginUser(Long userId, String username, List<? extends GrantedAuthority> authorities) {
        this.userId = userId; this.username = username; this.authorities = authorities;
    }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return null; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
    public boolean hasRole(String role) { return authorities.stream().anyMatch(a->a.getAuthority().equals("ROLE_"+role)); }
}
