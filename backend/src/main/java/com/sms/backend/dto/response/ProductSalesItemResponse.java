package com.sms.backend.dto.response;

import java.math.BigDecimal;

public record ProductSalesItemResponse(
        Long productId, String productCode, String productName, Long categoryId, String categoryName,
        BigDecimal quantitySold, BigDecimal grossSales, BigDecimal discount, BigDecimal netSales, BigDecimal revenueShare
) {}
