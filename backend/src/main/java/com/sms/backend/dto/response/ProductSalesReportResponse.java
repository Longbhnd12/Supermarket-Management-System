package com.sms.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProductSalesReportResponse(
        LocalDate fromDate, LocalDate toDate, Long categoryId, String sortBy,
        BigDecimal totalNetSales, List<ProductSalesItemResponse> products
) {}
