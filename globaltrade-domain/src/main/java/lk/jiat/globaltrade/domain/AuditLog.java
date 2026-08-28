package lk.jiat.globaltrade.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

/**
 * Tamper-evident audit trail required for customs compliance and trade-agreement
 * reporting. Written by AuditServiceBean under REQUIRES_NEW so the record
 * survives rollback of the business transaction that produced it.
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "ix_audit_time",        columnList = "occurred_at"),
        @Index(name = "ix_audit_principal",   columnList = "principal_name"),
        @Index(name = "ix_audit_correlation", columnList = "correlation_id"),
        @Index(name = "ix_audit_action",      columnList = "action")
})
@NamedQueries({
    @NamedQuery(name = "AuditLog.findByCorrelation",
                query = "SELECT a FROM AuditLog a WHERE a.correlationId = :cid ORDER BY a.occurredAt"),
    @NamedQuery(name = "AuditLog.purgeOlderThan",
                query = "DELETE FROM AuditLog a WHERE a.occurredAt < :cutoff")
})
public class AuditLog implements Serializable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // D10: this column is sized for exactly one bare UUID. Any caller that
    // prefixes the correlation id will be silently rejected by MySQL, so the
    // timeout branch of SecurityAuditInterceptor must emit a plain UUID.
    @Column(name = "correlation_id", nullable = false, length = 36)
    private String correlationId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(name = "principal_name", length = 128)
    private String principalName;

    @Column(name = "principal_roles", length = 256)
    private String principalRoles;

    @Column(name = "source_module", length = 64)
    private String sourceModule;

    @Column(length = 16)
    private String outcome;          // SUCCESS | FAILURE

    @Column(name = "duration_millis")
    private Long durationMillis;

    @Lob
    @Column(name = "detail")
    private String detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    public Long getId()                        { return id; }
    public String getCorrelationId()           { return correlationId; }
    public void setCorrelationId(String c)     { this.correlationId = c; }
    public String getAction()                  { return action; }
    public void setAction(String a)            { this.action = a; }
    public String getTargetType()              { return targetType; }
    public void setTargetType(String t)        { this.targetType = t; }
    public String getTargetId()                { return targetId; }
    public void setTargetId(String t)          { this.targetId = t; }
    public String getPrincipalName()           { return principalName; }
    public void setPrincipalName(String p)     { this.principalName = p; }
    public String getPrincipalRoles()          { return principalRoles; }
    public void setPrincipalRoles(String r)    { this.principalRoles = r; }
    public String getSourceModule()            { return sourceModule; }
    public void setSourceModule(String s)      { this.sourceModule = s; }
    public String getOutcome()                 { return outcome; }
    public void setOutcome(String o)           { this.outcome = o; }
    public Long getDurationMillis()            { return durationMillis; }
    public void setDurationMillis(Long d)      { this.durationMillis = d; }
    public String getDetail()                  { return detail; }
    public void setDetail(String d)            { this.detail = d; }
    public Instant getOccurredAt()             { return occurredAt; }
    public void setOccurredAt(Instant i)       { this.occurredAt = i; }
}
