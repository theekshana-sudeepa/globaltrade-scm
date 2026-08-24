package lk.jiat.globaltrade.ejb.interceptor;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import lk.jiat.globaltrade.common.exception.CustomsComplianceException;
import lk.jiat.globaltrade.common.interceptorbinding.ComplianceChecked;
import lk.jiat.globaltrade.domain.CustomsDeclaration;
import lk.jiat.globaltrade.domain.Shipment;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * INTERCEPTOR 3 of 5.
 *
 * Enforces international trade rules as a cross-cutting concern rather than
 * scattering regulatory if-statements through every business bean. When the
 * regulations change, one class changes.
 */
@ComplianceChecked
@Interceptor
@Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 200)
public class ComplianceCheckInterceptor {

    private static final Logger LOG = Logger.getLogger(ComplianceCheckInterceptor.class.getName());

    /** Countries under trade restriction - in production this comes from a rules table. */
    private static final Set<String> EMBARGOED = Set.of("XX", "YY");

    /** Destinations requiring an advance electronic manifest (WCO SAFE framework). */
    private static final Set<String> ADVANCE_MANIFEST_REQUIRED = Set.of("US", "GB", "DE", "FR", "NL");

    @AroundInvoke
    public Object checkCompliance(InvocationContext ctx) throws Exception {
        for (Object param : ctx.getParameters()) {

            if (param instanceof Shipment s) {
                if (EMBARGOED.contains(s.getDestinationCountry())
                        || EMBARGOED.contains(s.getOriginCountry())) {
                    throw new CustomsComplianceException("TRADE-EMBARGO",
                        "Route " + s.getOriginCountry() + "->" + s.getDestinationCountry()
                            + " is subject to a trade restriction");
                }
                if (ADVANCE_MANIFEST_REQUIRED.contains(s.getDestinationCountry())
                        && s.getDeclaredValue() == null) {
                    throw new CustomsComplianceException("WCO-SAFE",
                        "Advance manifest requires a declared value for destination "
                            + s.getDestinationCountry());
                }
            }

            if (param instanceof CustomsDeclaration d) {
                if (d.getHsCode() == null || !d.getHsCode().matches("^\\d{6,10}$")) {
                    throw new CustomsComplianceException("HS-CODE",
                        "HS code must be 6-10 digits, was: " + d.getHsCode());
                }
            }
        }

        Object result = ctx.proceed();

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("[" + ctx.getContextData().get(CorrelationContext.CORRELATION_ID)
                     + "] compliance cleared for " + ctx.getMethod().getName());
        }
        return result;
    }
}
