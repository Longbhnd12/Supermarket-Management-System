package com.sms.backend.repository;

import com.sms.backend.entity.Sale;
import com.sms.backend.entity.User;
import com.sms.backend.enums.SaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {
    Optional<Sale> findBySaleNumber(String saleNumber);
    List<Sale> findByCashier(User cashier);
    List<Sale> findByStatus(SaleStatus status);
    List<Sale> findByStatusAndCompletedAtBetween(SaleStatus status, LocalDateTime from, LocalDateTime to);
    List<Sale> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}
