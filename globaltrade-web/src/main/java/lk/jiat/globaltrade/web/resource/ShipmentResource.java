package lk.jiat.globaltrade.web.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import lk.jiat.globaltrade.common.constant.SecurityRoles;
import lk.jiat.globaltrade.common.constant.ShipmentStatus;
import lk.jiat.globaltrade.common.dto.ShipmentDTO;
import lk.jiat.globaltrade.common.exception.CustomsComplianceException;
import lk.jiat.globaltrade.common.exception.InsufficientInventoryException;
import lk.jiat.globaltrade.common.exception.ShipmentNotFoundException;
import lk.jiat.globaltrade.domain.Shipment;
import lk.jiat.globaltrade.ejb.service.ShipmentService;

import java.util.List;
import java.util.Map;

/**
 * RESTful shipment API.
 *
 * Follows the best-practice rules from the module reading: noun resource names,
 * hierarchical URIs, HTTP verbs carry the action, 201 + Location on creation,
 * 204 on a successful state change with no body, and a self link on every
 * representation (connectedness).
 */
@Path("/shipments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ShipmentResource {

    @EJB
    private ShipmentService shipmentService;

    @Context
    private UriInfo uriInfo;

    @POST
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.LOGISTICS_COORDINATOR})
    public Response create(Shipment shipment) throws CustomsComplianceException {
        Shipment created = shipmentService.createShipment(shipment);
        return Response.created(uriInfo.getAbsolutePathBuilder()
                                       .path(created.getTrackingNumber()).build())
                       .entity(toDto(created))
                       .build();
    }

    @GET
    @Path("/{trackingNumber}")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.LOGISTICS_COORDINATOR,
                   SecurityRoles.CUSTOMS_AGENT, SecurityRoles.WAREHOUSE_MANAGER,
                   SecurityRoles.VENDOR_REP, SecurityRoles.CARRIER_SYSTEM})
    public Response get(@PathParam("trackingNumber") String trackingNumber)
            throws ShipmentNotFoundException {
        return Response.ok(toDto(shipmentService.findByTrackingNumber(trackingNumber))).build();
    }

    /** PUT is idempotent: setting the same status twice has the same effect. */
    @PUT
    @Path("/{trackingNumber}/status")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.LOGISTICS_COORDINATOR,
                   SecurityRoles.CARRIER_SYSTEM})
    public Response updateStatus(@PathParam("trackingNumber") String trackingNumber,
                                 Map<String, String> body) throws ShipmentNotFoundException {
        String raw = body == null ? null : body.get("status");
        if (raw == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity(Map.of("error", "status is required")).build();
        }
        shipmentService.updateStatus(trackingNumber, ShipmentStatus.valueOf(raw));
        return Response.noContent().build();                      // 204
    }

    /** POST because allocation is NOT idempotent - two calls reserve twice. */
    @POST
    @Path("/{trackingNumber}/allocations")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.WAREHOUSE_MANAGER,
                   SecurityRoles.LOGISTICS_COORDINATOR})
    public Response allocate(@PathParam("trackingNumber") String trackingNumber,
                             Map<String, Object> body)
            throws ShipmentNotFoundException, InsufficientInventoryException {
        shipmentService.allocateAndDispatch(trackingNumber,
                (String) body.get("sku"),
                (String) body.get("warehouse"),
                ((Number) body.get("quantity")).intValue());
        return Response.status(Response.Status.CREATED)
                       .entity(Map.of("message", "allocated")).build();
    }

    @GET
    @Path("/in-transit")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.LOGISTICS_COORDINATOR})
    public Response inTransit(@QueryParam("limit") @DefaultValue("100") int limit) {
        List<ShipmentDTO> all = shipmentService.findInTransit().stream()
                .limit(limit).map(this::toDto).toList();
        return Response.ok(Map.of("count", all.size(), "items", all,
                                  "self", uriInfo.getAbsolutePath().toString())).build();
    }

    @GET
    @Path("/overdue")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.LOGISTICS_COORDINATOR})
    public Response overdue() {
        List<ShipmentDTO> all = shipmentService.findOverdue().stream().map(this::toDto).toList();
        return Response.ok(Map.of("count", all.size(), "items", all)).build();
    }

    private ShipmentDTO toDto(Shipment s) {
        return new ShipmentDTO(s.getId(), s.getTrackingNumber(), s.getOriginCountry(),
                s.getDestinationCountry(), s.getCarrierCode(), s.getStatus(),
                s.getEstimatedArrival(), s.getVendor() == null ? null : s.getVendor().getId());
    }
}
