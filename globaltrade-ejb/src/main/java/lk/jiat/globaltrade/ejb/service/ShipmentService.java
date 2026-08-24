package lk.jiat.globaltrade.ejb.service;

import jakarta.ejb.Local;
import lk.jiat.globaltrade.common.constant.ShipmentStatus;
import lk.jiat.globaltrade.common.exception.CustomsComplianceException;
import lk.jiat.globaltrade.common.exception.InsufficientInventoryException;
import lk.jiat.globaltrade.common.exception.ShipmentNotFoundException;
import lk.jiat.globaltrade.domain.Shipment;

import java.util.List;

/**
 * LOCAL interface. Rationale for the assignment's "remote vs local interface
 * selection": every caller of this bean lives inside the same EAR, so a @Local
 * pass-by-reference call avoids serialisation entirely. Only the carrier
 * gateway (see CarrierIntegrationRemote) is exposed remotely.
 */
@Local
public interface ShipmentService {

    Shipment createShipment(Shipment shipment) throws CustomsComplianceException;

    Shipment findByTrackingNumber(String trackingNumber) throws ShipmentNotFoundException;

    void updateStatus(String trackingNumber, ShipmentStatus newStatus)
            throws ShipmentNotFoundException;

    void allocateAndDispatch(String trackingNumber, String sku, String warehouse, int quantity)
            throws ShipmentNotFoundException, InsufficientInventoryException;

    List<Shipment> findInTransit();

    List<Shipment> findOverdue();

    long refreshTrackingBatch(int batchSize);
}
