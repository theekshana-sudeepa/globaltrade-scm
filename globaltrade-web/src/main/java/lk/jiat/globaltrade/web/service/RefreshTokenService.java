package lk.jiat.globaltrade.web.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lk.jiat.globaltrade.domain.RefreshToken;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Refresh tokens are ROTATED on every use: the presented token is deleted and a
 * fresh one issued. If an attacker replays a stolen refresh token after the
 * legitimate client has already used it, the lookup fails and the session dies -
 * refresh-token-reuse detection with no extra state.
 */
@ApplicationScoped
@Transactional
public class RefreshTokenService {

    private static final long VALIDITY_DAYS = 7;
    private static final SecureRandom RANDOM = new SecureRandom();

    @PersistenceContext(unitName = "GlobalTradePU")
    private EntityManager em;

    public RefreshToken create(String username) {
        byte[] raw = new byte[48];
        RANDOM.nextBytes(raw);                       // CSPRNG, not UUID concatenation
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant expiry = Instant.now().plusSeconds(VALIDITY_DAYS * 24 * 3600);
        RefreshToken rt = new RefreshToken(token, username, expiry);
        em.persist(rt);
        return rt;
    }

    public Optional<RefreshToken> findValid(String token) {
        return em.createNamedQuery("RefreshToken.findValid", RefreshToken.class)
                 .setParameter("token", token)
                 .setParameter("now", Instant.now())
                 .getResultStream().findFirst();
    }

    public void delete(String token) {
        em.createNamedQuery("RefreshToken.deleteByToken")
          .setParameter("token", token)
          .executeUpdate();
    }

    /** Global logout - used when a compromise is suspected. */
    public int revokeAllForUser(String username) {
        return em.createNamedQuery("RefreshToken.deleteAllForUser")
                 .setParameter("username", username)
                 .executeUpdate();
    }
}
