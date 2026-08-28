package lk.jiat.globaltrade.web.mapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lk.jiat.globaltrade.common.dto.ErrorResponse;
import lk.jiat.globaltrade.common.exception.UnauthorizedSupplyChainAccessException;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * 403 for a PROGRAMMATIC authorization denial (defect D11).
 *
 * A declarative denial raises EJBAccessException and SecurityExceptionMapper answers it.
 * A programmatic one raises UnauthorizedSupplyChainAccessException, which extends
 * RuntimeException rather than SupplyChainException, so nothing claimed it and the
 * catch-all reported a working security control as 500.
 *
 * The envelope is deliberately identical to SecurityExceptionMapper's: a client must not
 * be able to tell which half of the model stopped it, or it learns that the resource
 * exists and is merely owned by someone else. Principal and resource are logged only.
 */
@Provider
public class UnauthorizedAccessExceptionMapper
        implements ExceptionMapper<UnauthorizedSupplyChainAccessException> {

    private static final Logger LOG =
            Logger.getLogger(UnauthorizedAccessExceptionMapper.class.getName());

    @Override
    public Response toResponse(UnauthorizedSupplyChainAccessException e) {
        String correlationId = UUID.randomUUID().toString();
        LOG.warning("AUTHORIZATION DENIED (programmatic) corrId=" + correlationId
                    + " principal=" + e.getPrincipal()
                    + " resource=" + e.getAttemptedResource());
        return Response.status(Response.Status.FORBIDDEN)
                       .type(MediaType.APPLICATION_JSON)
                       .entity(ErrorResponse.of("SCM-403-FORBIDDEN",
                               "You do not have permission to perform this operation",
                               403, correlationId))
                       .build();
    }
}
