package com.carechain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "actor_email", nullable = false, length = 120)
    private String actorEmail;

    @Column(name = "actor_role", nullable = false, length = 30)
    private String actorRole;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 80)
    private String resourceType;

    @Column(name = "resource_id", length = 120)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AuditOutcome outcome;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "request_path", length = 255)
    private String requestPath;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "details_json", nullable = false)
    private String detailsJson;

    @Column(name = "previous_hash", length = 64)
    private String previousHash;

    @Column(name = "entry_hash", nullable = false, length = 64, unique = true)
    private String entryHash;
}
