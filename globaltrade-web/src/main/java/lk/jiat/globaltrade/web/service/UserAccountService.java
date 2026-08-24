package lk.jiat.globaltrade.web.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lk.jiat.globaltrade.domain.AppUser;
import lk.jiat.globaltrade.web.security.PasswordHasher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Account lookup plus brute-force lockout.
 *
 * The lockout counter is itself a security-vs-availability trade-off worth
 * discussing: five failures locks an account for 15 minutes, which stops
 * credential stuffing but hands an attacker a denial-of-service against a named
 * customs agent. Mitigation: lockout is per account AND the failure counter
 * resets on success; production would additionally rate-limit by source IP at
 * the gateway so a distributed attack cannot lock every account.
 */
@ApplicationScoped
public class UserAccountService {

    private static final int  MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES     = 15;

    @PersistenceContext(unitName = "GlobalTradePU")
    private EntityManager em;

    public Optional<AppUser> findByEmail(String email) {
        try {
            return Optional.of(em.createNamedQuery("AppUser.findByEmail", AppUser.class)
                                 .setParameter("email", email)
                                 .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public boolean validate(String email, String password) {
        return findByEmail(email)
                .map(u -> PasswordHasher.verify(password, u.getPasswordHash()))
                .orElse(false);
    }

    public Set<String> rolesOf(String email) {
        return findByEmail(email).map(AppUser::getRoles).orElse(Collections.emptySet());
    }

    public boolean isLockedOut(String email) {
        return findByEmail(email)
                .map(u -> u.getLockedUntil() != null && u.getLockedUntil().isAfter(Instant.now()))
                .orElse(false);
    }

    @Transactional
    public void recordFailure(String email) {
        findByEmail(email).ifPresent(u -> {
            int attempts = u.getFailedAttempts() + 1;
            u.setFailedAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                u.setLockedUntil(Instant.now().plus(LOCKOUT_MINUTES, ChronoUnit.MINUTES));
                u.setFailedAttempts(0);
            }
            em.merge(u);
        });
    }

    @Transactional
    public void recordSuccess(String email) {
        findByEmail(email).ifPresent(u -> {
            u.setFailedAttempts(0);
            u.setLockedUntil(null);
            em.merge(u);
        });
    }
}
