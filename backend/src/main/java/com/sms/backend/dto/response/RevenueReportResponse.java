package com.sms.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** UC39 revenue only; it intentionally excludes COGS and profit margin. */
public record RevenueReportResponse(
        LocalDate fromDate, LocalDate toDate, BigDecimal totalRevenue, BigDecimal cashRevenue,
        BigDecimal cardRevenue, BigDecimal eWalletRevenue, BigDecimal otherRevenue,
        List<RevenueByDayResponse> dailyRevenue
) {}
