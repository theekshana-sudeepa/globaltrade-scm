package lk.jiat.globaltrade.ejb.service;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import lk.jiat.globaltrade.common.constant.SecurityRoles;
import lk.jiat.globaltrade.common.exception.InsufficientInventoryException;
import lk.jiat.globaltrade.common.interceptorbinding.Audited;
import lk.jiat.globaltrade.common.interceptorbinding.Monitored;
import lk.jiat.globaltrade.domain.InventoryItem;

import java.time.Instant;
import java.util.List;

@Stateless
@Audited
@Monitored
@RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.WAREHOUSE_MANAGER,
               SecurityRoles.LOGISTICS_COORDINATOR})
public class InventoryServiceBean implements InventoryService {

    @PersistenceContext(unitName = "GlobalTradePU")
    private EntityManager em;

    /**
     * MANDATORY: reserving stock is never a standalone operation. It is always
     * one half of "reserve stock AND dispatch shipment". Declaring MANDATORY
     * makes the container enforce that contract at runtime - a caller that
     * forgets to open a transaction gets EJBTransactionRequiredException rather
     * than silently committing an orphaned reservation.
     *
     * PESSIMISTIC_WRITE: stock rows for fast-moving SKUs are contended hot rows.
     * Optimistic @Version would produce a storm of OptimisticLockException retries
     * during peak season; a short row lock here is measurably cheaper.
     */
    @Override
    @Audited("INVENTORY_RESERVE")
    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    public void reserve(String sku, String warehouse, int quantity)
            throws InsufficientInventoryException {

        InventoryItem item = loadForUpdate(sku, warehouse);
        if (item == null || item.available() < quantity) {
            int available = item == null ? 0 : item.available();
            // rollback = true -> the whole allocate-and-dispatch unit is undone
            throw new InsufficientInventoryException(sku, quantity, available);
        }
        item.setQuantityReserved(item.getQuantityReserved() + quantity);
    }

    @Override
    @Audited("INVENTORY_RELEASE")
    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    public void release(String sku, String warehouse, int quantity) {
        InventoryItem item = loadForUpdate(sku, warehouse);
        if (item != null) {
            item.setQuantityReserved(Math.max(0, item.getQuantityReserved() - quantity));
        }
    }

    @Override
    @PermitAll
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<InventoryItem> findBelowReorderPoint() {
        return em.createNamedQuery("InventoryItem.findBelowReorderPoint", InventoryItem.class)
                 .getResultList();
    }

    /**
     * REQUIRES_NEW: invoked by the replenishment timer. Committing on its own
     * means a replenishment run is durable even if the calling timer transaction
     * later fails for an unrelated reason.
     */
    @Override
    @Audited("INVENTORY_REPLENISH")
    @PermitAll
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public int replenishAllBelowReorderPoint() {
        List<InventoryItem> low = findBelowReorderPoint();
        Instant now = Instant.now();
        for (InventoryItem i : low) {
            i.setQuantityOnHand(i.getQuantityOnHand() + i.getReorderQuantity());
            i.setLastReplenishedAt(now);
            em.merge(i);
        }
        return low.size();
    }

    private InventoryItem loadForUpdate(String sku, String warehouse) {
        try {
            return em.createNamedQuery("InventoryItem.findBySkuAndWarehouse", InventoryItem.class)
                     .setParameter("sku", sku)
                     .setParameter("wh", warehouse)
                     .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                     .setHint("jakarta.persistence.lock.timeout", 3000)
                     .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}
