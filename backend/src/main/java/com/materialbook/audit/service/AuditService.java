package com.materialbook.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.materialbook.audit.entity.AuditLog;
import com.materialbook.audit.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Entry point other modules use to write audit rows; joins the caller's transaction (or starts one). */
@Service
public class AuditService {
    static final String REDACTED = "***";
    /** Substring match, case-insensitive: covers password, passwordHash, newPassword, token, refreshToken, secret ... */
    private static final List<String> SENSITIVE_FRAGMENTS = List.of("password", "token", "secret");

    private final AuditLogRepository repository;
    private final ObjectMapper mapper;

    public AuditService(AuditLogRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** Actor is the authenticated user (JWT {@code sub}); null when anonymous. */
    @Transactional
    public void log(String action, String module, Long recordId, Object oldValues, Object newValues) {
        logAs(currentUserId(), action, module, recordId, oldValues, newValues);
    }

    @Transactional
    public void logAs(Long userId, String action, String module, Long recordId, Object oldValues, Object newValues) {
        repository.save(new AuditLog(userId, action, module, recordId,
                redacted(oldValues), redacted(newValues), clientIp()));
    }

    private JsonNode redacted(Object value) {
        if (value == null) {
            return null;
        }
        JsonNode tree = mapper.valueToTree(value);
        redact(tree);
        return tree;
    }

    private static void redact(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            for (Iterator<Map.Entry<String, JsonNode>> it = object.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                if (isSensitive(field.getKey())) {
                    field.setValue(TextNode.valueOf(REDACTED));
                } else {
                    redact(field.getValue());
                }
            }
        } else if (node.isArray()) {
            node.forEach(AuditService::redact);
        }
    }

    private static boolean isSensitive(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_FRAGMENTS.stream().anyMatch(lower::contains);
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return Long.valueOf(jwt.getSubject());
        }
        return null;
    }

    private static InetAddress clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String addr = request.getRemoteAddr();
            if (addr != null) {
                try {
                    return InetAddress.getByName(addr); // remoteAddr is always an IP literal: no DNS lookup
                } catch (UnknownHostException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
