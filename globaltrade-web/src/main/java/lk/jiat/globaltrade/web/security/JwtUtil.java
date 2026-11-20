package lk.jiat.globaltrade.web.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * JWT issuing and verification.
 *
 * SECURITY NOTE - this is a deliberate improvement over the common teaching
 * example, which hard-codes the signing secret as a private static final String
 * in source. A key in source control is a key in every developer laptop, every
 * CI log and every git history rewrite. Here the key is read from an environment
 * variable / system property, and the application REFUSES TO START without it.
 *
 * Other hardening applied:
 *   - explicit issuer + audience claims, verified on every request
 *   - 5-minute access-token lifetime (short blast radius for a stolen token)
 *   - jti claim so an individual token can be added to a revocation denylist
 *   - 60-second leeway for clock skew across globally distributed nodes
 */
public final class JwtUtil {

    private static final String ISSUER   = "globaltrade-scm";
    private static final String AUDIENCE = "globaltrade-api";
    private static final long   ACCESS_TOKEN_SECONDS = 300;   // 5 minutes

    private static final Algorithm ALGORITHM;
    private static final JWTVerifier VERIFIER;

    static {
        String secret = System.getenv("GT_JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            secret = System.getProperty("gt.jwt.secret");
        }
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "GT_JWT_SECRET must be supplied and at least 32 characters. "
              + "Refusing to start with a default or absent signing key.");
        }
        ALGORITHM = Algorithm.HMAC256(secret.getBytes(StandardCharsets.UTF_8));
        VERIFIER  = JWT.require(ALGORITHM)
                       .withIssuer(ISSUER)
                       .withAudience(AUDIENCE)
                       .acceptLeeway(60)
                       .build();
    }

    private JwtUtil() { }

    public static String generateToken(String username, Set<String> roles) {
        Instant now = Instant.now();
        return JWT.create()
                  .withJWTId(UUID.randomUUID().toString())
                  .withIssuer(ISSUER)
                  .withAudience(AUDIENCE)
                  .withSubject(username)
                  .withClaim("roles", List.copyOf(roles))
                  .withIssuedAt(Date.from(now))
                  .withExpiresAt(Date.from(now.plusSeconds(ACCESS_TOKEN_SECONDS)))
                  .sign(ALGORITHM);
    }

    public static DecodedJWT parseToken(String token) { return VERIFIER.verify(token); }

    /** Single-pass signature and claim verification without double-parsing on the hot path. */
    public static Optional<DecodedJWT> verify(String token) {
        try {
            return Optional.of(VERIFIER.verify(token));
        } catch (JWTVerificationException e) {
            return Optional.empty();
        }
    }

    public static boolean isValid(String token) {
        return verify(token).isPresent();
    }

    public static long accessTokenSeconds() { return ACCESS_TOKEN_SECONDS; }
}
