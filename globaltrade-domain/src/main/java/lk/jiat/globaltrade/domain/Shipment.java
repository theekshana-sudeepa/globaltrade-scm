package lk.jiat.globaltrade.domain;

import jakarta.persistence.*;
import lk.jiat.globaltrade.common.constant.ShipmentStatus;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "shipment", indexes = {
        @Index(name = "ix_shipment_tracking", columnList = "tracking_number", unique = true),
        @Index(name = "ix_shipment_status",   columnList = "status"),
        @Index(name = "ix_shipment_eta",      columnList = "estimated_arrival"),
        @Index(name = "ix_shipment_vendor",   columnList = "vendor_id")
})
@NamedQueries({
    @NamedQuery(name = "Shipment.findByTracking",
                query = "SELECT s FROM Shipment s LEFT JOIN FETCH s.vendor "
                      + "WHERE s.trackingNumber = :tracking"),
    @NamedQuery(name = "Shipment.findInTransit",
                query = "SELECT s FROM Shipment s WHERE s.status IN "
                      + "(lk.jiat.globaltrade.common.constant.ShipmentStatus.IN_TRANSIT, "
                      + " lk.jiat.globaltrade.common.constant.ShipmentStatus.BOOKED)"),
    @NamedQuery(name = "Shipment.findOverdue",
                query = "SELECT s FROM Shipment s WHERE s.estimatedArrival < :now "
                      + "AND s.status <> lk.jiat.globaltrade.common.constant.ShipmentStatus.DELIVERED"),
    @NamedQuery(name = "Shipment.countByStatus",
                query = "SELECT s.status, COUNT(s) FROM Shipment s GROUP BY s.status")
})
public class Shipment implements Serializable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 40)
    private String trackingNumber;

    @Column(name = "origin_country", nullable = false, length = 2)
    private String originCountry;

    @Column(name = "destination_country", nullable = false, length = 2)
    private String destinationCountry;

    @Column(name = "carrier_code", nullable = false, length = 16)
    private String carrierCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ShipmentStatus status = ShipmentStatus.CREATED;

    @Column(name = "estimated_arrival")
    private Instant estimatedArrival;

    @Column(name = "actual_arrival")
    private Instant actualArrival;

    @Column(name = "declared_value", precision = 15, scale = 2)
    private java.math.BigDecimal declaredValue;

    /** LAZY on purpose: the tracking-poll timer touches 10k rows and never needs the vendor. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_polled_at")
    private Instant lastPolledAt;

    @Version
    private Long version;

    public Long getId()                       { return id; }
    public void setId(Long id)                { this.id = id; }
    public String getTrackingNumber()         { return trackingNumber; }
    public void setTrackingNumber(String t)   { this.trackingNumber = t; }
    public String getOriginCountry()          { return originCountry; }
    public void setOriginCountry(String o)    { this.originCountry = o; }
    public String getDestinationCountry()     { return destinationCountry; }
    public void setDestinationCountry(String d){ this.destinationCountry = d; }
    public String getCarrierCode()            { return carrierCode; }
    public void setCarrierCode(String c)      { this.carrierCode = c; }
    public ShipmentStatus getStatus()         { return status; }
    public void setStatus(ShipmentStatus s)   { this.status = s; }
    public Instant getEstimatedArrival()      { return estimatedArrival; }
    public void setEstimatedArrival(Instant i){ this.estimatedArrival = i; }
    public Instant getActualArrival()         { return actualArrival; }
    public void setActualArrival(Instant i)   { this.actualArrival = i; }
    public java.math.BigDecimal getDeclaredValue()          { return declaredValue; }
    public void setDeclaredValue(java.math.BigDecimal v)    { this.declaredValue = v; }
    public Vendor getVendor()                 { return vendor; }
    public void setVendor(Vendor v)           { this.vendor = v; }
    public Instant getCreatedAt()             { return createdAt; }
    public Instant getLastPolledAt()          { return lastPolledAt; }
    public void setLastPolledAt(Instant i)    { this.lastPolledAt = i; }
    public Long getVersion()                  { return version; }
}
