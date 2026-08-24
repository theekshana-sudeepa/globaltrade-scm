package lk.jiat.globaltrade.ejb.timer;

import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lk.jiat.globaltrade.common.interceptorbinding.Audited;
import lk.jiat.globaltrade.common.interceptorbinding.Monitored;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.logging.Logger;

/**
 * Security and compliance housekeeping.
 *
 * Data retention (assignment: "assess logistics data retention strategies"):
 * customs regulations in most jurisdictions require trade records to be retained
 * for 5-7 years, so the audit purge below is deliberately conservative at 7 years
 * and is the ONLY place audit rows are ever deleted.
 */
@Singleton
@Startup
@Audited
@Monitored
@PermitAll
public class SecurityMaintenanceTimerBean {

    private static final Logger LOG =
            Logger.getLogger(SecurityMaintenanceTimerBean.class.getName());

    private static final long AUDIT_RETENTION_DAYS = 7 * 365L;

    @PersistenceContext(unitName = "GlobalTradePU")
    private EntityManager em;

    /** Hourly: purge expired refresh tokens so a stolen token cannot be replayed. */
    @Schedule(minute = "7", hour = "*", persistent = false,
              info = "REFRESH_TOKEN_CLEANUP", timezone = "UTC")
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void purgeExpiredRefreshTokens() {
        int deleted = em.createNamedQuery("RefreshToken.deleteExpired")
                        .setParameter("now", Instant.now())
                        .executeUpdate();
        if (deleted > 0) LOG.info("Refresh token cleanup: " + deleted + " tokens removed");
    }

    /** Yearly: purge audit records beyond the regulatory retention period. */
    @Schedule(second = "0", minute = "0", hour = "1", dayOfMonth = "1", month = "Jan",
              persistent = true, info = "AUDIT_RETENTION_PURGE", timezone = "UTC")
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void purgeExpiredAuditRecords() {
        Instant cutoff = Instant.now().minus(AUDIT_RETENTION_DAYS, ChronoUnit.DAYS);
        int deleted = em.createNamedQuery("AuditLog.purgeOlderThan")
                        .setParameter("cutoff", cutoff)
                        .executeUpdate();
        LOG.info("Audit retention purge: " + deleted + " records older than " + cutoff);
    }
}
