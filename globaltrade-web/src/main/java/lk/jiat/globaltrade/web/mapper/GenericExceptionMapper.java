package lk.jiat.globaltrade.web.mapper;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lk.jiat.globaltrade.common.dto.ErrorResponse;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Last-resort safety net.
 *
 * CWE-209 mitigation: the client receives ONLY a correlation id. The stack trace
 * goes to the structured server log, never to the wire - an attacker must not be
 * able to fingerprint the ORM, driver versions or table names from an error body.
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GenericExceptionMapper.class.getName());

    @Override
    public Response toResponse(Exception e) {
        if (e instanceof WebApplicationException wae) {
            return wae.getResponse();          // preserve 404/405/415 etc.
        }
        String correlationId = UUID.randomUUID().toString();
        LOG.log(Level.SEVERE, "UNHANDLED EXCEPTION corrId=" + correlationId, e);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                       .type(MediaType.APPLICATION_JSON)
                       .entity(ErrorResponse.of("SCM-500-INTERNAL",
                               "An unexpected error occurred. Quote reference "
                                       + correlationId + " to support.",
                               500, correlationId))
                       .build();
    }
}
