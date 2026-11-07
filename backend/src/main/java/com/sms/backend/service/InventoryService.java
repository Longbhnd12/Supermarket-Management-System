package com.sms.backend.service;

import com.sms.backend.dto.inventory.CreateStockReceiptRequest;
import com.sms.backend.dto.inventory.InventoryFilter;
import com.sms.backend.dto.inventory.InventoryPageResponse;
import com.sms.backend.dto.inventory.StockAdjustmentRequest;
import com.sms.backend.dto.inventory.StockAdjustmentResponse;
import com.sms.backend.dto.inventory.StockAlertFilter;
import com.sms.backend.dto.inventory.StockAlertSummaryResponse;
import com.sms.backend.dto.inventory.StockCheckResponse;
import com.sms.backend.dto.inventory.StockCheckSessionRequest;
import com.sms.backend.dto.inventory.StockCheckSessionResponse;
import com.sms.backend.dto.inventory.StockCheckSessionPageResponse;
import com.sms.backend.dto.inventory.UpdateStockCheckCountRequest;
import com.sms.backend.dto.inventory.StockHistoryFilter;
import com.sms.backend.dto.inventory.StockMovementPageResponse;
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
 * UC32: getNextStockCheckSessionNumber, performStockCheck, getStockCheckSession
 * UC33: adjustStock
 * UC34: getStockHistory
 * UC35: getStockAlerts
 * UC36: createStockReceipt, getNextReceiptNumber
 * UC37: getStockReceiptList, getStockReceiptById, confirmStockReceipt
 */
public interface InventoryService {

    // UC31 - Inventory List
    InventoryPageResponse getInventoryList(InventoryFilter filter);
    byte[] exportInventoryCsv(InventoryFilter filter);

    // UC32 - Check Stock & Stock Difference
    String getNextStockCheckSessionNumber();
    StockCheckSessionResponse performStockCheck(StockCheckSessionRequest request);
    StockCheckSessionResponse getStockCheckSession(String checkNumber);
    List<StockCheckSessionResponse> getAllStockCheckSessions();
    StockCheckSessionPageResponse getStockCheckSessions(int page, int size, String keyword);
    StockCheckResponse updateStockCheckItemCount(Long stockCheckId, UpdateStockCheckCountRequest request);

    // UC33 - Adjust Stock
    StockAdjustmentResponse adjustStock(StockAdjustmentRequest request);
    List<com.sms.backend.dto.inventory.ProductBatchResponse> getProductBatches(Long productId);
    List<com.sms.backend.dto.inventory.GroupedProductBatchResponse> getGroupedProductBatches(Long productId);

    // UC37 - Stock Receipt List
    StockReceiptPageResponse getStockReceiptList(StockReceiptFilter filter);

    // UC37: StockReceipt (legacy/detail)
    List<StockReceiptResponse> getAllStockReceipts();
    List<StockReceiptResponse> getStockReceiptsByStatus(StockReceiptStatus status);
    StockReceiptResponse getStockReceiptById(Long id);
    StockReceiptResponse confirmStockReceipt(Long id);
    StockReceiptResponse updateStockReceipt(Long id, CreateStockReceiptRequest request);
    StockReceiptResponse cancelStockReceipt(Long id);

    // UC36: Create Stock Receipt
    StockReceiptResponse createStockReceipt(CreateStockReceiptRequest request);
    String getNextReceiptNumber();

    // UC34: Stock History
    StockMovementPageResponse getStockHistory(StockHistoryFilter filter);

    // UC35: Stock Alerts
    StockAlertSummaryResponse getStockAlerts(StockAlertFilter filter);

    // StockMovement
    List<StockMovementResponse> getMovementsByProduct(Long productId);
    List<StockMovementResponse> getMovementsByType(StockMovementType movementType);
    List<StockMovementResponse> getMovementsByReference(StockReferenceType referenceType, Long referenceId);

    // StockCheck
    List<StockCheckResponse> getAllStockChecks();
    StockCheckResponse getStockCheckById(Long id);
    List<StockCheckResponse> getStockChecksByProduct(Long productId);
}
