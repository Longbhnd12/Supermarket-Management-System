package com.sms.backend.repository;

import com.sms.backend.entity.Sale;
import com.sms.backend.entity.User;
import com.sms.backend.enums.SaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.saleId = :id")
    Optional<Sale> findByIdForUpdate(@Param("id") Long id);

    Optional<Sale> findBySaleNumber(String saleNumber);

    List<Sale> findByCashier(User cashier);

    @Query("select s.saleId from Sale s where s.completionRequested = true and s.status = com.sms.backend.enums.SaleStatus.DRAFT order by s.saleId")
    List<Long> findCompletionRequests(org.springframework.data.domain.Pageable page);

    List<Sale> findByStatus(SaleStatus status);
}
