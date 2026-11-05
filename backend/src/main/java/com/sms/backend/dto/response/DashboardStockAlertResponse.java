package com.sms.backend.dto.response;

import java.math.BigDecimal;

public record DashboardStockAlertResponse(Long productId, String productName, BigDecimal currentStock, BigDecimal minimumStockLevel, String status) {}
