package lk.jiat.globaltrade.common.exception;

import jakarta.ejb.ApplicationException;

/**
 * Runtime (unchecked) exception deliberately MARKED as an application exception
 * with rollback = false.
 *
 * Rationale (critical analysis material): a carrier outage is an infrastructure
 * failure, but it is EXPECTED in a 50-country logistics network. If we let it
 * propagate as an ordinary system exception the container would roll back the
 * transaction AND destroy the bean instance on every carrier hiccup, churning
 * the stateless pool. Marking it as an application exception keeps the pooled
 * instance alive and lets the circuit breaker degrade gracefully.
 */
@ApplicationException(rollback = false, inherited = true)
public class CarrierUnavailableException extends RuntimeException {

    private final String carrierCode;

    public CarrierUnavailableException(String carrierCode, String message) {
        super(message, null, false, false);
        this.carrierCode = carrierCode;
    }

    public String getCarrierCode() { return carrierCode; }
}
