package com.sms.backend.repository;

import com.sms.backend.entity.Product;
import com.sms.backend.entity.StockReceipt;
import com.sms.backend.entity.StockReceiptDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface StockReceiptDetailRepository
        extends JpaRepository<StockReceiptDetail, Long> {

    // UC31: Find batch details by product
    List<StockReceiptDetail> findByProduct(Product product);

    // UC37: Find receipt details including product information
    @Query("""
        SELECT d
        FROM StockReceiptDetail d
        JOIN FETCH d.product
        WHERE d.stockReceipt = :receipt
        ORDER BY d.stockReceiptItemId
        """)
    List<StockReceiptDetail> findByStockReceipt(
            @Param("receipt") StockReceipt receipt
    );

    // Find batch details by expiration date and remaining quantity
    List<StockReceiptDetail>
    findByExpirationDateBetweenAndRemainingQuantityGreaterThan(
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal remainingQuantity
    );

    // UC35: Find active receipt batch details with expiry tracking for confirmed receipts only
    @Query("""
        SELECT d
        FROM StockReceiptDetail d
        JOIN FETCH d.product p
        LEFT JOIN FETCH p.category c
        JOIN FETCH d.stockReceipt r
        WHERE r.status = com.sms.backend.enums.StockReceiptStatus.CONFIRMED
          AND d.remainingQuantity > 0
          AND d.expirationDate IS NOT NULL
          AND p.trackExpiry = true
        ORDER BY d.expirationDate ASC
        """)
    List<StockReceiptDetail> findActiveExpiringBatches();
}
