package lk.jiat.globaltrade.common.exception;

import jakarta.ejb.ApplicationException;
import java.util.List;

/** Raised by the validation interceptor before the business method runs. */
@ApplicationException(rollback = false, inherited = true)
public class VendorValidationException extends SupplyChainException {

    private final List<String> violations;

    public VendorValidationException(List<String> violations) {
        super("SCM-400-VENDOR", "Vendor data validation failed: " + violations, false);
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() { return violations; }
}
