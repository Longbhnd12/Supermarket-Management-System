package com.sms.backend.service;

import com.sms.backend.dto.sale.SaleItemRequest;
import com.sms.backend.dto.sale.SaleResponse;
import com.sms.backend.dto.sale.CreateSaleRequest;
import com.sms.backend.dto.sale.SaleProductResponse;
import com.sms.backend.dto.sale.UpdateSaleItemRequest;
import com.sms.backend.enums.SaleStatus;

import java.util.List;

/**
 * Service interface for Sale (POS) operations.
 * TODO (SDD): Confirm sale lifecycle: DRAFT -> COMPLETED/CANCELLED state transitions.
 * TODO (SDD): Confirm whether a sale auto-creates StockMovement records on completion.
 * TODO (SDD): Confirm discount application rules (promotion vs. manual discount).
 */
public interface SaleService {

    List<SaleResponse> getAllSales();

    List<SaleResponse> getSalesByStatus(SaleStatus status);

    SaleResponse getSaleById(Long id);

    SaleResponse getSaleBySaleNumber(String saleNumber);

    /**
     * Creates a DRAFT for an explicitly supplied active user until authentication is implemented.
     * Generates a SALE-UUID transaction number; no invoice is issued here.
     */
    SaleResponse createSale(CreateSaleRequest request);

    List<SaleProductResponse> searchProducts(String barcode, String keyword);

    /**
     * Adds an item to an existing DRAFT sale.
     */
    SaleResponse addItemToSale(Long saleId, SaleItemRequest itemRequest);

    SaleResponse updateItemQuantity(Long saleId, Long saleItemId, UpdateSaleItemRequest request);

    /** Recalculates persisted line subtotals and sale totals for a DRAFT. */
    SaleResponse calculateSaleTotal(Long saleId);

    /**
     * Removes an item from an existing DRAFT sale.
     */
    SaleResponse removeItemFromSale(Long saleId, Long saleItemId);

    /**
     * Completes an already paid sale through UC30 FEFO allocation and invoice issuance.
     */
    SaleResponse completeSale(Long saleId);

    /**
     * Cancels a DRAFT or COMPLETED sale.
     * TODO (SDD): Confirm cancellation rules and stock reversal policy.
     */
    SaleResponse cancelSale(Long saleId);
}
