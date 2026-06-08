package com.campus.trade.common.filter;
import com.campus.trade.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
@Slf4j @Component @RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.parseToken(bearer.substring(7));
                Long userId = Long.parseLong(claims.getSubject());
                String username = claims.get("username",String.class);
                String roles = claims.get("roles",String.class);
                List<SimpleGrantedAuthority> authorities = Arrays.stream(roles.split(",")).map(r->new SimpleGrantedAuthority("ROLE_"+r.trim())).toList();
                LoginUser loginUser = new LoginUser(userId, username, authorities);
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null, authorities));
            } catch (ExpiredJwtException e) { log.debug("JWT expired"); } catch (Exception e) { log.debug("JWT invalid: {}",e.getMessage()); }
        }
        chain.doFilter(request, response);
    }
}
