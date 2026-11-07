package com.sms.backend.repository;

import com.sms.backend.entity.Category;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.Promotion;
import com.sms.backend.enums.GeneralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    @Query("select p from Promotion p left join fetch p.product left join fetch p.category "
            + "where p.status = :status and p.startAt <= :now and p.endAt >= :now order by p.promotionId")
    List<Promotion> findEffective(@Param("status") GeneralStatus status, @Param("now") LocalDateTime now);

    @Query("select p from Promotion p left join fetch p.product left join fetch p.category "
            + "where p.status = com.sms.backend.enums.GeneralStatus.ACTIVE and p.startAt <= :now and p.endAt >= :now order by p.promotionId")
    List<Promotion> findActivePromotions(@Param("now") LocalDateTime now);

    List<Promotion> findByStatus(GeneralStatus status);

    List<Promotion> findByProduct(Product product);

    List<Promotion> findByCategory(Category category);

    List<Promotion> findByProductAndStatus(Product product, GeneralStatus status);

    List<Promotion> findByCategoryAndStatus(Category category, GeneralStatus status);
}
