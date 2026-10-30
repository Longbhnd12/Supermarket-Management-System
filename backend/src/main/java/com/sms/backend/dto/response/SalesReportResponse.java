package com.sms.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** UC38 report output. Revenue/profit/COGS are deliberately not conflated. */
public record SalesReportResponse(
        LocalDate fromDate,
        LocalDate toDate,
        long totalTransactions,
        long completedTransactions,
        long cancelledTransactions,
        BigDecimal totalItemsSold,
        BigDecimal grossSales,
        BigDecimal totalDiscount,
        BigDecimal netSales,
        List<DailySalesResponse> dailySales
) {
}
