package lk.jiat.globaltrade.domain;

import jakarta.persistence.*;
import lk.jiat.globaltrade.common.constant.ComplianceStatus;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "customs_declaration", indexes = {
        @Index(name = "ix_customs_deadline", columnList = "filing_deadline"),
        @Index(name = "ix_customs_status",   columnList = "status")
})
@NamedQueries({
    @NamedQuery(name = "CustomsDeclaration.findDueBefore",
                query = "SELECT c FROM CustomsDeclaration c WHERE c.filingDeadline <= :cutoff "
                      + "AND c.status = lk.jiat.globaltrade.common.constant.ComplianceStatus.PENDING"),
    @NamedQuery(name = "CustomsDeclaration.findByShipment",
                query = "SELECT c FROM CustomsDeclaration c WHERE c.shipment.id = :shipmentId")
})
public class CustomsDeclaration implements Serializable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Column(name = "hs_code", nullable = false, length = 16)
    private String hsCode;

    @Column(name = "declaration_ref", length = 48)
    private String declarationRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ComplianceStatus status = ComplianceStatus.PENDING;

    @Column(name = "filing_deadline", nullable = false)
    private Instant filingDeadline;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "timer_scheduled", nullable = false)
    private boolean timerScheduled = false;

    @Version
    private Long version;

    public Long getId()                       { return id; }
    public Shipment getShipment()             { return shipment; }
    public void setShipment(Shipment s)       { this.shipment = s; }
    public String getHsCode()                 { return hsCode; }
    public void setHsCode(String h)           { this.hsCode = h; }
    public String getDeclarationRef()         { return declarationRef; }
    public void setDeclarationRef(String d)   { this.declarationRef = d; }
    public ComplianceStatus getStatus()       { return status; }
    public void setStatus(ComplianceStatus s) { this.status = s; }
    public Instant getFilingDeadline()        { return filingDeadline; }
    public void setFilingDeadline(Instant i)  { this.filingDeadline = i; }
    public Instant getSubmittedAt()           { return submittedAt; }
    public void setSubmittedAt(Instant i)     { this.submittedAt = i; }
    public boolean isTimerScheduled()         { return timerScheduled; }
    public void setTimerScheduled(boolean b)  { this.timerScheduled = b; }
}
