package com.sms.backend.service;

import com.sms.backend.dto.sale.SaleItemRequest;
import com.sms.backend.dto.sale.SaleResponse;
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
     * Creates a new sale in DRAFT status for the currently authenticated cashier.
     * TODO (SDD): Confirm auto-generation pattern for saleNumber.
     */
    SaleResponse createSale();

    /**
     * Adds an item to an existing DRAFT sale.
     */
    SaleResponse addItemToSale(Long saleId, SaleItemRequest itemRequest);

    /**
     * Removes an item from an existing DRAFT sale.
     */
    SaleResponse removeItemFromSale(Long saleId, Long saleItemId);

    /**
     * Completes a sale: validates items, processes payment, updates stock.
     * TODO (SDD): Confirm exact completion workflow and payment integration.
     */
    SaleResponse completeSale(Long saleId);

    /**
     * Cancels a DRAFT or COMPLETED sale.
     * TODO (SDD): Confirm cancellation rules and stock reversal policy.
     */
    SaleResponse cancelSale(Long saleId);
}
