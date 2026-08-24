package lk.jiat.globaltrade.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

/** Rolling scorecard produced by the weekly vendor-evaluation timer. */
@Entity
@Table(name = "vendor_performance", indexes = {
        @Index(name = "ix_vperf_vendor_period", columnList = "vendor_id,period_start")
})
@NamedQuery(name = "VendorPerformance.latestForVendor",
            query = "SELECT p FROM VendorPerformance p WHERE p.vendor.id = :vendorId "
                  + "ORDER BY p.periodStart DESC")
public class VendorPerformance implements Serializable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "shipments_total", nullable = false)
    private int shipmentsTotal;

    @Column(name = "shipments_on_time", nullable = false)
    private int shipmentsOnTime;

    @Column(name = "on_time_rate")
    private Double onTimeRate;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt = Instant.now();

    public Long getId()                         { return id; }
    public Vendor getVendor()                   { return vendor; }
    public void setVendor(Vendor v)             { this.vendor = v; }
    public Instant getPeriodStart()             { return periodStart; }
    public void setPeriodStart(Instant i)       { this.periodStart = i; }
    public Instant getPeriodEnd()               { return periodEnd; }
    public void setPeriodEnd(Instant i)         { this.periodEnd = i; }
    public int getShipmentsTotal()              { return shipmentsTotal; }
    public void setShipmentsTotal(int i)        { this.shipmentsTotal = i; }
    public int getShipmentsOnTime()             { return shipmentsOnTime; }
    public void setShipmentsOnTime(int i)       { this.shipmentsOnTime = i; }
    public Double getOnTimeRate()               { return onTimeRate; }
    public void setOnTimeRate(Double d)         { this.onTimeRate = d; }
    public Instant getComputedAt()              { return computedAt; }
}
