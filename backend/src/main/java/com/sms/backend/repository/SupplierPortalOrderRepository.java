package com.sms.backend.repository;

import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.entity.PurchaseOrderDetail;
import com.sms.backend.enums.PurchaseOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * UC48 / UC49 - queries of the Supplier portal.
 * Kept separate from PurchaseOrderRepository (staff side) so the two features do not touch each other.
 * Every query is limited to ONE supplier (BR-01: a supplier only sees its own orders).
 */
@Repository
public interface SupplierPortalOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @Query(value = """
            SELECT po FROM PurchaseOrder po
            WHERE po.supplier.supplierId = :supplierId
              AND po.status IN :statuses
              AND (:keyword IS NULL OR LOWER(po.poNumber) LIKE :keyword)
              AND (:fromDate IS NULL OR po.createdAt >= :fromDate)
              AND (:toDate IS NULL OR po.createdAt < :toDate)
            """,
            countQuery = """
            SELECT COUNT(po) FROM PurchaseOrder po
            WHERE po.supplier.supplierId = :supplierId
              AND po.status IN :statuses
              AND (:keyword IS NULL OR LOWER(po.poNumber) LIKE :keyword)
              AND (:fromDate IS NULL OR po.createdAt >= :fromDate)
              AND (:toDate IS NULL OR po.createdAt < :toDate)
            """)
    Page<PurchaseOrder> searchForSupplier(@Param("supplierId") Long supplierId,
                                          @Param("statuses") Collection<PurchaseOrderStatus> statuses,
                                          @Param("keyword") String keyword,
                                          @Param("fromDate") LocalDateTime fromDate,
                                          @Param("toDate") LocalDateTime toDate,
                                          Pageable pageable);

    @Query("""
            SELECT po.status, COUNT(po) FROM PurchaseOrder po
            WHERE po.supplier.supplierId = :supplierId
            GROUP BY po.status
            """)
    List<Object[]> countByStatusForSupplier(@Param("supplierId") Long supplierId);

    /** orderId, number of lines, number of lines with a proposal */
    @Query("""
            SELECT d.purchaseOrder.orderId, COUNT(d),
                   SUM(CASE WHEN d.proposedQuantity IS NOT NULL OR d.proposedUnitPrice IS NOT NULL THEN 1 ELSE 0 END)
            FROM PurchaseOrderDetail d
            WHERE d.purchaseOrder.orderId IN :orderIds
            GROUP BY d.purchaseOrder.orderId
            """)
    List<Object[]> lineStatsForOrders(@Param("orderIds") Collection<Long> orderIds);

    @Query("""
            SELECT d FROM PurchaseOrderDetail d
            JOIN FETCH d.product
            WHERE d.purchaseOrder.orderId = :orderId
            ORDER BY d.orderDetailId
            """)
    List<PurchaseOrderDetail> findLines(@Param("orderId") Long orderId);

    /** Locks the order row while the supplier response is saved (SRS 4.2.2 Concurrency). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT po FROM PurchaseOrder po WHERE po.orderId = :orderId")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("orderId") Long orderId);
}
