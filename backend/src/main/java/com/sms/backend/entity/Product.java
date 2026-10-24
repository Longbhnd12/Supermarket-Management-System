package com.sms.backend.entity;

import com.sms.backend.enums.GeneralStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product", schema = "dbo")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Nationalized
    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Nationalized
    @Column(name = "unit", nullable = false, length = 30)
    private String unit;

    @Column(name = "selling_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "stock_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal stockQuantity = new BigDecimal("0");

    @Column(name = "minimum_stock_level", nullable = false, precision = 12, scale = 3)
    private BigDecimal minimumStockLevel = new BigDecimal("0");

    @Column(name = "track_expiry", nullable = false)
    private Boolean trackExpiry = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GeneralStatus status = GeneralStatus.ACTIVE;
}
