package com.sms.backend.repository;

import com.sms.backend.entity.Category;
import com.sms.backend.enums.GeneralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByStatus(GeneralStatus status);

    boolean existsByCategoryName(String categoryName);
}
