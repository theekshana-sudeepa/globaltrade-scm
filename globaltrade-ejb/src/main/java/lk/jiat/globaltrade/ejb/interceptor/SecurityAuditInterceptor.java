package lk.jiat.globaltrade.ejb.interceptor;

import jakarta.annotation.Priority;
import jakarta.annotation.Resource;
import jakarta.ejb.SessionContext;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.AroundTimeout;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import lk.jiat.globaltrade.common.interceptorbinding.Audited;

import java.security.Principal;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * INTERCEPTOR 1 of 5 - outermost.
 *
 * Establishes the security + correlation context for the whole chain. Every
 * downstream interceptor and the audit record itself read the correlation id
 * from InvocationContext.getContextData(), which is shared down the chain and
 * back up again.
 */
@Audited
@Interceptor
@Priority(jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE + 10)
public class SecurityAuditInterceptor {

    private static final Logger LOG = Logger.getLogger(SecurityAuditInterceptor.class.getName());

    @Resource
    private SessionContext sessionContext;

    @AroundInvoke
    public Object establishContext(InvocationContext ctx) throws Exception {
        String correlationId = UUID.randomUUID().toString();
        ctx.getContextData().put(CorrelationContext.CORRELATION_ID, correlationId);
        ctx.getContextData().put(CorrelationContext.START_NANOS, System.nanoTime());

        String principalName = "anonymous";
        try {
            Principal p = sessionContext.getCallerPrincipal();
            if (p != null) principalName = p.getName();
        } catch (IllegalStateException ignored) {
            // no security context (e.g. timer callback) - acceptable
        }
        ctx.getContextData().put(CorrelationContext.PRINCIPAL, principalName);

        // Guarded logging: never build the message unless it will actually be emitted.
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("[" + correlationId + "] " + principalName + " -> "
                     + ctx.getMethod().getDeclaringClass().getSimpleName()
                     + "." + ctx.getMethod().getName());
        }
        return ctx.proceed();
    }

    /**
     * The SAME interceptor also covers timeout callbacks, so scheduled logistics
     * jobs land in the audit trail with a correlation id exactly like user calls.
     */
    @AroundTimeout
    public Object aroundTimeout(InvocationContext ctx) throws Exception {
        // D09: NOT "TIMER-" + uuid. audit_log.correlation_id is CHAR(36), sized for a
        // bare UUID, so the 42-char prefixed form was rejected by MySQL and EVERY
        // timer-run audit row was lost. The timer origin is already recorded twice
        // over - in action ("TIMER:<method>") and in principal ("system:timer") -
        // so the prefix was redundant as well as too long.
        ctx.getContextData().put(CorrelationContext.CORRELATION_ID, UUID.randomUUID().toString());
        ctx.getContextData().put(CorrelationContext.START_NANOS, System.nanoTime());
        ctx.getContextData().put(CorrelationContext.PRINCIPAL, "system:timer");
        return ctx.proceed();
    }
}
