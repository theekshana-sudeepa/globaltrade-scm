package lk.jiat.globaltrade.web.mapper;

import jakarta.ejb.EJBAccessException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lk.jiat.globaltrade.common.dto.ErrorResponse;

import java.util.UUID;
import java.util.logging.Logger;

/** 403 for an authenticated caller lacking the role; the WWW layer handles 401. */
@Provider
public class SecurityExceptionMapper implements ExceptionMapper<EJBAccessException> {

    private static final Logger LOG = Logger.getLogger(SecurityExceptionMapper.class.getName());

    @Override
    public Response toResponse(EJBAccessException e) {
        String correlationId = UUID.randomUUID().toString();
        LOG.warning("AUTHORIZATION DENIED corrId=" + correlationId + " : " + e.getMessage());
        return Response.status(Response.Status.FORBIDDEN)
                       .type(MediaType.APPLICATION_JSON)
                       .entity(ErrorResponse.of("SCM-403-FORBIDDEN",
                               "You do not have permission to perform this operation",
                               403, correlationId))
                       .build();
    }
}
