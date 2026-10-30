package com.sms.backend.service.impl;

import com.sms.backend.dto.response.*;
import com.sms.backend.entity.*;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.PaymentMethod;
import com.sms.backend.enums.PaymentStatus;
import com.sms.backend.enums.SaleStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.repository.*;
import com.sms.backend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {
    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final PaymentRepository paymentRepository;
    private final StockReceiptDetailRepository stockReceiptDetailRepository;

    @Override
    public InventoryReportResponse getInventoryReport(Long categoryId, String stockStatus) {
        String requestedStatus = normalizeStockStatus(stockStatus);
        Set<Long> nearExpiryIds = stockReceiptDetailRepository
                .findByExpirationDateBetweenAndRemainingQuantityGreaterThan(LocalDate.now(), LocalDate.now().plusDays(30), BigDecimal.ZERO)
                .stream().map(detail -> detail.getProduct().getProductId()).collect(Collectors.toSet());

        List<Product> allProducts = productRepository.findAll().stream()
                .filter(product -> product.getStatus() == GeneralStatus.ACTIVE)
                .filter(product -> categoryId == null || Objects.equals(product.getCategory().getCategoryId(), categoryId))
                .toList();
        List<Product> filtered = allProducts.stream()
                .filter(product -> "ALL".equals(requestedStatus) || requestedStatus.equals(classifyStock(product)))
                .toList();
        Map<Long, List<Product>> byCategory = filtered.stream().collect(Collectors.groupingBy(product -> product.getCategory().getCategoryId()));
        List<InventoryCategorySummaryResponse> categories = byCategory.values().stream().map(products -> {
            Category category = products.getFirst().getCategory();
            return new InventoryCategorySummaryResponse(category.getCategoryId(), category.getCategoryName(), products.size(),
                    sumProducts(products), products.stream().filter(product -> "LOW_STOCK".equals(classifyStock(product))).count(),
                    products.stream().filter(product -> "OUT_OF_STOCK".equals(classifyStock(product))).count());
        }).sorted(Comparator.comparing(InventoryCategorySummaryResponse::categoryName, Comparator.nullsLast(String::compareToIgnoreCase))).toList();
        List<InventoryReportItemResponse> items = filtered.stream().map(product -> new InventoryReportItemResponse(
                product.getProductId(), product.getProductCode(), product.getProductName(), product.getCategory().getCategoryId(),
                product.getCategory().getCategoryName(), safe(product.getStockQuantity()), safe(product.getMinimumStockLevel()),
                classifyStock(product), nearExpiryIds.contains(product.getProductId())
        )).sorted(Comparator.comparing(InventoryReportItemResponse::productName, Comparator.nullsLast(String::compareToIgnoreCase))).toList();
        return new InventoryReportResponse(categoryId, requestedStatus, filtered.size(),
                filtered.stream().filter(product -> "IN_STOCK".equals(classifyStock(product))).count(),
                filtered.stream().filter(product -> "LOW_STOCK".equals(classifyStock(product))).count(),
                filtered.stream().filter(product -> "OUT_OF_STOCK".equals(classifyStock(product))).count(),
                filtered.stream().filter(product -> nearExpiryIds.contains(product.getProductId())).count(), sumProducts(filtered), categories, items);
    }

    @Override
    public RevenueReportResponse getRevenueReport(LocalDate fromDate, LocalDate toDate) {
        DateRange range = dateRange(fromDate, toDate);
        List<Sale> completed = saleRepository.findByStatusAndCompletedAtBetween(SaleStatus.COMPLETED, range.from(), range.to());
        BigDecimal totalRevenue = sum(completed, Sale::getTotalAmount);
        List<Payment> payments = paymentRepository.findBySaleStatusAndPaymentStatusAndSaleCompletedAtBetween(
                SaleStatus.COMPLETED, PaymentStatus.SUCCESS, range.from(), range.to());
        BigDecimal cash = sumPayments(payments, PaymentMethod.CASH);
        BigDecimal card = sumPayments(payments, PaymentMethod.CARD);
        BigDecimal eWallet = sumPayments(payments, PaymentMethod.EWALLET);
        BigDecimal other = totalRevenue.subtract(cash).subtract(card).subtract(eWallet).max(BigDecimal.ZERO);
        List<RevenueByDayResponse> daily = completed.stream().collect(Collectors.groupingBy(sale -> sale.getCompletedAt().toLocalDate()))
                .entrySet().stream().map(entry -> new RevenueByDayResponse(entry.getKey(), sum(entry.getValue(), Sale::getTotalAmount)))
                .sorted(Comparator.comparing(RevenueByDayResponse::date)).toList();
        return new RevenueReportResponse(range.fromDate(), range.toDate(), totalRevenue, cash, card, eWallet, other, daily);
    }

    @Override
    public ProductSalesReportResponse getProductSalesReport(LocalDate fromDate, LocalDate toDate, Long categoryId, String sortBy) {
        DateRange range = dateRange(fromDate, toDate);
        String normalizedSort = normalizeSort(sortBy);
        List<Long> completedIds = saleRepository.findByStatusAndCompletedAtBetween(SaleStatus.COMPLETED, range.from(), range.to())
                .stream().map(Sale::getSaleId).toList();
        List<SaleItem> items = completedIds.isEmpty() ? List.of() : saleItemRepository.findBySaleSaleIdIn(completedIds).stream()
                .filter(item -> categoryId == null || Objects.equals(item.getProduct().getCategory().getCategoryId(), categoryId)).toList();
        Map<Long, List<SaleItem>> byProduct = items.stream().collect(Collectors.groupingBy(item -> item.getProduct().getProductId()));
        BigDecimal totalNetSales = items.stream().map(this::netItemValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        Comparator<ProductSalesItemResponse> comparator = "REVENUE".equals(normalizedSort)
                ? Comparator.comparing(ProductSalesItemResponse::netSales).reversed().thenComparing(ProductSalesItemResponse::productName)
                : Comparator.comparing(ProductSalesItemResponse::quantitySold).reversed().thenComparing(ProductSalesItemResponse::productName);
        List<ProductSalesItemResponse> products = byProduct.values().stream().map(group -> productSummary(group, totalNetSales)).sorted(comparator).toList();
        return new ProductSalesReportResponse(range.fromDate(), range.toDate(), categoryId, normalizedSort, totalNetSales, products);
    }

    private ProductSalesItemResponse productSummary(List<SaleItem> items, BigDecimal totalNetSales) {
        Product product = items.getFirst().getProduct();
        BigDecimal quantity = items.stream().map(SaleItem::getQuantity).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gross = items.stream().map(item -> safe(item.getQuantity()).multiply(safe(item.getUnitPrice()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = items.stream().map(SaleItem::getDiscountAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = gross.subtract(discount);
        BigDecimal share = totalNetSales.signum() == 0 ? BigDecimal.ZERO : net.multiply(BigDecimal.valueOf(100)).divide(totalNetSales, 2, java.math.RoundingMode.HALF_UP);
        return new ProductSalesItemResponse(product.getProductId(), product.getProductCode(), product.getProductName(),
                product.getCategory().getCategoryId(), product.getCategory().getCategoryName(), quantity, gross, discount, net, share);
    }

    private BigDecimal netItemValue(SaleItem item) { return safe(item.getQuantity()).multiply(safe(item.getUnitPrice())).subtract(safe(item.getDiscountAmount())); }
    private BigDecimal sumProducts(List<Product> products) { return products.stream().map(Product::getStockQuantity).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private BigDecimal sum(List<Sale> sales, Function<Sale, BigDecimal> extractor) { return sales.stream().map(extractor).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private BigDecimal sumPayments(List<Payment> payments, PaymentMethod method) { return payments.stream().filter(payment -> payment.getPaymentMethod() == method).map(Payment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private BigDecimal safe(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private String classifyStock(Product product) { return safe(product.getStockQuantity()).signum() <= 0 ? "OUT_OF_STOCK" : safe(product.getStockQuantity()).compareTo(safe(product.getMinimumStockLevel())) <= 0 ? "LOW_STOCK" : "IN_STOCK"; }
    private String normalizeStockStatus(String status) { String value = status == null || status.isBlank() ? "ALL" : status.trim().toUpperCase(Locale.ROOT); if (!Set.of("ALL", "IN_STOCK", "LOW_STOCK", "OUT_OF_STOCK").contains(value)) throw new BadRequestException("Unsupported stock status: " + status); return value; }
    private String normalizeSort(String sort) { String value = sort == null || sort.isBlank() ? "QUANTITY" : sort.trim().toUpperCase(Locale.ROOT); if (!Set.of("QUANTITY", "REVENUE").contains(value)) throw new BadRequestException("Unsupported product-sales sort: " + sort); return value; }
    private DateRange dateRange(LocalDate fromDate, LocalDate toDate) { LocalDate today = LocalDate.now(); LocalDate start = fromDate == null ? YearMonth.from(today).atDay(1) : fromDate; LocalDate end = toDate == null ? today : toDate; if (start.isAfter(end)) throw new BadRequestException("From date cannot be after To date"); return new DateRange(start, end, start.atStartOfDay(), end.atTime(LocalTime.MAX)); }
    private record DateRange(LocalDate fromDate, LocalDate toDate, LocalDateTime from, LocalDateTime to) {}
}
