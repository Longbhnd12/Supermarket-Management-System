package com.sms.backend.entity;

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
@Table(name = "stock_check", schema = "dbo")
public class StockCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_check_id")
    private Long stockCheckId;

    @Column(name = "check_number", nullable = false, length = 50)
    private String checkNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_by", nullable = false)
    private User checkedBy;

    @Column(name = "system_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal systemQuantity;

    @Column(name = "actual_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal actualQuantity;

    @Column(name = "difference", nullable = false, precision = 12, scale = 3)
    private BigDecimal difference;

    @Nationalized
    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;
}
