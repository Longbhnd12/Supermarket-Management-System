package com.sms.backend.service;

import com.sms.backend.dto.product.ProductResponse;
import com.sms.backend.dto.purchaseorder.ApprovePurchaseOrderRequest;
import com.sms.backend.dto.purchaseorder.PurchaseOrderFilter;
import com.sms.backend.dto.purchaseorder.PurchaseOrderPageResponse;
import com.sms.backend.dto.purchaseorder.PurchaseOrderRequest;
import com.sms.backend.dto.purchaseorder.PurchaseOrderResponse;
import com.sms.backend.dto.purchaseorder.PurchaseOrderStatusCountsResponse;
import com.sms.backend.dto.purchaseorder.RejectPurchaseOrderRequest;
import com.sms.backend.enums.PurchaseOrderStatus;

import java.util.List;

/**
 * Service interface for Purchase Order & Purchase Request operations.
 */
public interface PurchaseOrderService {

    List<PurchaseOrderResponse> getAllPurchaseOrders();

    List<PurchaseOrderResponse> getPurchaseOrdersByStatus(PurchaseOrderStatus status);

    PurchaseOrderPageResponse getPurchaseOrdersPage(PurchaseOrderFilter filter);

    PurchaseOrderStatusCountsResponse getStatusCounts(PurchaseOrderFilter filter);

    List<PurchaseOrderResponse> getPendingPurchaseRequests();

    PurchaseOrderResponse getPurchaseOrderById(Long id);

    PurchaseOrderResponse createPurchaseOrder(PurchaseOrderRequest request);

    String getNextRequestNumber();

    List<ProductResponse> getLowStockSuggestions();

    PurchaseOrderResponse approvePurchaseOrder(Long id, ApprovePurchaseOrderRequest request);

    default PurchaseOrderResponse approvePurchaseOrder(Long id) {
        return approvePurchaseOrder(id, new ApprovePurchaseOrderRequest(null, null));
    }

    PurchaseOrderResponse rejectPurchaseOrder(Long id, RejectPurchaseOrderRequest request);

    default PurchaseOrderResponse rejectPurchaseOrder(Long id, String reason) {
        return rejectPurchaseOrder(id, new RejectPurchaseOrderRequest(reason, null));
    }

    PurchaseOrderResponse cancelPurchaseOrder(Long id);
}
