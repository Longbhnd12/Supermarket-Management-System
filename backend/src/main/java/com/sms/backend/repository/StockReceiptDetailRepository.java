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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from StockReceiptDetail b where b.product.productId = :id order by b.stockReceiptItemId")
    List<StockReceiptDetail> lockBatches(@org.springframework.data.repository.query.Param("id") Long id);

    List<StockReceiptDetail> findByStockReceipt(StockReceipt stockReceipt);

    List<StockReceiptDetail> findByProduct(Product product);

    @Query("select d from StockReceiptDetail d where d.stockReceipt.status = com.sms.backend.enums.StockReceiptStatus.CONFIRMED and d.remainingQuantity > 0 and d.expirationDate is not null and d.product.trackExpiry = true")
    List<StockReceiptDetail> findActiveExpiringBatches();

    @Query("select d from StockReceiptDetail d where d.expirationDate between :from and :to and d.remainingQuantity > :minQty")
    List<StockReceiptDetail> findByExpirationDateBetweenAndRemainingQuantityGreaterThan(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("minQty") BigDecimal minQty);
}
