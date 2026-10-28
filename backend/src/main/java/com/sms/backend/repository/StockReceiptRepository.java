package com.sms.backend.repository;

import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.entity.StockReceipt;
import com.sms.backend.entity.Supplier;
import com.sms.backend.enums.StockReceiptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockReceiptRepository extends JpaRepository<StockReceipt, Long> {

    List<StockReceipt> findBySupplier(Supplier supplier);

    List<StockReceipt> findByPurchaseOrder(PurchaseOrder purchaseOrder);

    List<StockReceipt> findByStatus(StockReceiptStatus status);
}
