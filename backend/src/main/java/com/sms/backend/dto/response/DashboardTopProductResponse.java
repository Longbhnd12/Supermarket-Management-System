package com.sms.backend.dto.response;

import java.math.BigDecimal;

public record DashboardTopProductResponse(Long productId, String productName, BigDecimal quantitySold, BigDecimal netSales) {}
