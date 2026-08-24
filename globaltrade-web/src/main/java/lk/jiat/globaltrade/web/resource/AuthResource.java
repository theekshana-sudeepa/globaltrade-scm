package lk.jiat.globaltrade.web.resource;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStoreHandler;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lk.jiat.globaltrade.common.exception.InvalidCredentialsException;
import lk.jiat.globaltrade.domain.RefreshToken;
import lk.jiat.globaltrade.web.security.JwtUtil;
import lk.jiat.globaltrade.web.service.RefreshTokenService;
import lk.jiat.globaltrade.web.service.UserAccountService;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject private IdentityStoreHandler identityStoreHandler;
    @Inject private RefreshTokenService  refreshTokenService;
    @Inject private UserAccountService   userAccountService;

    public record LoginRequest(String username, String password) { }
    public record RefreshRequest(String refreshToken) { }

    /**
     * dontRollbackOn = InvalidCredentialsException: a failed login must still
     * COMMIT the incremented failed-attempt counter. Rolling it back would reset
     * the brute-force lockout on every wrong password - i.e. the security control
     * would silently do nothing. This is the clearest example in the system of
     * why exception-to-transaction mapping is a design decision, not a detail.
     */
    @POST
    @Path("/login")
    @PermitAll
    @Transactional(rollbackOn = {Exception.class},
                   dontRollbackOn = {InvalidCredentialsException.class})
    public Response login(LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity(Map.of("error", "username and password are required")).build();
        }

        CredentialValidationResult result = identityStoreHandler.validate(
                new UsernamePasswordCredential(request.username(), request.password()));

        if (result.getStatus() != CredentialValidationResult.Status.VALID) {
            // Deliberately generic: never reveal whether the account exists or is locked.
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String principal = result.getCallerPrincipal().getName();
        Set<String> roles = result.getCallerGroups();
        String accessToken = JwtUtil.generateToken(principal, roles);
        RefreshToken refresh = refreshTokenService.create(principal);

        return Response.ok(Map.of(
                "accessToken",  accessToken,
                "refreshToken", refresh.getToken(),
                "tokenType",    "Bearer",
                "expiresIn",    JwtUtil.accessTokenSeconds(),
                "username",     principal,
                "roles",        roles)).build();
    }

    /** Rotating refresh: the presented token is destroyed and replaced. */
    @POST
    @Path("/refresh")
    @PermitAll
    @Transactional
    public Response refresh(RefreshRequest request) {
        if (request == null || request.refreshToken() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity(Map.of("error", "refreshToken is required")).build();
        }
        Optional<RefreshToken> found = refreshTokenService.findValid(request.refreshToken());
        if (found.isEmpty()) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }
        RefreshToken old = found.get();
        String username = old.getUsername();

        refreshTokenService.delete(old.getToken());          // rotate
        RefreshToken fresh = refreshTokenService.create(username);
        Set<String> roles = userAccountService.rolesOf(username);

        return Response.ok(Map.of(
                "accessToken",  JwtUtil.generateToken(username, roles),
                "refreshToken", fresh.getToken(),
                "tokenType",    "Bearer",
                "expiresIn",    JwtUtil.accessTokenSeconds(),
                "username",     username,
                "roles",        roles)).build();
    }

    @POST
    @Path("/logout")
    @PermitAll
    @Transactional
    public Response logout(RefreshRequest request) {
        if (request != null && request.refreshToken() != null) {
            refreshTokenService.delete(request.refreshToken());
        }
        return Response.ok(Map.of("message", "Logged out")).build();
    }
}
