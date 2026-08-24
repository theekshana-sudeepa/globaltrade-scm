package lk.jiat.globaltrade.web.mapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lk.jiat.globaltrade.common.dto.ErrorResponse;
import lk.jiat.globaltrade.common.exception.InvalidCredentialsException;

import java.util.UUID;

@Provider
public class InvalidCredentialsExceptionMapper
        implements ExceptionMapper<InvalidCredentialsException> {

    @Override
    public Response toResponse(InvalidCredentialsException e) {
        return Response.status(Response.Status.UNAUTHORIZED)
                       .type(MediaType.APPLICATION_JSON)
                       .header("WWW-Authenticate", "Bearer realm=\"globaltrade\"")
                       .entity(ErrorResponse.of("SCM-401-UNAUTHORIZED",
                               "Invalid username or password", 401,
                               UUID.randomUUID().toString()))
                       .build();
    }
}
