package lk.jiat.globaltrade.common.exception;

import jakarta.ejb.ApplicationException;

/**
 * Regulatory rejection. rollback = true because a declaration that fails
 * compliance must not be persisted in a half-submitted state, which would
 * corrupt the customs audit trail.
 */
@ApplicationException(rollback = true, inherited = true)
public class CustomsComplianceException extends SupplyChainException {

    private final String regulation;

    public CustomsComplianceException(String regulation, String detail) {
        super("SCM-422-CUSTOMS", "Compliance failure [" + regulation + "]: " + detail);
        this.regulation = regulation;
    }

    public String getRegulation() { return regulation; }
}
