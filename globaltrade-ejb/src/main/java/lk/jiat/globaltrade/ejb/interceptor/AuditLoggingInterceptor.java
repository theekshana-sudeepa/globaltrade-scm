package lk.jiat.globaltrade.ejb.interceptor;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.AroundTimeout;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import lk.jiat.globaltrade.common.interceptorbinding.Audited;
import lk.jiat.globaltrade.ejb.service.AuditService;

import java.util.Arrays;

/**
 * INTERCEPTOR 4 of 5.
 *
 * Writes the customs-compliance audit trail. Delegates to AuditServiceBean,
 * which runs REQUIRES_NEW, so the audit row COMMITS even when the business
 * transaction rolls back. Without that, every failed shipment would erase the
 * evidence of its own failure - the exact opposite of what a customs auditor needs.
 */
@Audited
@Interceptor
@Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 300)
public class AuditLoggingInterceptor {

    @Inject
    private AuditService auditService;

    @AroundInvoke
    public Object audit(InvocationContext ctx) throws Exception {
        return doAudit(ctx, resolveAction(ctx));
    }

    @AroundTimeout
    public Object auditTimeout(InvocationContext ctx) throws Exception {
        return doAudit(ctx, "TIMER:" + ctx.getMethod().getName());
    }

    private Object doAudit(InvocationContext ctx, String action) throws Exception {
        String correlationId = String.valueOf(
                ctx.getContextData().getOrDefault(CorrelationContext.CORRELATION_ID, "n/a"));
        String principal = String.valueOf(
                ctx.getContextData().getOrDefault(CorrelationContext.PRINCIPAL, "anonymous"));

        long start = System.nanoTime();
        try {
            Object result = ctx.proceed();
            auditService.recordAsync(correlationId, action, principal, "SUCCESS",
                    (System.nanoTime() - start) / 1_000_000L,
                    summarise(ctx.getParameters()));
            return result;
        } catch (Exception e) {
            auditService.recordAsync(correlationId, action, principal, "FAILURE",
                    (System.nanoTime() - start) / 1_000_000L,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;   // never swallow - the container must still see it
        }
    }

    private String resolveAction(InvocationContext ctx) {
        Audited a = ctx.getMethod().getAnnotation(Audited.class);
        if (a == null) a = ctx.getMethod().getDeclaringClass().getAnnotation(Audited.class);
        if (a != null && !a.value().isEmpty()) return a.value();
        return ctx.getMethod().getDeclaringClass().getSimpleName() + "." + ctx.getMethod().getName();
    }

    /** Never log full payloads - PII and vendor commercial data must not reach the log. */
    private String summarise(Object[] params) {
        if (params == null || params.length == 0) return "[]";
        return Arrays.stream(params)
                     .map(p -> p == null ? "null" : p.getClass().getSimpleName())
                     .reduce((a, b) -> a + "," + b).orElse("[]");
    }
}
