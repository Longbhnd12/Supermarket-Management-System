package com.sms.backend.repository;

import com.sms.backend.entity.StockReceipt;
import com.sms.backend.entity.StockReceiptDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockReceiptDetailRepository extends JpaRepository<StockReceiptDetail, Long> {

    List<StockReceiptDetail> findByStockReceipt(StockReceipt stockReceipt);
}
