package com.campus.trade.common.util;
import com.campus.trade.common.filter.LoginUser;
import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
public final class SecurityUtil {
    private SecurityUtil() {}
    public static LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser)) throw new BizException(ErrorCode.UNAUTHORIZED);
        return (LoginUser) auth.getPrincipal();
    }
    public static Long currentUserId() { return currentUser().getUserId(); }
    public static String currentUsername() { return currentUser().getUsername(); }
    public static boolean hasRole(String role) { return currentUser().hasRole(role); }
}
