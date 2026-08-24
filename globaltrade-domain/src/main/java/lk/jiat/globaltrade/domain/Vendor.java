package lk.jiat.globaltrade.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "vendor", indexes = {
        @Index(name = "ix_vendor_code", columnList = "vendor_code", unique = true),
        @Index(name = "ix_vendor_country", columnList = "country_code")
})
@NamedQueries({
    @NamedQuery(name = "Vendor.findByCode",
                query = "SELECT v FROM Vendor v WHERE v.vendorCode = :code"),
    @NamedQuery(name = "Vendor.findActive",
                query = "SELECT v FROM Vendor v WHERE v.active = true ORDER BY v.vendorCode"),
    @NamedQuery(name = "Vendor.findDueForReview",
                query = "SELECT v FROM Vendor v WHERE v.active = true "
                      + "AND (v.lastEvaluatedAt IS NULL OR v.lastEvaluatedAt < :cutoff)")
})
@Cacheable
public class Vendor implements Serializable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vendor_code", nullable = false, unique = true, length = 32)
    private String vendorCode;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "tax_id", length = 64)
    private String taxId;

    @Column(name = "contact_email", length = 160)
    private String contactEmail;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "performance_score")
    private Double performanceScore;

    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;

    /** Optimistic locking: prevents lost updates without holding DB locks across the WAN. */
    @Version
    private Long version;

    public Long getId()                    { return id; }
    public void setId(Long id)             { this.id = id; }
    public String getVendorCode()          { return vendorCode; }
    public void setVendorCode(String v)    { this.vendorCode = v; }
    public String getName()                { return name; }
    public void setName(String n)          { this.name = n; }
    public String getCountryCode()         { return countryCode; }
    public void setCountryCode(String c)   { this.countryCode = c; }
    public String getTaxId()               { return taxId; }
    public void setTaxId(String t)         { this.taxId = t; }
    public String getContactEmail()        { return contactEmail; }
    public void setContactEmail(String e)  { this.contactEmail = e; }
    public boolean isActive()              { return active; }
    public void setActive(boolean a)       { this.active = a; }
    public Double getPerformanceScore()    { return performanceScore; }
    public void setPerformanceScore(Double s) { this.performanceScore = s; }
    public Instant getLastEvaluatedAt()    { return lastEvaluatedAt; }
    public void setLastEvaluatedAt(Instant i) { this.lastEvaluatedAt = i; }
    public Long getVersion()               { return version; }
}
