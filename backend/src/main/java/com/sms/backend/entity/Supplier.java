package com.sms.backend.entity;

import com.sms.backend.enums.SupplierStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "supplier", schema = "dbo")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "supplier_code", nullable = false, length = 50)
    private String supplierCode;

    @Nationalized
    @Column(name = "supplier_name", nullable = false, length = 200)
    private String supplierName;

    @Nationalized
    @Column(name = "contact_person", length = 100)
    private String contactPerson;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Nationalized
    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "tax_code", length = 20)
    private String taxCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupplierStatus status = SupplierStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
