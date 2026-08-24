package lk.jiat.globaltrade.web.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJB;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lk.jiat.globaltrade.common.constant.SecurityRoles;
import lk.jiat.globaltrade.common.dto.MetricSnapshot;
import lk.jiat.globaltrade.ejb.monitoring.MetricsRegistry;
import lk.jiat.globaltrade.ejb.timer.CustomsDeadlineTimerBean;
import lk.jiat.globaltrade.ejb.timer.ShipmentTrackingTimerBean;

import java.util.List;
import java.util.Map;

/** Performance and timer monitoring - the evidence source for the Test Report. */
@Path("/admin/monitoring")
@Produces(MediaType.APPLICATION_JSON)
public class MonitoringResource {

    @EJB private MetricsRegistry metricsRegistry;
    @EJB private ShipmentTrackingTimerBean trackingTimers;
    @EJB private CustomsDeadlineTimerBean  deadlineTimers;

    @GET
    @Path("/metrics")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.LOGISTICS_COORDINATOR})
    public Response metrics() {
        List<MetricSnapshot> snapshot = metricsRegistry.snapshot();
        return Response.ok(Map.of(
                "generatedAt", System.currentTimeMillis(),
                "methodCount", snapshot.size(),
                "metrics", snapshot.stream().map(m -> Map.of(
                        "method",       m.method(),
                        "invocations",  m.invocations(),
                        "avgMillis",    Math.round(m.averageMillis() * 1000d) / 1000d,
                        "maxMillis",    Math.round(m.maxMillis()     * 1000d) / 1000d,
                        "failures",     m.failures())).toList()
        )).build();
    }

    @GET
    @Path("/timers")
    @RolesAllowed(SecurityRoles.ADMIN)
    public Response timers() {
        return Response.ok(Map.of(
                "automaticTimers",   trackingTimers.describeTimers(),
                "programmaticCount", deadlineTimers.activeTimerCount())).build();
    }

    @DELETE
    @Path("/metrics")
    @RolesAllowed(SecurityRoles.ADMIN)
    public Response reset() {
        metricsRegistry.reset();
        return Response.noContent().build();
    }
}
