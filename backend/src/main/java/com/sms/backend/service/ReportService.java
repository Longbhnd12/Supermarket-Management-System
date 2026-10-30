package com.sms.backend.service;

import com.sms.backend.dto.response.InventoryReportResponse;
import com.sms.backend.dto.response.ProductSalesReportResponse;
import com.sms.backend.dto.response.RevenueReportResponse;

import java.time.LocalDate;

public interface ReportService {
    InventoryReportResponse getInventoryReport(Long categoryId, String stockStatus);
    RevenueReportResponse getRevenueReport(LocalDate fromDate, LocalDate toDate);
    ProductSalesReportResponse getProductSalesReport(LocalDate fromDate, LocalDate toDate, Long categoryId, String sortBy);
}
