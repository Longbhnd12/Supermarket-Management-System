package com.sms.backend.controller;

import com.sms.backend.dto.ApiResponse;
import com.sms.backend.dto.response.InventoryReportResponse;
import com.sms.backend.dto.response.ProductSalesReportResponse;
import com.sms.backend.dto.response.RevenueReportResponse;
import com.sms.backend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;

    @GetMapping("/inventory")
    public ResponseEntity<ApiResponse<InventoryReportResponse>> inventory(
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) String stockStatus
    ) { return ResponseEntity.ok(ApiResponse.success("Inventory report generated", reportService.getInventoryReport(categoryId, stockStatus))); }

    @GetMapping("/revenue")
    public ResponseEntity<ApiResponse<RevenueReportResponse>> revenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) { return ResponseEntity.ok(ApiResponse.success("Revenue report generated", reportService.getRevenueReport(fromDate, toDate))); }

    @GetMapping("/product-sales")
    public ResponseEntity<ApiResponse<ProductSalesReportResponse>> productSales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long categoryId, @RequestParam(defaultValue = "QUANTITY") String sortBy
    ) { return ResponseEntity.ok(ApiResponse.success("Product sales report generated", reportService.getProductSalesReport(fromDate, toDate, categoryId, sortBy))); }
}
