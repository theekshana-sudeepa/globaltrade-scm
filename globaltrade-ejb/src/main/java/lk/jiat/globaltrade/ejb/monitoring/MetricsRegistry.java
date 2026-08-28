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
 * WildFly sets default-missing-method-permissions-deny-access="true", so on a bean
 * that carries ANY security metadata a method carrying none is DENIED, not unchecked.
 * The class-level @PermitAll supplies that default; the method-level @RolesAllowed
 * below still wins, so snapshot() and reset() stay admin-restricted.
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

    /** Sink on the path of every @Monitored call, including unauthenticated timer threads. */
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
