package com.sms.backend.repository;

import com.sms.backend.entity.Product;
import com.sms.backend.entity.StockCheck;
import com.sms.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockCheckRepository extends JpaRepository<StockCheck, Long> {

    List<StockCheck> findByProduct(Product product);

    List<StockCheck> findByCheckedBy(User checkedBy);

    List<StockCheck> findByStatus(String status);

    // UC32: Find all item check records for an audit session
    @Query("""
        SELECT sc
        FROM StockCheck sc
        JOIN FETCH sc.product p
        LEFT JOIN FETCH p.category c
        JOIN FETCH sc.checkedBy u
        WHERE sc.checkNumber = :checkNumber
        ORDER BY sc.stockCheckId ASC
        """)
    List<StockCheck> findByCheckNumber(@Param("checkNumber") String checkNumber);

    // UC32: Find all item check records ordered by time for session history
    @Query("""
        SELECT sc
        FROM StockCheck sc
        JOIN FETCH sc.product p
        LEFT JOIN FETCH p.category c
        JOIN FETCH sc.checkedBy u
        ORDER BY sc.checkedAt DESC, sc.stockCheckId DESC
        """)
    List<StockCheck> findAllWithDetails();

    boolean existsByCheckNumber(String checkNumber);

    Optional<StockCheck> findTopByOrderByStockCheckIdDesc();
}
