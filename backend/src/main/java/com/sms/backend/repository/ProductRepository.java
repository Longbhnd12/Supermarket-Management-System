package com.sms.backend.repository;

import com.sms.backend.entity.Category;
import com.sms.backend.entity.Product;
import com.sms.backend.enums.GeneralStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query(value = "SELECT p FROM Product p JOIN FETCH p.category c WHERE p.status = com.sms.backend.enums.GeneralStatus.ACTIVE AND (:categoryId IS NULL OR c.categoryId = :categoryId) AND (:keyword IS NULL OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', :keyword, '%')))", countQuery = "SELECT COUNT(p) FROM Product p WHERE p.status = com.sms.backend.enums.GeneralStatus.ACTIVE AND (:categoryId IS NULL OR p.category.categoryId = :categoryId) AND (:keyword IS NULL OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> findAllByFilter(@Param("keyword") String keyword, @Param("categoryId") Long categoryId, Pageable pageable);

    @Query("SELECT p FROM Product p JOIN FETCH p.category c WHERE p.status = com.sms.backend.enums.GeneralStatus.ACTIVE AND (:categoryId IS NULL OR c.categoryId = :categoryId) AND (:keyword IS NULL OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Product> findAllByFilterNoPaging(@Param("keyword") String keyword, @Param("categoryId") Long categoryId);
}
