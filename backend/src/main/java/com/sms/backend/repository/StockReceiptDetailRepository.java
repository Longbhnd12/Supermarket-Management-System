package com.sms.backend.repository;

import com.sms.backend.entity.Product;
import com.sms.backend.entity.StockReceipt;
import com.sms.backend.entity.StockReceiptDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockReceiptDetailRepository extends JpaRepository<StockReceiptDetail, Long> {

    /** UC31 NEAR_EXPIRY: fetch all batch rows for a given product. */
    List<StockReceiptDetail> findByProduct(Product product);

    /**
     * UC37 Details: fetch all detail lines for a receipt, eagerly loading
     * the product to avoid N+1 queries during DTO mapping.
     */
    @Query("""
            SELECT d
            FROM StockReceiptDetail d
            JOIN FETCH d.product
            WHERE d.stockReceipt = :receipt
            ORDER BY d.stockReceiptItemId
            """)
    List<StockReceiptDetail> findByStockReceipt(@Param("receipt") StockReceipt receipt);
}
