package com.fixlog.common.security;

import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
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

    /** 인증이 전제된 경로에서 쓴다. 인증 정보가 없으면 401로 끊는다. */
    public static String requireCurrentUserId() {
        String userId = getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(Code.UNAUTHORIZED, "인증 정보가 없습니다.");
        }
        return userId;
    }
}
