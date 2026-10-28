package com.sms.backend.service;

import com.sms.backend.dto.purchaseorder.PurchaseOrderRequest;
import com.sms.backend.dto.purchaseorder.PurchaseOrderResponse;
import com.sms.backend.enums.PurchaseOrderStatus;

import java.util.List;

/**
 * Service interface for Purchase Order operations.
 * TODO (SDD): Confirm PO approval workflow and which roles can approve.
 * TODO (SDD): Confirm supplier counter-proposal flow (proposedDeliveryDate, proposedQuantity, proposedUnitPrice).
 * TODO (SDD): Confirm auto-generation pattern for poNumber.
 */
public interface PurchaseOrderService {

    List<PurchaseOrderResponse> getAllPurchaseOrders();

    List<PurchaseOrderResponse> getPurchaseOrdersByStatus(PurchaseOrderStatus status);

    PurchaseOrderResponse getPurchaseOrderById(Long id);

    PurchaseOrderResponse createPurchaseOrder(PurchaseOrderRequest request);

    /**
     * Approves a pending PurchaseOrder.
     * TODO (SDD): Confirm what fields change on approval and who is recorded as approvedBy.
     */
    PurchaseOrderResponse approvePurchaseOrder(Long id);

    /**
     * Rejects a pending PurchaseOrder with a reason.
     * TODO (SDD): Confirm rejection workflow.
     */
    PurchaseOrderResponse rejectPurchaseOrder(Long id, String reason);

    /**
     * Cancels a PurchaseOrder.
     * TODO (SDD): Confirm which statuses allow cancellation.
     */
    PurchaseOrderResponse cancelPurchaseOrder(Long id);
}
