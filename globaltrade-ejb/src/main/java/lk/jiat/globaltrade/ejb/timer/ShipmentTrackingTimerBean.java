package lk.jiat.globaltrade.ejb.timer;

import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.*;
import lk.jiat.globaltrade.common.constant.SecurityRoles;
import lk.jiat.globaltrade.common.interceptorbinding.Audited;
import lk.jiat.globaltrade.common.interceptorbinding.Monitored;
import lk.jiat.globaltrade.ejb.service.ShipmentService;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DECLARATIVE (AUTOMATIC) TIMERS - shipment tracking and delay detection.
 *
 * @Singleton + @Startup: the container creates exactly one instance per JVM at
 * deployment. @ConcurrencyManagement(BEAN) prevents the default write lock from
 * serialising the two schedules against each other.
 *
 * CLUSTERING (critical analysis): with persistent = true and a shared timer
 * datastore, Payara/WildFly elect a SINGLE node to fire each automatic timer, so
 * a 6-node global cluster polls carriers once, not six times. Set persistent =
 * false and every node fires - which is correct for the cache refresh below and
 * catastrophic for the carrier poll (6x the API cost and 6x the rate-limit risk).
 */
@Singleton
@Startup
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@Audited
@Monitored
@PermitAll                    // inbound: let the container invoke the callbacks
@RunAs(SecurityRoles.ADMIN)   // outbound: detectDelayedShipments() calls @RolesAllowed findOverdue()
public class ShipmentTrackingTimerBean {

    private static final Logger LOG = Logger.getLogger(ShipmentTrackingTimerBean.class.getName());

    private static final int BATCH_SIZE = 500;

    @EJB
    private ShipmentService shipmentService;

    @Resource
    private TimerService timerService;

    /** Guard against overlapping runs when a poll takes longer than the interval. */
    private volatile boolean pollInProgress = false;

    // ------------------------------------------------------------------
    // Every 5 minutes: pull live status for shipments in transit.
    // PERSISTENT: a missed poll during a restart must be replayed, because a
    // customer-visible ETA depends on it.
    // ------------------------------------------------------------------
    @Schedule(minute = "*/5", hour = "*", persistent = true,
              info = "SHIPMENT_STATUS_POLL", timezone = "UTC")
    public void pollShipmentStatus() {
        if (pollInProgress) {
            LOG.warning("Previous shipment poll still running - skipping this tick "
                        + "(consider widening the schedule or increasing BATCH_SIZE)");
            return;
        }
        pollInProgress = true;
        long start = System.currentTimeMillis();
        try {
            // REQUIRES_NEW per batch: batch N committing is not undone by batch N+1 failing.
            long updated = shipmentService.refreshTrackingBatch(BATCH_SIZE);
            if (LOG.isLoggable(Level.INFO)) {
                LOG.info("Shipment poll complete: " + updated + " shipments refreshed in "
                         + (System.currentTimeMillis() - start) + " ms");
            }
        } catch (RuntimeException e) {
            // A timer callback must never let an exception escape unlogged: the
            // container would retry or silently drop the timer depending on vendor.
            LOG.log(Level.SEVERE, "Shipment status poll failed", e);
        } finally {
            pollInProgress = false;
        }
    }

    // ------------------------------------------------------------------
    // Every 15 minutes: detect overdue shipments and raise alerts.
    // ------------------------------------------------------------------
    @Schedule(minute = "*/15", hour = "*", persistent = true,
              info = "DELAY_DETECTION", timezone = "UTC")
    public void detectDelayedShipments() {
        try {
            int overdue = shipmentService.findOverdue().size();
            if (overdue > 0) {
                LOG.warning("SUPPLY CHAIN ALERT: " + overdue + " shipments past their ETA");
                // Production: publish to a JMS topic so the alerting module,
                // the customer portal and the vendor scorecard all react
                // without any coupling to this bean.
            }
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Delay detection failed", e);
        }
    }

    // ------------------------------------------------------------------
    // MULTIPLE SCHEDULES ON ONE METHOD - route optimisation runs at the two
    // daily dispatch windows rather than on a fixed interval.
    // ------------------------------------------------------------------
    @Schedules({
        @Schedule(hour = "4",  minute = "30", persistent = true, info = "ROUTE_OPT_AM", timezone = "UTC"),
        @Schedule(hour = "16", minute = "30", persistent = true, info = "ROUTE_OPT_PM", timezone = "UTC")
    })
    public void optimiseRoutes() {
        LOG.info("Route optimisation window started");
        // Delegates to the routing engine; kept short so the timer thread is freed.
    }

    // ------------------------------------------------------------------
    // NON-PERSISTENT: in-memory route cache warm-up. Every cluster node needs
    // its OWN cache warmed, so firing on all nodes is the desired behaviour,
    // and a missed run during downtime is irrelevant.
    // ------------------------------------------------------------------
    @Schedule(minute = "*/2", hour = "*", persistent = false, info = "ROUTE_CACHE_REFRESH")
    public void refreshRouteCache() {
        if (LOG.isLoggable(Level.FINE)) LOG.fine("Route cache refreshed on this node");
    }

    // ------------------------------------------------------------------
    // Operational visibility: list every timer this bean owns.
    // ------------------------------------------------------------------
    public String describeTimers() {
        StringBuilder sb = new StringBuilder();
        for (Timer t : timerService.getTimers()) {
            sb.append(t.getInfo()).append(" next=").append(t.getNextTimeout())
              .append(" persistent=").append(t.isPersistent()).append('\n');
        }
        return sb.toString();
    }
}
