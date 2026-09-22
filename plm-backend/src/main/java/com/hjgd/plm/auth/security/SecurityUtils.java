package com.hjgd.plm.auth.security;

import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    public static LoginUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw new BusinessException(ResultCode.UNAUTHORIZED);
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().getUserId();
    }

    public static String getCurrentUsername() {
        return getCurrentUser().getUsername();
    }

    public static String getCurrentRealName() {
        return getCurrentUser().getRealName();
    }

    public static String getCurrentRole() {
        return getCurrentUser().getPrimaryRole();
    }

    public static boolean hasRole(String roleCode) {
        LoginUser user = getCurrentUser();
        return user.getRoles() != null && user.getRoles().contains(roleCode);
    }

    public static boolean hasPermission(String permCode) {
        LoginUser user = getCurrentUser();
        return user.getPermissions() != null && user.getPermissions().contains(permCode);
    }
}
