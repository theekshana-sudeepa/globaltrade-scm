package lk.jiat.globaltrade.web.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.security.enterprise.AuthenticationException;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bearer-token authentication mechanism.
 *
 * On a valid token it calls notifyContainerAboutLogin(), which is what makes the
 * identity flow all the way down into the EJB tier - so SessionContext
 * .getCallerPrincipal() and .isCallerInRole() inside ShipmentServiceBean see the
 * same caller the HTTP layer authenticated. That propagation is the whole point
 * of using container security rather than rolling a filter by hand.
 */
@ApplicationScoped
public class JwtAuthenticationMechanism implements HttpAuthenticationMechanism {

    private static final Logger LOG =
            Logger.getLogger(JwtAuthenticationMechanism.class.getName());

    private static final String BEARER = "Bearer ";

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request,
                                                HttpServletResponse response,
                                                HttpMessageContext context)
            throws AuthenticationException {

        // Security response headers on every request.
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length()).trim();
            if (JwtUtil.isValid(token)) {
                DecodedJWT jwt = JwtUtil.parseToken(token);
                List<String> claimed = jwt.getClaim("roles").asList(String.class);
                Set<String> roles = claimed != null ? new HashSet<>(claimed) : Set.of();
                return context.notifyContainerAboutLogin(jwt.getSubject(), roles);
            }
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Rejected an invalid or expired bearer token from "
                         + request.getRemoteAddr());
            }
        }

        if (context.isProtected()) {
            return context.responseUnauthorized();
        }
        return context.doNothing();
    }
}
