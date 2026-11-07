package com.sms.backend.repository;

import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.entity.Supplier;
import com.sms.backend.entity.User;
import com.sms.backend.enums.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    boolean existsByPoNumber(String poNumber);

    Optional<PurchaseOrder> findTopByOrderByOrderIdDesc();

    List<PurchaseOrder> findBySupplier(Supplier supplier);

    List<PurchaseOrder> findByRequestedBy(User requestedBy);

    List<PurchaseOrder> findByStatus(PurchaseOrderStatus status);

    @Query(value = """
            SELECT po
            FROM PurchaseOrder po
            JOIN FETCH po.supplier s
            JOIN FETCH po.requestedBy u
            LEFT JOIN FETCH po.approvedBy ab
            WHERE (:keyword IS NULL
                   OR LOWER(po.poNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(s.supplierName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:supplierId IS NULL OR s.supplierId = :supplierId)
              AND (:status IS NULL OR po.status = :status)
              AND (:dateFrom IS NULL OR COALESCE(po.updatedAt, po.createdAt) >= :dateFrom)
              AND (:dateTo IS NULL OR COALESCE(po.updatedAt, po.createdAt) <= :dateTo)
            ORDER BY COALESCE(po.updatedAt, po.createdAt) DESC, po.orderId DESC
            """,
            countQuery = """
            SELECT COUNT(po)
            FROM PurchaseOrder po
            JOIN po.supplier s
            JOIN po.requestedBy u
            WHERE (:keyword IS NULL
                   OR LOWER(po.poNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(s.supplierName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:supplierId IS NULL OR s.supplierId = :supplierId)
              AND (:status IS NULL OR po.status = :status)
              AND (:dateFrom IS NULL OR COALESCE(po.updatedAt, po.createdAt) >= :dateFrom)
              AND (:dateTo IS NULL OR COALESCE(po.updatedAt, po.createdAt) <= :dateTo)
            """)
    Page<PurchaseOrder> findAllByFilter(
            @Param("keyword") String keyword,
            @Param("supplierId") Long supplierId,
            @Param("status") PurchaseOrderStatus status,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable
    );

    /**
     * Returns purchase orders for a supplier that are eligible for goods receiving (ACCEPTED or PARTIALLY_FULFILLED).
     * Fetches supplier, requestedBy, and approvedBy eagerly to avoid lazy-load issues.
     */
    @Query("""
            SELECT po
            FROM PurchaseOrder po
            JOIN FETCH po.supplier s
            JOIN FETCH po.requestedBy u
            LEFT JOIN FETCH po.approvedBy ab
            WHERE s.supplierId = :supplierId
              AND po.status IN (com.sms.backend.enums.PurchaseOrderStatus.ACCEPTED,
                                com.sms.backend.enums.PurchaseOrderStatus.PARTIALLY_FULFILLED)
            ORDER BY po.updatedAt DESC, po.orderId DESC
            """)
    List<PurchaseOrder> findEligibleBySupplier(@Param("supplierId") Long supplierId);

    @Query("""
            SELECT po.status, COUNT(po)
            FROM PurchaseOrder po
            JOIN po.supplier s
            JOIN po.requestedBy u
            WHERE (:keyword IS NULL
                   OR LOWER(po.poNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(s.supplierName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:supplierId IS NULL OR s.supplierId = :supplierId)
              AND (:dateFrom IS NULL OR COALESCE(po.updatedAt, po.createdAt) >= :dateFrom)
              AND (:dateTo IS NULL OR COALESCE(po.updatedAt, po.createdAt) <= :dateTo)
            GROUP BY po.status
            """)
    List<Object[]> countByStatusWithFilter(
            @Param("keyword") String keyword,
            @Param("supplierId") Long supplierId,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo
    );
}
