package com.sms.backend.repository;

import com.sms.backend.entity.Product;
import com.sms.backend.entity.StockMovement;
import com.sms.backend.enums.StockMovementType;
import com.sms.backend.enums.StockReferenceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByProduct(Product product);

    List<StockMovement> findByMovementType(StockMovementType movementType);

    List<StockMovement> findByReferenceTypeAndReferenceId(StockReferenceType referenceType, Long referenceId);

    boolean existsByReferenceTypeAndReferenceId(StockReferenceType referenceType, Long referenceId);

    /**
     * UC34 — View Stock History: paginated and filtered stock movements query.
     * Ordered by createdAt descending (BR-06).
     */
    @Query(value = """
            SELECT sm
            FROM StockMovement sm
            JOIN FETCH sm.product p
            JOIN FETCH sm.createdBy u
            LEFT JOIN FETCH sm.stockReceiptDetail srd
            LEFT JOIN FETCH srd.stockReceipt sr
            WHERE (:productId IS NULL OR p.productId = :productId)
              AND (:keyword IS NULL
                   OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(sm.reason) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:movementType IS NULL OR sm.movementType = :movementType)
              AND (:dateFrom IS NULL OR sm.createdAt >= :dateFrom)
              AND (:dateTo IS NULL OR sm.createdAt <= :dateTo)
            ORDER BY sm.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(sm)
            FROM StockMovement sm
            JOIN sm.product p
            WHERE (:productId IS NULL OR p.productId = :productId)
              AND (:keyword IS NULL
                   OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(sm.reason) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:movementType IS NULL OR sm.movementType = :movementType)
              AND (:dateFrom IS NULL OR sm.createdAt >= :dateFrom)
              AND (:dateTo IS NULL OR sm.createdAt <= :dateTo)
            """)
    Page<StockMovement> findAllByFilter(
            @Param("productId") Long productId,
            @Param("keyword") String keyword,
            @Param("movementType") StockMovementType movementType,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable
    );
}
