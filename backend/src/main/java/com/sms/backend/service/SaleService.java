package com.sms.backend.service;

import com.sms.backend.dto.response.PageResponse;
import com.sms.backend.dto.response.SaleListItemResponse;
import com.sms.backend.dto.sale.SaleItemRequest;
import com.sms.backend.dto.sale.SaleResponse;
import com.sms.backend.enums.SaleStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface SaleService {
    PageResponse<SaleListItemResponse> getSales(String search, String status, LocalDateTime fromDate, LocalDateTime toDate, int page, int size);
    List<SaleResponse> getAllSales();
    List<SaleResponse> getSalesByStatus(SaleStatus status);
    SaleResponse getSaleById(Long id);
    SaleResponse getSaleBySaleNumber(String saleNumber);
    SaleResponse createSale();
    SaleResponse addItemToSale(Long saleId, SaleItemRequest itemRequest);
    SaleResponse removeItemFromSale(Long saleId, Long saleItemId);
    SaleResponse completeSale(Long saleId);
    SaleResponse cancelSale(Long saleId);
}
