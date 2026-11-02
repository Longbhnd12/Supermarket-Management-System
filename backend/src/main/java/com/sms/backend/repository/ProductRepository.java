package com.sms.backend.repository;

import com.sms.backend.entity.Category;
import com.sms.backend.entity.Product;
import com.sms.backend.enums.GeneralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Product p where p.productId = :id")
    Optional<Product> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Product> findByProductCode(String productCode);

    Optional<Product> findByBarcode(String barcode);

    List<Product> findTop50ByStatusAndProductNameContainingIgnoreCaseOrStatusAndProductCodeContainingIgnoreCaseOrderByProductIdAsc(
            GeneralStatus nameStatus, String name, GeneralStatus codeStatus, String code);

    boolean existsByProductCode(String productCode);

    boolean existsByBarcode(String barcode);

    List<Product> findByCategory(Category category);

    List<Product> findByStatus(GeneralStatus status);
}
