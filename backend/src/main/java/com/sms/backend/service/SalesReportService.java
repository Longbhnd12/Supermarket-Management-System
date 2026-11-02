package com.sms.backend.service;

import com.sms.backend.dto.response.SalesReportResponse;

import java.time.LocalDate;

public interface SalesReportService {
    SalesReportResponse getSalesReport(LocalDate fromDate, LocalDate toDate);
}
