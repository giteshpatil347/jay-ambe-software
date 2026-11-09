package com.materialbook.attachments;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

final class CurrentUser {
    private CurrentUser() {}

    /** JWT subject of the authenticated caller; null when there is none. */
    static Long id() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof Jwt jwt
                ? Long.valueOf(jwt.getSubject()) : null;
    }
}
