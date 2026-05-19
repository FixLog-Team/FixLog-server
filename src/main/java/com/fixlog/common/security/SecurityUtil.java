package com.fixlog.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class SecurityUtil {
    private SecurityUtil() {
    }

    public static String getCurrentUserId() {
        OAuth2User user = getCurrentUser();
        return user != null ? user.getAttribute("sub") : null;
    }

    public static OAuth2User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User) {
            return (OAuth2User) authentication.getPrincipal();
        }
        return null;
    }
}
