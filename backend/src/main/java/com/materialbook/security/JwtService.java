package com.materialbook.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/** Issues and verifies HS256 access tokens. Fails fast when the secret is missing or shorter than 256 bits. */
@Service
public class JwtService {
    public static final String PERMISSIONS_CLAIM = "perms";
    private static final int MIN_SECRET_BYTES = 32;
    static final String ISSUER = "materialbook";
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final JwtEncoder encoder;

    public JwtService(@Value("${app.jwt.secret:}") String secret,
                      @Value("${app.jwt.access-token-ttl}") Duration accessTokenTtl) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " bytes long");
        }
        if (secret.toLowerCase(Locale.ROOT).contains("change-me")) {
            throw new IllegalStateException("JWT_SECRET is still the .env.example placeholder; generate one with scripts/gen-secrets.sh");
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
        this.accessTokenTtl = accessTokenTtl;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    public String issue(Long userId, Collection<String> permissions) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl))
                .claim(PERMISSIONS_CLAIM, permissions)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public long expiresInSeconds() { return accessTokenTtl.toSeconds(); }

    public JwtDecoder decoder() {
        // Algorithm pinned to HS256 (alg=none / HS384 / RS256 tokens are rejected); exp/nbf with 30 s skew; issuer must match.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(CLOCK_SKEW), new JwtIssuerValidator(ISSUER)));
        return decoder;
    }
}
