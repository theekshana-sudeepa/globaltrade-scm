package lk.jiat.globaltrade.common.exception;

import jakarta.ejb.ApplicationException;

/** Thrown by PROGRAMMATIC authorization checks (SessionContext.isCallerInRole). */
@ApplicationException(rollback = true, inherited = true)
public class UnauthorizedSupplyChainAccessException extends RuntimeException {

    private final String principal;
    private final String attemptedResource;

    public UnauthorizedSupplyChainAccessException(String principal, String attemptedResource) {
        super("Principal '" + principal + "' is not permitted to access " + attemptedResource,
              null, false, false);
        this.principal = principal;
        this.attemptedResource = attemptedResource;
    }

    public String getPrincipal()         { return principal; }
    public String getAttemptedResource() { return attemptedResource; }
}
