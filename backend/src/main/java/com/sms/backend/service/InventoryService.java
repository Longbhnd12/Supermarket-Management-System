package com.sms.backend.service;

import com.sms.backend.dto.inventory.StockCheckResponse;
import com.sms.backend.dto.inventory.StockMovementResponse;
import com.sms.backend.dto.inventory.StockReceiptResponse;
import com.sms.backend.enums.StockMovementType;
import com.sms.backend.enums.StockReceiptStatus;
import com.sms.backend.enums.StockReferenceType;

import java.util.List;

/**
 * Service interface for inventory-related operations.
 * Covers StockReceipt, StockMovement, and StockCheck.
 * TODO (SDD): Confirm whether StockReceipt confirmation auto-creates StockMovement records.
 * TODO (SDD): Confirm StockCheck adjustment flow (approved → creates ADJUSTMENT movement).
 */
public interface InventoryService {

    // --- StockReceipt ---

    List<StockReceiptResponse> getAllStockReceipts();

    List<StockReceiptResponse> getStockReceiptsByStatus(StockReceiptStatus status);

    StockReceiptResponse getStockReceiptById(Long id);

    /**
     * TODO (SDD): Confirm StockReceipt creation request DTO fields (needs detail lines).
     * This method signature is intentionally left incomplete pending SDD review.
     */
    // StockReceiptResponse createStockReceipt(StockReceiptRequest request);

    /**
     * Confirms a DRAFT StockReceipt, updating stock quantities.
     * TODO (SDD): Confirm what exactly changes on confirmation.
     */
    StockReceiptResponse confirmStockReceipt(Long id);

    // --- StockMovement ---

    List<StockMovementResponse> getMovementsByProduct(Long productId);

    List<StockMovementResponse> getMovementsByType(StockMovementType movementType);

    List<StockMovementResponse> getMovementsByReference(StockReferenceType referenceType, Long referenceId);

    // --- StockCheck ---

    List<StockCheckResponse> getAllStockChecks();

    StockCheckResponse getStockCheckById(Long id);

    List<StockCheckResponse> getStockChecksByProduct(Long productId);
}
