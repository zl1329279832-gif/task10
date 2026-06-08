package com.campus.trade.common.config;
import com.campus.trade.domain.entity.User;
import com.campus.trade.mapper.UserMapper;
import com.campus.trade.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.List;
@Service @RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.findByUsername(username);
        if (user == null) throw new UsernameNotFoundException("User not found: "+username);
        if (user.getStatus() != 1) throw new UsernameNotFoundException("Account disabled");
        List<String> roles = userRoleMapper.findRoleCodesByUserId(user.getId());
        List<SimpleGrantedAuthority> authorities = roles.stream().map(r->new SimpleGrantedAuthority("ROLE_"+r)).toList();
        return new org.springframework.security.core.userdetails.User(user.getUsername(), user.getPassword(), authorities);
    }
}
