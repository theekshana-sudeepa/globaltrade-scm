package lk.jiat.globaltrade.ejb.interceptor;

import jakarta.annotation.Priority;
import jakarta.ejb.EJB;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.AroundTimeout;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import lk.jiat.globaltrade.common.interceptorbinding.Monitored;
import lk.jiat.globaltrade.ejb.monitoring.MetricsRegistry;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * INTERCEPTOR 5 of 5 - innermost, closest to the business method, so the timing
 * it reports is the business method itself and not the cost of the other four
 * interceptors. (Put it outermost and you measure your own instrumentation.)
 */
@Monitored
@Interceptor
@Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 400)
public class PerformanceMonitoringInterceptor {

    private static final Logger LOG = Logger.getLogger(PerformanceMonitoringInterceptor.class.getName());

    /** Anything slower than this is reported as a supply-chain latency incident. */
    private static final long SLOW_THRESHOLD_MILLIS = 500L;

    @EJB
    private MetricsRegistry metrics;

    @AroundInvoke
    public Object monitor(InvocationContext ctx) throws Exception { return time(ctx); }

    @AroundTimeout
    public Object monitorTimeout(InvocationContext ctx) throws Exception { return time(ctx); }

    private Object time(InvocationContext ctx) throws Exception {
        String key = ctx.getMethod().getDeclaringClass().getSimpleName()
                   + "." + ctx.getMethod().getName();
        long start = System.nanoTime();
        boolean failed = false;
        try {
            return ctx.proceed();
        } catch (Exception e) {
            failed = true;
            throw e;
        } finally {
            // DEFECT D08: this block runs in a finally, so ANYTHING it throws silently
            // REPLACES the business method's return value or its real exception. When
            // metrics.record() started throwing EJBAccessException (defect D07), every
            // monitored call surfaced as a 403 that had nothing to do with the caller's
            // roles. Instrumentation must never be able to change the outcome of the
            // thing it instruments, so the whole block is now failure-isolated.
            try {
                long elapsed = System.nanoTime() - start;
                metrics.record(key, elapsed, failed);
                long millis = elapsed / 1_000_000L;
                if (millis > SLOW_THRESHOLD_MILLIS && LOG.isLoggable(Level.WARNING)) {
                    LOG.warning("SLOW SUPPLY CHAIN OPERATION " + key + " took " + millis
                                + "ms (threshold " + SLOW_THRESHOLD_MILLIS + "ms) corrId="
                                + ctx.getContextData().get(CorrelationContext.CORRELATION_ID));
                }
            } catch (RuntimeException instrumentationFailure) {
                LOG.log(Level.WARNING, "Metrics recording failed for " + key
                        + " - business result is unaffected", instrumentationFailure);
            }
        }
    }
}
