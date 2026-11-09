package com.materialbook.audit.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.net.InetAddress;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only (DB trigger rejects UPDATE/DELETE): constructed once, inserted, never modified. */
@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private String action;

    @Column(nullable = false, updatable = false)
    private String module;

    @Column(name = "record_id", updatable = false)
    private Long recordId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", updatable = false)
    private JsonNode oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", updatable = false)
    private JsonNode newValues;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address", updatable = false)
    private InetAddress ipAddress;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {}

    public AuditLog(Long userId, String action, String module, Long recordId,
                    JsonNode oldValues, JsonNode newValues, InetAddress ipAddress) {
        this.userId = userId;
        this.action = action;
        this.module = module;
        this.recordId = recordId;
        this.oldValues = oldValues;
        this.newValues = newValues;
        this.ipAddress = ipAddress;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getAction() { return action; }
    public String getModule() { return module; }
    public Long getRecordId() { return recordId; }
    public JsonNode getOldValues() { return oldValues; }
    public JsonNode getNewValues() { return newValues; }
    public InetAddress getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
}
