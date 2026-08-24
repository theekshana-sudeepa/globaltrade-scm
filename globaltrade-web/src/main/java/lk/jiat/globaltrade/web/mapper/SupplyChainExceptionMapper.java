package lk.jiat.globaltrade.web.mapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lk.jiat.globaltrade.common.dto.ErrorResponse;
import lk.jiat.globaltrade.common.exception.*;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maps every known business exception to the correct HTTP status.
 * Nothing internal ever reaches the client: the caller gets a stable error code,
 * a safe message and a correlation id that support can grep in the audit trail.
 */
@Provider
public class SupplyChainExceptionMapper implements ExceptionMapper<SupplyChainException> {

    private static final Logger LOG = Logger.getLogger(SupplyChainExceptionMapper.class.getName());

    @Override
    public Response toResponse(SupplyChainException e) {
        String correlationId = UUID.randomUUID().toString();
        Response.Status status = statusFor(e);

        LOG.log(Level.INFO, "Business exception corrId=" + correlationId
                + " code=" + e.getErrorCode() + " status=" + status.getStatusCode()
                + " msg=" + e.getMessage());

        return Response.status(status)
                       .type(MediaType.APPLICATION_JSON)
                       .entity(ErrorResponse.of(e.getErrorCode(), e.getMessage(),
                                                status.getStatusCode(), correlationId))
                       .build();
    }

    private Response.Status statusFor(SupplyChainException e) {
        if (e instanceof ShipmentNotFoundException)      return Response.Status.NOT_FOUND;              // 404
        if (e instanceof InsufficientInventoryException) return Response.Status.CONFLICT;               // 409
        if (e instanceof CustomsComplianceException)     return Response.Status.fromStatusCode(422);    // 422
        if (e instanceof VendorValidationException)      return Response.Status.BAD_REQUEST;            // 400
        return Response.Status.INTERNAL_SERVER_ERROR;
    }
}
