package com.sms.backend.repository;

import com.sms.backend.entity.Product;
import com.sms.backend.entity.StockCheck;
import com.sms.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockCheckRepository extends JpaRepository<StockCheck, Long> {

    List<StockCheck> findByProduct(Product product);

    List<StockCheck> findByCheckedBy(User checkedBy);

    List<StockCheck> findByStatus(String status);
}
