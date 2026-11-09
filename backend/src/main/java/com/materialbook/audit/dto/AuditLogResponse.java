package com.materialbook.audit.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record AuditLogResponse(Long id, Long userId, String userFullName, String action, String module,
                               Long recordId, JsonNode oldValues, JsonNode newValues,
                               String ipAddress, Instant createdAt) {}
