package com.sms.backend.entity;

import com.sms.backend.enums.PaymentMethod;
import com.sms.backend.enums.PaymentStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment", schema = "dbo")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "received_amount", precision = 15, scale = 2)
    private BigDecimal receivedAmount;

    @Column(name = "change_amount", precision = 15, scale = 2)
    private BigDecimal changeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "gateway_transaction_id", length = 150)
    private String gatewayTransactionId;

    @Nationalized
    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "txn_ref", length = 100)
    private String txnRef;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "bank_code", length = 20)
    private String bankCode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "reconciliation_required", nullable = false)
    private boolean reconciliationRequired;

    @Nationalized
    @Column(name = "payment_url", columnDefinition = "nvarchar(max)")
    private String paymentUrl;
}
