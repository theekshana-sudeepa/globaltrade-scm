package lk.jiat.globaltrade.ejb.monitoring;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.ConcurrencyManagement;
import jakarta.ejb.ConcurrencyManagementType;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import lk.jiat.globaltrade.common.constant.SecurityRoles;
import lk.jiat.globaltrade.common.dto.MetricSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory performance monitor fed by PerformanceMonitoringInterceptor.
 *
 * DESIGN NOTE (critical analysis):
 * The default container-managed concurrency on a @Singleton takes a WRITE lock on
 * every method, serialising all callers. For a metrics sink hit on EVERY business
 * invocation that would itself become the bottleneck we are trying to measure.
 * We therefore use BEAN-managed concurrency with lock-free ConcurrentHashMap +
 * AtomicLong counters, so recording a metric costs a single CAS and never blocks.
 * Measured: 0.08 us/record vs 4.1 us/record under the default write lock at
 * 200 concurrent threads (Test Report section 3.2).
 *
 * SECURITY NOTE (defect D07):
 * WildFly runs with ejb3/default-missing-method-permissions-deny-access="true".
 * Under that setting, once a bean carries ANY security metadata, every method
 * that does NOT carry its own becomes implicitly DENIED TO ALL CALLERS - it is
 * not "unchecked". Because snapshot() and reset() are @RolesAllowed, record()
 * carried no metadata of its own and so became callable by NOBODY - including the
 * interceptor that feeds it on every monitored invocation. The class-level
 * @PermitAll below supplies the missing metadata for record() (and for any method
 * added later); the method-level @RolesAllowed on snapshot()/reset() still wins,
 * so the read and reset operations remain admin-restricted.
 */
@Singleton
@Startup
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@PermitAll                       // default for this bean; overridden per method below
public class MetricsRegistry {

    private final Map<String, Counters> metrics = new ConcurrentHashMap<>();

    private static final class Counters {
        final AtomicLong invocations = new AtomicLong();
        final AtomicLong totalNanos  = new AtomicLong();
        final AtomicLong maxNanos    = new AtomicLong();
        final AtomicLong failures    = new AtomicLong();
    }

    /**
     * Instrumentation sink on the invocation path of EVERY @Monitored method.
     * MUST stay @PermitAll: it is called on behalf of whatever identity is running,
     * including the container's UNAUTHENTICATED timer threads.
     */
    @PermitAll
    public void record(String method, long nanos, boolean failed) {
        Counters c = metrics.computeIfAbsent(method, k -> new Counters());
        c.invocations.incrementAndGet();
        c.totalNanos.addAndGet(nanos);
        c.maxNanos.accumulateAndGet(nanos, Math::max);
        if (failed) c.failures.incrementAndGet();
    }

    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.LOGISTICS_COORDINATOR})
    public List<MetricSnapshot> snapshot() {
        List<MetricSnapshot> out = new ArrayList<>(metrics.size());
        metrics.forEach((name, c) -> out.add(new MetricSnapshot(
                name, c.invocations.get(), c.totalNanos.get(), c.maxNanos.get(), c.failures.get())));
        out.sort(Comparator.comparingDouble(MetricSnapshot::averageMillis).reversed());
        return out;
    }

    @RolesAllowed(SecurityRoles.ADMIN)
    public void reset() { metrics.clear(); }
}
