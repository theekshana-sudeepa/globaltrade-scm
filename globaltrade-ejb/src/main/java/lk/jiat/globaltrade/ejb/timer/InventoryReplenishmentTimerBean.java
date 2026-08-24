package lk.jiat.globaltrade.ejb.timer;

import jakarta.annotation.security.PermitAll;
import jakarta.ejb.*;
import lk.jiat.globaltrade.common.interceptorbinding.Audited;
import lk.jiat.globaltrade.common.interceptorbinding.Monitored;
import lk.jiat.globaltrade.ejb.service.InventoryService;
import lk.jiat.globaltrade.ejb.service.VendorService;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Nightly inventory replenishment and weekly vendor performance evaluation.
 *
 * TIMEZONE NOTE: timezone = "UTC" is explicit and deliberate. A logistics
 * platform spanning 50+ countries must not have its nightly batch drift by an
 * hour twice a year because a server happened to sit in a DST-observing zone.
 */
@Singleton
@Startup
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@Audited
@Monitored
@PermitAll
public class InventoryReplenishmentTimerBean {

    private static final Logger LOG =
            Logger.getLogger(InventoryReplenishmentTimerBean.class.getName());

    @EJB private InventoryService inventoryService;
    @EJB private VendorService    vendorService;

    /** Daily at 02:15 UTC - low-traffic window across all served regions. */
    @Schedule(second = "0", minute = "15", hour = "2", dayOfWeek = "*",
              persistent = true, info = "INVENTORY_REPLENISHMENT", timezone = "UTC")
    public void replenishInventory() {
        try {
            int replenished = inventoryService.replenishAllBelowReorderPoint();
            LOG.info("Inventory replenishment complete: " + replenished + " SKUs raised");
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Inventory replenishment failed", e);
        }
    }

    /** Weekly on Monday 03:00 UTC - vendor scorecards for the previous 7 days. */
    @Schedule(second = "0", minute = "0", hour = "3", dayOfWeek = "Mon",
              persistent = true, info = "VENDOR_PERFORMANCE_EVAL", timezone = "UTC")
    public void evaluateVendorPerformance() {
        try {
            int evaluated = vendorService.evaluateAllDue(7);
            LOG.info("Vendor performance evaluation complete: " + evaluated + " vendors scored");
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Vendor performance evaluation failed", e);
        }
    }

    /** Month-end reconciliation. dayOfMonth = "Last" is a calendar-expression feature. */
    @Schedule(second = "0", minute = "45", hour = "23", dayOfMonth = "Last",
              persistent = true, info = "MONTH_END_RECONCILIATION", timezone = "UTC")
    public void monthEndReconciliation() {
        LOG.info("Month-end supply chain reconciliation started");
    }
}
