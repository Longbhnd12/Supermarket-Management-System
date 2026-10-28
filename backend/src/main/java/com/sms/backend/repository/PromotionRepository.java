package com.sms.backend.repository;

import com.sms.backend.entity.Category;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.Promotion;
import com.sms.backend.enums.GeneralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    List<Promotion> findByStatus(GeneralStatus status);

    List<Promotion> findByProduct(Product product);

    List<Promotion> findByCategory(Category category);

    List<Promotion> findByProductAndStatus(Product product, GeneralStatus status);

    List<Promotion> findByCategoryAndStatus(Category category, GeneralStatus status);
}
