package com.sms.backend.repository;

import com.sms.backend.entity.Sale;
import com.sms.backend.entity.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
    List<SaleItem> findBySale(Sale sale);
    List<SaleItem> findBySaleSaleIdIn(Collection<Long> saleIds);
}
