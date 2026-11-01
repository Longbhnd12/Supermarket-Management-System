package com.sms.backend.service;

import com.sms.backend.dto.product.ProductResponse;
import com.sms.backend.dto.purchaseorder.PurchaseOrderRequest;
import com.sms.backend.dto.purchaseorder.PurchaseOrderResponse;
import com.sms.backend.enums.PurchaseOrderStatus;

import java.util.List;

/**
 * Service interface for Purchase Order operations.
 */
public interface PurchaseOrderService {

    List<PurchaseOrderResponse> getAllPurchaseOrders();

    List<PurchaseOrderResponse> getPurchaseOrdersByStatus(PurchaseOrderStatus status);

    PurchaseOrderResponse getPurchaseOrderById(Long id);

    PurchaseOrderResponse createPurchaseOrder(PurchaseOrderRequest request);

    String getNextRequestNumber();

    List<ProductResponse> getLowStockSuggestions();

    PurchaseOrderResponse approvePurchaseOrder(Long id);

    PurchaseOrderResponse rejectPurchaseOrder(Long id, String reason);

    PurchaseOrderResponse cancelPurchaseOrder(Long id);
}
