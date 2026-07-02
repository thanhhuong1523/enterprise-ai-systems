package com.vccorp.eap.infrastructure.security;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityContextHelper {

    public static User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException(ErrorCode.ERR_UNAUTHENTICATED);
        }
        if (authentication.getPrincipal() instanceof User) {
            return (User) authentication.getPrincipal();
        }
        throw new BusinessException(ErrorCode.ERR_UNAUTHENTICATED);
    }
}
