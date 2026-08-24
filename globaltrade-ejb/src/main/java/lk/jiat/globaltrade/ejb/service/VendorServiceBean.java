package lk.jiat.globaltrade.ejb.service;

import jakarta.annotation.Resource;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lk.jiat.globaltrade.common.constant.SecurityRoles;
import lk.jiat.globaltrade.common.exception.UnauthorizedSupplyChainAccessException;
import lk.jiat.globaltrade.common.exception.VendorValidationException;
import lk.jiat.globaltrade.common.interceptorbinding.Audited;
import lk.jiat.globaltrade.common.interceptorbinding.Monitored;
import lk.jiat.globaltrade.domain.Shipment;
import lk.jiat.globaltrade.domain.Vendor;
import lk.jiat.globaltrade.domain.VendorPerformance;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Stateless
@Audited
@Monitored
public class VendorServiceBean implements VendorService {

    @PersistenceContext(unitName = "GlobalTradePU")
    private EntityManager em;

    @Resource
    private SessionContext ctx;

    @Override
    @Audited("VENDOR_REGISTER")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.LOGISTICS_COORDINATOR})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Vendor register(Vendor vendor) throws VendorValidationException {
        em.persist(vendor);     // VendorDataValidationInterceptor already normalised it
        return vendor;
    }

    /**
     * Combined declarative + programmatic authorization.
     * Declarative gets the caller through the door; programmatic decides whether
     * THIS caller may touch THIS vendor row.
     */
    @Override
    @Audited("VENDOR_UPDATE")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.LOGISTICS_COORDINATOR,
                   SecurityRoles.VENDOR_REP})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Vendor update(Vendor vendor) throws VendorValidationException {
        if (ctx.isCallerInRole(SecurityRoles.VENDOR_REP)
                && !ctx.isCallerInRole(SecurityRoles.ADMIN)) {
            Principal caller = ctx.getCallerPrincipal();
            Vendor existing = em.find(Vendor.class, vendor.getId());
            boolean ownsRecord = existing != null
                    && existing.getContactEmail() != null
                    && existing.getContactEmail().equalsIgnoreCase(caller.getName());
            if (!ownsRecord) {
                throw new UnauthorizedSupplyChainAccessException(
                        caller.getName(), "vendor:" + vendor.getId());
            }
            // A vendor rep may never flip their own active flag or score.
            vendor.setActive(existing.isActive());
            vendor.setPerformanceScore(existing.getPerformanceScore());
        }
        return em.merge(vendor);
    }

    @Override
    @PermitAll
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<Vendor> listActive() {
        return em.createNamedQuery("Vendor.findActive", Vendor.class).getResultList();
    }

    /** Driven by the weekly vendor-performance timer. */
    @Override
    @Audited("VENDOR_EVALUATE_BATCH")
    @PermitAll
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int evaluateAllDue(int lookbackDays) {
        Instant cutoff = Instant.now().minus(lookbackDays, ChronoUnit.DAYS);
        List<Vendor> due = em.createNamedQuery("Vendor.findDueForReview", Vendor.class)
                             .setParameter("cutoff", cutoff)
                             .getResultList();

        for (Vendor v : due) {
            List<Shipment> shipments = em.createQuery(
                    "SELECT s FROM Shipment s WHERE s.vendor.id = :vid AND s.createdAt >= :from",
                    Shipment.class)
                .setParameter("vid", v.getId())
                .setParameter("from", cutoff)
                .getResultList();

            int total = shipments.size();
            int onTime = (int) shipments.stream()
                .filter(s -> s.getActualArrival() != null && s.getEstimatedArrival() != null
                          && !s.getActualArrival().isAfter(s.getEstimatedArrival()))
                .count();

            double rate = total == 0 ? 0d : (onTime * 100.0d) / total;

            VendorPerformance p = new VendorPerformance();
            p.setVendor(v);
            p.setPeriodStart(cutoff);
            p.setPeriodEnd(Instant.now());
            p.setShipmentsTotal(total);
            p.setShipmentsOnTime(onTime);
            p.setOnTimeRate(rate);
            em.persist(p);

            v.setPerformanceScore(rate);
            v.setLastEvaluatedAt(Instant.now());
        }
        return due.size();
    }

    /** Nobody may call this externally; retained for internal data-migration use only. */
    @DenyAll
    public void purgeAllVendorData() {
        em.createQuery("DELETE FROM VendorPerformance p").executeUpdate();
        em.createQuery("DELETE FROM Vendor v").executeUpdate();
    }
}
