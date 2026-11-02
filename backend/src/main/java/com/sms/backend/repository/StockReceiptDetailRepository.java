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
}
