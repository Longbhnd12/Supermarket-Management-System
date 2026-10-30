package com.sms.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(
        BigDecimal todaySales, BigDecimal monthToDateGrossRevenue, long activeCatalogSkus,
        double inStockHealth, List<DashboardSalesPointResponse> salesTrend,
        List<DashboardTopProductResponse> topProducts, List<DashboardStockAlertResponse> urgentRestock
) {}
