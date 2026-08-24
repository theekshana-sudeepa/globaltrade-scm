package lk.jiat.globaltrade.ejb.integration;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import lk.jiat.globaltrade.common.constant.SecurityRoles;
import lk.jiat.globaltrade.common.interceptorbinding.Audited;
import lk.jiat.globaltrade.common.interceptorbinding.Monitored;

@Stateless
@Audited
@Monitored
@RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.CARRIER_SYSTEM})
public class CarrierIntegrationBean implements CarrierIntegrationRemote {

    /**
     * NEVER: this method calls out to a third-party carrier over HTTP. It must
     * never run inside a transaction, because an external call cannot be rolled
     * back and holding a transaction open across it would pin a connection for
     * seconds. NEVER makes that contract explicit and enforced - a caller who
     * wraps it in a transaction gets an immediate EJBException instead of a
     * slow, silent resource leak.
     */
    @Override
    @TransactionAttribute(TransactionAttributeType.NEVER)
    public String pushStatusUpdate(String trackingNumber, String statusCode) {
        return "ACK:" + trackingNumber + ":" + statusCode;
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.NEVER)
    public boolean isCarrierReachable(String carrierCode) {
        return carrierCode != null && !carrierCode.isBlank();
    }
}
