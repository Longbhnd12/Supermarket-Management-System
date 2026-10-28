package com.sms.backend.repository;

import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.entity.Supplier;
import com.sms.backend.entity.User;
import com.sms.backend.enums.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    List<PurchaseOrder> findBySupplier(Supplier supplier);

    List<PurchaseOrder> findByRequestedBy(User requestedBy);

    List<PurchaseOrder> findByStatus(PurchaseOrderStatus status);
}
