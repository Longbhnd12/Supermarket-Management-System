package com.sms.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "purchase_order_detail", schema = "dbo")
public class PurchaseOrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_detail_id")
    private Long orderDetailId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "requested_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal requestedQuantity;

    @Column(name = "ordered_quantity", precision = 12, scale = 3)
    private BigDecimal orderedQuantity;

    @Column(name = "proposed_quantity", precision = 12, scale = 3)
    private BigDecimal proposedQuantity;

    @Column(name = "received_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal receivedQuantity = new BigDecimal("0");

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "proposed_unit_price", precision = 15, scale = 2)
    private BigDecimal proposedUnitPrice;
}
