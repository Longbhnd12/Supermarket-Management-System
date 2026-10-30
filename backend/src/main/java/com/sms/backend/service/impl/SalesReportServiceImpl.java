package com.sms.backend.service.impl;

import com.sms.backend.dto.response.DailySalesResponse;
import com.sms.backend.dto.response.SalesReportResponse;
import com.sms.backend.entity.Sale;
import com.sms.backend.entity.SaleItem;
import com.sms.backend.enums.SaleStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.repository.SaleItemRepository;
import com.sms.backend.repository.SaleRepository;
import com.sms.backend.service.SalesReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesReportServiceImpl implements SalesReportService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;

    @Override
    public SalesReportResponse getSalesReport(LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now();
        LocalDate effectiveFrom = fromDate == null ? YearMonth.from(today).atDay(1) : fromDate;
        LocalDate effectiveTo = toDate == null ? today : toDate;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BadRequestException("From date cannot be after To date");
        }

        LocalDateTime from = effectiveFrom.atStartOfDay();
        LocalDateTime to = effectiveTo.atTime(LocalTime.MAX);

        // Financial metrics are derived strictly from completed sales. This keeps
        // drafts and cancelled transactions out of gross/net sales and item totals.
        List<Sale> completedSales = saleRepository.findByStatusAndCompletedAtBetween(SaleStatus.COMPLETED, from, to);
        List<Sale> periodSales = saleRepository.findByCreatedAtBetween(from, to);

        BigDecimal grossSales = sum(completedSales, Sale::getSubtotal);
        BigDecimal totalDiscount = sum(completedSales, Sale::getDiscountAmount);
        BigDecimal netSales = sum(completedSales, Sale::getTotalAmount);

        List<Long> completedIds = completedSales.stream().map(Sale::getSaleId).toList();
        BigDecimal totalItemsSold = completedIds.isEmpty()
                ? BigDecimal.ZERO
                : saleItemRepository.findBySaleSaleIdIn(completedIds).stream()
                        .map(SaleItem::getQuantity)
                        .filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<LocalDate, List<Sale>> dailyGroups = completedSales.stream()
                .filter(sale -> sale.getCompletedAt() != null)
                .collect(Collectors.groupingBy(sale -> sale.getCompletedAt().toLocalDate()));
        List<DailySalesResponse> dailySales = dailyGroups.entrySet().stream()
                .map(entry -> new DailySalesResponse(
                        entry.getKey(),
                        entry.getValue().size(),
                        sum(entry.getValue(), Sale::getTotalAmount)
                ))
                .sorted(Comparator.comparing(DailySalesResponse::date))
                .toList();

        long cancelledTransactions = periodSales.stream().filter(sale -> sale.getStatus() == SaleStatus.CANCELLED).count();
        return new SalesReportResponse(
                effectiveFrom,
                effectiveTo,
                periodSales.size(),
                completedSales.size(),
                cancelledTransactions,
                totalItemsSold,
                grossSales,
                totalDiscount,
                netSales,
                dailySales
        );
    }

    private BigDecimal sum(List<Sale> sales, Function<Sale, BigDecimal> valueExtractor) {
        return sales.stream()
                .map(valueExtractor)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
