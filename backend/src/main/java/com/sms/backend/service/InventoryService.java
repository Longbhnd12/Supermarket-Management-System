package com.sms.backend.service;

import com.sms.backend.dto.inventory.InventoryFilter;
import com.sms.backend.dto.inventory.InventoryPageResponse;
import com.sms.backend.dto.inventory.StockCheckResponse;
import com.sms.backend.dto.inventory.StockMovementResponse;
import com.sms.backend.dto.inventory.StockReceiptFilter;
import com.sms.backend.dto.inventory.StockReceiptPageResponse;
import com.sms.backend.dto.inventory.StockReceiptResponse;
import com.sms.backend.enums.StockMovementType;
import com.sms.backend.enums.StockReceiptStatus;
import com.sms.backend.enums.StockReferenceType;

import java.util.List;

/**
 * Service interface for inventory operations.
 * UC31: getInventoryList, exportInventoryCsv
 * UC37: getStockReceiptList
 */
public interface InventoryService {

    // UC31 - Inventory List
    InventoryPageResponse getInventoryList(InventoryFilter filter);
    byte[] exportInventoryCsv(InventoryFilter filter);

    // UC37 - Stock Receipt List
    StockReceiptPageResponse getStockReceiptList(StockReceiptFilter filter);

    // StockReceipt (legacy/detail)
    List<StockReceiptResponse> getAllStockReceipts();
    List<StockReceiptResponse> getStockReceiptsByStatus(StockReceiptStatus status);
    StockReceiptResponse getStockReceiptById(Long id);
    StockReceiptResponse confirmStockReceipt(Long id);

    // StockMovement
    List<StockMovementResponse> getMovementsByProduct(Long productId);
    List<StockMovementResponse> getMovementsByType(StockMovementType movementType);
    List<StockMovementResponse> getMovementsByReference(StockReferenceType referenceType, Long referenceId);

    // StockCheck
    List<StockCheckResponse> getAllStockChecks();
    StockCheckResponse getStockCheckById(Long id);
    List<StockCheckResponse> getStockChecksByProduct(Long productId);
}