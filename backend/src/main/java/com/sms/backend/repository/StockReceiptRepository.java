package com.sms.backend.repository;

import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.entity.StockReceipt;
import com.sms.backend.entity.Supplier;
import com.sms.backend.enums.StockReceiptStatus;
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
public interface StockReceiptRepository extends JpaRepository<StockReceipt, Long> {

    List<StockReceipt> findBySupplier(Supplier supplier);

    List<StockReceipt> findByPurchaseOrder(PurchaseOrder purchaseOrder);

    List<StockReceipt> findByStatus(StockReceiptStatus status);

    // =========================================================================
    // UC37 Details — fetch single receipt with supplier, createdBy, and optional PO
    // =========================================================================

    /**
     * Loads a StockReceipt by ID with FETCH joins on supplier, createdBy,
     * and the optional purchaseOrder so no lazy-loading exceptions occur.
     */
    @Query("""
            SELECT sr
            FROM StockReceipt sr
            JOIN FETCH sr.supplier
            JOIN FETCH sr.createdBy
            LEFT JOIN FETCH sr.purchaseOrder
            WHERE sr.stockReceiptId = :id
            """)
    Optional<StockReceipt> findByIdWithDetails(@Param("id") Long id);

    // =========================================================================
    // UC37 — Stock Receipt List (filtered + paginated)
    // =========================================================================

    /**
     * Returns a page of StockReceipt rows matching the given filters.
     *
     * Filters:
     *   - keyword: partial match on receiptNumber OR supplierName (case-insensitive)
     *   - productSku: existence check — receipt has at least one detail whose product.productCode matches
     *   - supplierId: exact supplier ID
     *   - status: exact status enum value
     *   - dateFrom / dateTo: inclusive range filter on COALESCE(receivedAt, createdAt)
     *
     * Passing null for any parameter disables that filter.
     */
    @Query(value = """
            SELECT sr
            FROM StockReceipt sr
            JOIN FETCH sr.supplier sup
            JOIN FETCH sr.createdBy cb
            LEFT JOIN FETCH sr.purchaseOrder po
            WHERE (:keyword IS NULL
                   OR LOWER(sr.receiptNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(sup.supplierName) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:productSku IS NULL
                   OR EXISTS (
                       SELECT 1 FROM StockReceiptDetail d
                       WHERE d.stockReceipt = sr
                         AND LOWER(d.product.productCode) LIKE LOWER(CONCAT('%', :productSku, '%'))
                   ))
              AND (:supplierId IS NULL OR sup.supplierId = :supplierId)
              AND (:status IS NULL OR sr.status = :status)
              AND (:dateFrom IS NULL OR sr.createdAt >= :dateFrom)
              AND (:dateTo IS NULL OR sr.createdAt <= :dateTo)
            ORDER BY sr.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(sr)
            FROM StockReceipt sr
            JOIN sr.supplier sup
            WHERE (:keyword IS NULL
                   OR LOWER(sr.receiptNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(sup.supplierName) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:productSku IS NULL
                   OR EXISTS (
                       SELECT 1 FROM StockReceiptDetail d
                       WHERE d.stockReceipt = sr
                         AND LOWER(d.product.productCode) LIKE LOWER(CONCAT('%', :productSku, '%'))
                   ))
              AND (:supplierId IS NULL OR sup.supplierId = :supplierId)
              AND (:status IS NULL OR sr.status = :status)
              AND (:dateFrom IS NULL OR sr.createdAt >= :dateFrom)
              AND (:dateTo IS NULL OR sr.createdAt <= :dateTo)
            """)
    Page<StockReceipt> findAllByFilter(
            @Param("keyword") String keyword,
            @Param("productSku") String productSku,
            @Param("supplierId") Long supplierId,
            @Param("status") StockReceiptStatus status,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable
    );
}

