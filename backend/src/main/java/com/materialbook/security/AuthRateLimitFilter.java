package com.materialbook.security;

import com.materialbook.auth.AuthProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP limit on /api/v1/auth/**. Uses getRemoteAddr(); real client IP comes from
 * {@code server.forward-headers-strategy=native} behind a trusted reverse proxy (forwarded headers are never parsed here).
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_TRACKED_IPS = 10_000;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int limitPerMinute;
    private final ProblemResponseWriter problems;

    public AuthRateLimitFilter(AuthProperties props, ProblemResponseWriter problems) {
        this.limitPerMinute = props.rateLimitPerMinute();
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (buckets.size() > MAX_TRACKED_IPS) {
            buckets.values().removeIf(b -> b.getAvailableTokens() >= limitPerMinute);
        }
        Bucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(), ip -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(limitPerMinute).refillGreedy(limitPerMinute, Duration.ofMinutes(1)).build())
                .build());
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setHeader("Retry-After", "60");
            problems.write(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests, try again later");
        }
    }
}
