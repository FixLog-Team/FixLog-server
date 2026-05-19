package com.fixlog.common.security;

import com.fixlog.domain.model.UserEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    private SecurityUtil() {
    }

    public static UserEntity getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserEntity user) {
            return user;
        }
        return null;
    }

    public static String getCurrentUserId() {
        UserEntity user = getCurrentUser();
        return user != null ? user.getUserId().toString() : null;
    }
}
