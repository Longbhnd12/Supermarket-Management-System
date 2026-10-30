package com.sms.backend.service;

import com.sms.backend.dto.response.PageResponse;
import com.sms.backend.dto.response.SaleListItemResponse;

import java.time.LocalDateTime;

public interface SaleService {
    PageResponse<SaleListItemResponse> getSales(
            String search, String status, LocalDateTime fromDate, LocalDateTime toDate, int page, int size
    );
}
