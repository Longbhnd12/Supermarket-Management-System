package com.sms.backend.repository;

import com.sms.backend.entity.Product;
import com.sms.backend.entity.StockReceipt;
import com.sms.backend.entity.StockReceiptDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface StockReceiptDetailRepository extends JpaRepository<StockReceiptDetail, Long> {
    List<StockReceiptDetail> findByStockReceipt(StockReceipt stockReceipt);
    List<StockReceiptDetail> findByProduct(Product product);
    List<StockReceiptDetail> findByExpirationDateBetweenAndRemainingQuantityGreaterThan(
            LocalDate fromDate, LocalDate toDate, BigDecimal remainingQuantity
    );
}
