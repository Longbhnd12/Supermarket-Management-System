package com.sms.backend.repository;

import com.sms.backend.entity.Payment;
import com.sms.backend.entity.Sale;
import com.sms.backend.enums.PaymentStatus;
import com.sms.backend.enums.SaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findBySale(Sale sale);
    Optional<Payment> findBySaleAndPaymentStatus(Sale sale, PaymentStatus paymentStatus);

    List<Payment> findBySaleOrderByPaymentIdDesc(Sale sale);

    Optional<Payment> findBySaleAndIdempotencyKey(Sale sale, String idempotencyKey);

    Optional<Payment> findByTxnRef(String txnRef);

    @Query("select p from Payment p where p.sale.status = :saleStatus "
            + "and p.paymentStatus = :paymentStatus and p.reconciliationRequired = false "
            + "and p.sale.completedAt between :from and :to")
    List<Payment> findBySaleStatusAndPaymentStatusAndSaleCompletedAtBetween(
            @Param("saleStatus") SaleStatus saleStatus,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("select p.sale.saleId from Payment p where p.txnRef = :ref")
    Optional<Long> findSaleIdByTxnRef(@Param("ref") String ref);

    @Query("select p.sale.saleId from Payment p where p.paymentId = :id")
    Optional<Long> findSaleIdByPaymentId(@Param("id") Long id);

    @Query("select count(p) > 0 from Payment p where p.sale = :sale and "
            + "(p.paymentStatus in (com.sms.backend.enums.PaymentStatus.PENDING, com.sms.backend.enums.PaymentStatus.SUCCESS) "
            + "or p.reconciliationRequired = true)")
    boolean hasBlockingPayment(@Param("sale") Sale sale);

    @Query("select p.paymentId from Payment p where p.paymentMethod <> com.sms.backend.enums.PaymentMethod.CASH "
            + "and p.expiresAt <= :now and (p.paymentStatus = com.sms.backend.enums.PaymentStatus.PENDING or p.reconciliationRequired = true)")
    List<Long> findExpiredIds(@Param("now") LocalDateTime now);
}
