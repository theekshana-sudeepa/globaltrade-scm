package lk.jiat.globaltrade.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "inventory_item", indexes = {
        @Index(name = "ix_inv_sku_wh", columnList = "sku,warehouse_code", unique = true)
})
@NamedQueries({
    @NamedQuery(name = "InventoryItem.findBySkuAndWarehouse",
                query = "SELECT i FROM InventoryItem i WHERE i.sku = :sku AND i.warehouseCode = :wh"),
    @NamedQuery(name = "InventoryItem.findBelowReorderPoint",
                query = "SELECT i FROM InventoryItem i WHERE i.quantityOnHand <= i.reorderPoint")
})
public class InventoryItem implements Serializable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 48)
    private String sku;

    @Column(name = "warehouse_code", nullable = false, length = 16)
    private String warehouseCode;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved;

    @Column(name = "reorder_point", nullable = false)
    private int reorderPoint = 10;

    @Column(name = "reorder_quantity", nullable = false)
    private int reorderQuantity = 100;

    @Column(name = "last_replenished_at")
    private Instant lastReplenishedAt;

    @Version
    private Long version;

    public int available() { return quantityOnHand - quantityReserved; }

    public Long getId()                     { return id; }
    public String getSku()                  { return sku; }
    public void setSku(String s)            { this.sku = s; }
    public String getWarehouseCode()        { return warehouseCode; }
    public void setWarehouseCode(String w)  { this.warehouseCode = w; }
    public int getQuantityOnHand()          { return quantityOnHand; }
    public void setQuantityOnHand(int q)    { this.quantityOnHand = q; }
    public int getQuantityReserved()        { return quantityReserved; }
    public void setQuantityReserved(int q)  { this.quantityReserved = q; }
    public int getReorderPoint()            { return reorderPoint; }
    public void setReorderPoint(int r)      { this.reorderPoint = r; }
    public int getReorderQuantity()         { return reorderQuantity; }
    public void setReorderQuantity(int r)   { this.reorderQuantity = r; }
    public Instant getLastReplenishedAt()   { return lastReplenishedAt; }
    public void setLastReplenishedAt(Instant i) { this.lastReplenishedAt = i; }
    public Long getVersion()                { return version; }
}
