package com.sms.backend.repository;

import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.entity.PurchaseOrderDetail;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderDetailRepository extends JpaRepository<PurchaseOrderDetail, Long> {

    List<PurchaseOrderDetail> findByPurchaseOrder(PurchaseOrder purchaseOrder);

    /**
     * Retrieves PurchaseOrderDetail lines for a PO with a pessimistic write lock
     * to prevent concurrent over-fulfillment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM PurchaseOrderDetail d WHERE d.purchaseOrder = :purchaseOrder")
    List<PurchaseOrderDetail> findByPurchaseOrderWithLock(@Param("purchaseOrder") PurchaseOrder purchaseOrder);
}
