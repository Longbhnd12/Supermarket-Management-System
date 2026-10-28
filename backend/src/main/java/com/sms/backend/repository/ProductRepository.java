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

    Optional<Product> findByProductCode(String productCode);

    Optional<Product> findByBarcode(String barcode);

    boolean existsByProductCode(String productCode);

    boolean existsByBarcode(String barcode);

    List<Product> findByCategory(Category category);

    List<Product> findByStatus(GeneralStatus status);
}
