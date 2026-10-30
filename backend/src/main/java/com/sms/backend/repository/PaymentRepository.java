package com.sms.backend.repository;

import com.sms.backend.entity.Payment;
import com.sms.backend.enums.PaymentStatus;
import com.sms.backend.enums.SaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findBySaleStatusAndPaymentStatusAndSaleCompletedAtBetween(
            SaleStatus saleStatus, PaymentStatus paymentStatus, LocalDateTime from, LocalDateTime to
    );
}
