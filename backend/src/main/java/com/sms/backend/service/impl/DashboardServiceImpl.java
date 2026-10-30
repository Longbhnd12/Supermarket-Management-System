package com.sms.backend.service.impl;

import com.sms.backend.dto.response.*;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.Sale;
import com.sms.backend.entity.SaleItem;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.SaleStatus;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.repository.SaleItemRepository;
import com.sms.backend.repository.SaleRepository;
import com.sms.backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;

    @Override public DashboardResponse getDashboard() {
        LocalDate today = LocalDate.now();
        List<Sale> todayCompleted = completedBetween(today, today);
        List<Sale> monthCompleted = completedBetween(YearMonth.from(today).atDay(1), today);
        List<Sale> trendSales = completedBetween(today.minusDays(6), today);
        List<Product> activeProducts = productRepository.findAll().stream().filter(product -> product.getStatus() == GeneralStatus.ACTIVE).toList();
        long healthy = activeProducts.stream().filter(product -> classify(product).equals("IN_STOCK")).count();
        double stockHealth = activeProducts.isEmpty() ? 0d : BigDecimal.valueOf(healthy * 100d / activeProducts.size()).setScale(1, RoundingMode.HALF_UP).doubleValue();
        List<Long> monthSaleIds = monthCompleted.stream().map(Sale::getSaleId).toList();
        List<SaleItem> items = monthSaleIds.isEmpty() ? List.of() : saleItemRepository.findBySaleSaleIdIn(monthSaleIds);
        List<DashboardTopProductResponse> topProducts = items.stream().collect(Collectors.groupingBy(item -> item.getProduct().getProductId())).values().stream()
                .map(this::topProduct).sorted(Comparator.comparing(DashboardTopProductResponse::netSales).reversed()).limit(5).toList();
        List<DashboardStockAlertResponse> alerts = activeProducts.stream().filter(product -> !classify(product).equals("IN_STOCK"))
                .map(product -> new DashboardStockAlertResponse(product.getProductId(), product.getProductName(), safe(product.getStockQuantity()), safe(product.getMinimumStockLevel()), classify(product)))
                .sorted(Comparator.comparing(DashboardStockAlertResponse::currentStock)).limit(8).toList();
        List<DashboardSalesPointResponse> trend = trendSales.stream().collect(Collectors.groupingBy(sale -> sale.getCompletedAt().toLocalDate())).entrySet().stream()
                .map(entry -> new DashboardSalesPointResponse(entry.getKey(), sumSales(entry.getValue()))).sorted(Comparator.comparing(DashboardSalesPointResponse::date)).toList();
        return new DashboardResponse(sumSales(todayCompleted), sumSales(monthCompleted), activeProducts.size(), stockHealth, trend, topProducts, alerts);
    }
    private List<Sale> completedBetween(LocalDate from, LocalDate to) { return saleRepository.findByStatusAndCompletedAtBetween(SaleStatus.COMPLETED, from.atStartOfDay(), to.atTime(LocalTime.MAX)); }
    private DashboardTopProductResponse topProduct(List<SaleItem> rows) { SaleItem first=rows.getFirst(); BigDecimal quantity=rows.stream().map(SaleItem::getQuantity).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add); BigDecimal net=rows.stream().map(item->safe(item.getQuantity()).multiply(safe(item.getUnitPrice())).subtract(safe(item.getDiscountAmount()))).reduce(BigDecimal.ZERO,BigDecimal::add); return new DashboardTopProductResponse(first.getProduct().getProductId(),first.getProduct().getProductName(),quantity,net); }
    private BigDecimal sumSales(List<Sale> sales) { return sales.stream().map(Sale::getTotalAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add); }
    private BigDecimal safe(BigDecimal value) { return value==null?BigDecimal.ZERO:value; }
    private String classify(Product product) { return safe(product.getStockQuantity()).signum()<=0?"OUT_OF_STOCK":safe(product.getStockQuantity()).compareTo(safe(product.getMinimumStockLevel()))<=0?"LOW_STOCK":"IN_STOCK"; }
}
