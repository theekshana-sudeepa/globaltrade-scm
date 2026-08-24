package lk.jiat.globaltrade.common.exception;

import jakarta.ejb.ApplicationException;

/**
 * rollback = true: a partially-allocated shipment must never survive.
 * The container rolls the transaction back but KEEPS the bean instance in
 * the pool, unlike a system exception which would destroy it.
 */
@ApplicationException(rollback = true, inherited = true)
public class InsufficientInventoryException extends SupplyChainException {

    private final String sku;
    private final int requested;
    private final int available;

    public InsufficientInventoryException(String sku, int requested, int available) {
        super("SCM-409-INVENTORY",
              "Insufficient stock for SKU " + sku + ": requested " + requested
                  + ", available " + available);
        this.sku = sku;
        this.requested = requested;
        this.available = available;
    }

    public String getSku()      { return sku; }
    public int getRequested()   { return requested; }
    public int getAvailable()   { return available; }
}
