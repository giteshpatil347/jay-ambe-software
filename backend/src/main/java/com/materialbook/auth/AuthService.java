package com.materialbook.auth;

import com.materialbook.auth.AuthDtos.TokenResponse;
import com.materialbook.audit.service.AuditService;
import com.materialbook.auth.AuthDtos.UserInfo;
import com.materialbook.common.ApiException;
import com.materialbook.common.ApiException.ValidationException;
import com.materialbook.security.JwtService;
import com.materialbook.users.User;
import com.materialbook.users.UserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String INVALID_CREDENTIALS = "Invalid mobile number or password";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** A freshly issued session: the raw refresh token goes into the cookie, never into the body. */
    public record Session(TokenResponse tokens, String refreshToken) {}

    private final UserService users;
    private final RefreshTokenRepository refreshTokens;
    private final JwtService jwt;
    private final PasswordEncoder encoder;
    private final AuthProperties props;
    private final AuditService audit;
    private final String dummyHash;

    public AuthService(UserService users, RefreshTokenRepository refreshTokens, JwtService jwt,
                       PasswordEncoder encoder, AuthProperties props, AuditService audit) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.jwt = jwt;
        this.encoder = encoder;
        this.props = props;
        this.audit = audit;
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    // noRollbackFor: the failed-login counter must persist even though we throw 401.
    @Transactional(noRollbackFor = ApiException.class)
    public Session login(String mobile, String password) {
        Instant now = Instant.now();
        User user = users.findByMobileForLogin(mobile.trim()).orElse(null);
        // Always run one BCrypt compare so unknown users cost the same as known ones.
        boolean passwordOk = encoder.matches(password, user == null ? dummyHash : user.getPasswordHash());
        Long userId = user == null ? null : user.getId();
        if (user == null || !user.isActive() || user.isLocked(now)) {
            audit.logAs(userId, "LOGIN_FAILED", "AUTH", userId, null, Map.of("mobile", abbreviate(mobile)));
            throw invalidCredentials();
        }
        if (!passwordOk) {
            user.registerFailedLogin(now, props.maxFailedLogins(), props.lockoutDuration());
            audit.logAs(userId, "LOGIN_FAILED", "AUTH", userId, null, Map.of("mobile", abbreviate(mobile)));
            if (user.isLocked(now)) {
                audit.logAs(userId, "ACCOUNT_LOCKED", "AUTH", userId, null, Map.of("mobile", abbreviate(mobile)));
            }
            throw invalidCredentials();
        }
        user.registerSuccessfulLogin(now);
        audit.logAs(userId, "LOGIN", "AUTH", userId, null, null);
        return issue(user, UUID.randomUUID(), now);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String rawToken) {
        Instant now = Instant.now();
        RefreshToken token = rawToken == null ? null : refreshTokens.findByTokenHash(sha256(rawToken)).orElse(null);
        if (token == null) {
            throw invalidSession();
        }
        if (token.isRevoked()) {
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            log.warn("Refresh token reuse detected; revoked family for user {}", token.getUserId());
            audit.logAs(token.getUserId(), "REFRESH_TOKEN_REUSE", "AUTH", token.getUserId(), null, null);
            throw invalidSession();
        }
        if (token.isExpired(now)) {
            throw invalidSession();
        }
        User user = users.getById(token.getUserId());
        if (!user.isActive()) {
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            throw invalidSession();
        }
        token.revoke(now);
        return issue(user, token.getFamilyId(), now);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null) {
            return;
        }
        refreshTokens.findByTokenHash(sha256(rawToken)).ifPresent(t -> {
            refreshTokens.revokeFamily(t.getFamilyId(), Instant.now());
            audit.logAs(t.getUserId(), "LOGOUT", "AUTH", t.getUserId(), null, null);
        });
    }

    @Transactional(readOnly = true)
    public UserInfo me(Long userId) {
        User user = users.getById(userId);
        if (!user.isActive()) {
            throw invalidSession();
        }
        return userInfo(user);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = users.getById(userId);
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ValidationException("Current password is incorrect");
        }
        if (currentPassword.equals(newPassword)) {
            throw new ValidationException("New password must differ from the current password");
        }
        user.changePassword(encoder.encode(newPassword));
        refreshTokens.revokeAllForUser(userId, Instant.now());
        audit.logAs(userId, "PASSWORD_CHANGED", "AUTH", userId, null, null);
    }

    public long refreshTtlSeconds() { return props.refreshTokenTtl().toSeconds(); }

    private Session issue(User user, UUID familyId, Instant now) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user.getId(), familyId, sha256(raw), now.plus(props.refreshTokenTtl())));
        UserInfo info = userInfo(user);
        // A user who still has to change a temporary/bootstrap password gets a token that authorises nothing except
        // the authenticated-only endpoints (/auth/me, /auth/change-password); the body keeps the real permissions for the UI.
        String access = jwt.issue(user.getId(), user.isPasswordChangeRequired() ? List.of() : info.permissions());
        return new Session(new TokenResponse(access, jwt.expiresInSeconds(), info), raw);
    }

    private UserInfo userInfo(User user) {
        List<String> perms = List.copyOf(users.effectivePermissions(user.getId()));
        return new UserInfo(user.getId(), user.getFullName(), perms, user.isPasswordChangeRequired());
    }

    /** The attempted login id is attacker-controlled; keep audit rows small. */
    private static String abbreviate(String value) {
        return value.length() <= 32 ? value : value.substring(0, 32) + "...";
    }

    private static ApiException invalidCredentials() {
        return new ApiException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
    }

    private static ApiException invalidSession() {
        return new ApiException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "Session is no longer valid");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
