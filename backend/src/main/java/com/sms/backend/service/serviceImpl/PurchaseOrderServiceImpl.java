package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.purchaseorder.PurchaseOrderRequest;
import com.sms.backend.dto.purchaseorder.PurchaseOrderResponse;
import com.sms.backend.entity.PurchaseOrder;
import com.sms.backend.enums.PurchaseOrderStatus;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.PurchaseOrderRepository;
import com.sms.backend.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;

    @Override
    public List<PurchaseOrderResponse> getAllPurchaseOrders() {
        return purchaseOrderRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<PurchaseOrderResponse> getPurchaseOrdersByStatus(PurchaseOrderStatus status) {
        return purchaseOrderRepository.findByStatus(status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public PurchaseOrderResponse getPurchaseOrderById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public PurchaseOrderResponse createPurchaseOrder(PurchaseOrderRequest request) {
        // TODO: Implement – auto-generate poNumber, resolve supplier + requester,
        //       create PurchaseOrder + PurchaseOrderDetail lines
        // TODO (SDD): Confirm initial status on creation
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public PurchaseOrderResponse approvePurchaseOrder(Long id) {
        // TODO: Implement – validate status, update to APPROVED, set approvedBy
        // TODO (SDD): Confirm role authorization for approval
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public PurchaseOrderResponse rejectPurchaseOrder(Long id, String reason) {
        // TODO: Implement – validate status, update to REJECTED, set responseReason
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public PurchaseOrderResponse cancelPurchaseOrder(Long id) {
        // TODO (SDD): Confirm which statuses allow cancellation
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    // --- helpers ---

    private PurchaseOrder findById(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + id));
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder po) {
        // TODO: Implement full mapping including PurchaseOrderDetail list
        return new PurchaseOrderResponse(
                po.getOrderId(),
                po.getPoNumber(),
                po.getSupplier().getSupplierId(),
                po.getSupplier().getSupplierName(),
                po.getRequestedBy().getUserId(),
                po.getRequestedBy().getUsername(),
                po.getApprovedBy() != null ? po.getApprovedBy().getUserId() : null,
                po.getApprovedBy() != null ? po.getApprovedBy().getUsername() : null,
                po.getTotalAmount(),
                po.getExpectedDeliveryDate(),
                po.getProposedDeliveryDate(),
                po.getStatus(),
                po.getNote(),
                po.getResponseReason(),
                po.getCreatedAt(),
                po.getUpdatedAt(),
                List.of() // TODO: load details via PurchaseOrderDetailRepository
        );
    }
}
