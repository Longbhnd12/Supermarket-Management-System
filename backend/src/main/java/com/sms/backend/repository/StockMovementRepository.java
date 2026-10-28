package com.sms.backend.repository;

import com.sms.backend.entity.Product;
import com.sms.backend.entity.StockMovement;
import com.sms.backend.enums.StockMovementType;
import com.sms.backend.enums.StockReferenceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByProduct(Product product);

    List<StockMovement> findByMovementType(StockMovementType movementType);

    List<StockMovement> findByReferenceTypeAndReferenceId(StockReferenceType referenceType, Long referenceId);
}
