package com.materialbook.auth;

import com.materialbook.auth.AuthDtos.ChangePasswordRequest;
import com.materialbook.auth.AuthDtos.LoginRequest;
import com.materialbook.auth.AuthDtos.TokenResponse;
import com.materialbook.auth.AuthDtos.UserInfo;
import com.materialbook.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    static final String REFRESH_COOKIE = "refresh_token";
    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_VALUE = "MaterialBook";

    private final AuthService auth;
    private final AuthProperties props;

    public AuthController(AuthService auth, AuthProperties props) {
        this.auth = auth;
        this.props = props;
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return withCookie(auth.login(request.mobile(), request.password()));
    }

    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token,
                                          @RequestHeader(name = CSRF_HEADER, required = false) String requestedWith) {
        requireCsrfHeader(requestedWith);
        return withCookie(auth.refresh(token));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token,
                                @RequestHeader(name = CSRF_HEADER, required = false) String requestedWith) {
        requireCsrfHeader(requestedWith);
        auth.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", 0).toString()).build();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    UserInfo me(@AuthenticationPrincipal Jwt jwt) {
        return auth.me(Long.valueOf(jwt.getSubject()));
    }

    /** Revokes every refresh token, so the client must log in again afterwards. */
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(Long.valueOf(jwt.getSubject()), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", 0).toString()).build();
    }

    private ResponseEntity<TokenResponse> withCookie(AuthService.Session session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), auth.refreshTtlSeconds()).toString())
                .body(session.tokens());
    }

    private ResponseCookie cookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(maxAgeSeconds)
                .build();
    }

    private static void requireCsrfHeader(String value) {
        if (!CSRF_VALUE.equals(value)) {
            throw new ApiException("FORBIDDEN", HttpStatus.FORBIDDEN, "Missing or invalid " + CSRF_HEADER + " header");
        }
    }
}
