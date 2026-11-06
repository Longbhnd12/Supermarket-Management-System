package com.sms.backend.service;

import com.sms.backend.dto.supplierportal.RespondPurchaseOrderRequest;
import com.sms.backend.dto.supplierportal.SupplierOrderDetailResponse;
import com.sms.backend.dto.supplierportal.SupplierOrderPageResponse;
import com.sms.backend.enums.PurchaseOrderStatus;

import java.time.LocalDate;

/**
 * UC48 - View Purchase Orders (Supplier), UC49 - Respond to Purchase Order.
 * Always works on the supplier linked to the logged-in account.
 */
public interface SupplierPortalService {

    SupplierOrderPageResponse getMyOrders(String keyword, PurchaseOrderStatus status,
                                          LocalDate fromDate, LocalDate toDate, int page, int size);

    SupplierOrderDetailResponse getMyOrder(Long orderId);

    SupplierOrderDetailResponse respond(Long orderId, RespondPurchaseOrderRequest request);
}
