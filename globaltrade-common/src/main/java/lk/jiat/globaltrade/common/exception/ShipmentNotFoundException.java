package lk.jiat.globaltrade.common.exception;

import jakarta.ejb.ApplicationException;

/**
 * Expected business outcome: the client asked for a shipment that does not exist.
 *
 * rollback = false  -> the container must NOT roll back. Nothing was written,
 *                      and rolling back would also discard the audit record the
 *                      compliance requirement demands.
 * inherited = true  -> subclasses keep the designation (default since EJB 3.1).
 *
 * Stack trace capture is disabled: this is a high-frequency lookup miss, not a defect.
 */
@ApplicationException(rollback = false, inherited = true)
public class ShipmentNotFoundException extends SupplyChainException {

    public ShipmentNotFoundException(String trackingNumber) {
        super("SCM-404-SHIPMENT",
              "No shipment found for tracking number: " + trackingNumber,
              false);
    }
}
