package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.inventory.StockCheckResponse;
import com.sms.backend.dto.inventory.StockMovementResponse;
import com.sms.backend.dto.inventory.StockReceiptResponse;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.StockCheck;
import com.sms.backend.entity.StockMovement;
import com.sms.backend.entity.StockReceipt;
import com.sms.backend.enums.StockMovementType;
import com.sms.backend.enums.StockReceiptStatus;
import com.sms.backend.enums.StockReferenceType;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.repository.StockCheckRepository;
import com.sms.backend.repository.StockMovementRepository;
import com.sms.backend.repository.StockReceiptRepository;
import com.sms.backend.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final StockReceiptRepository stockReceiptRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockCheckRepository stockCheckRepository;
    private final ProductRepository productRepository;

    // --- StockReceipt ---

    @Override
    public List<StockReceiptResponse> getAllStockReceipts() {
        return stockReceiptRepository.findAll()
                .stream()
                .map(this::toReceiptResponse)
                .toList();
    }

    @Override
    public List<StockReceiptResponse> getStockReceiptsByStatus(StockReceiptStatus status) {
        return stockReceiptRepository.findByStatus(status)
                .stream()
                .map(this::toReceiptResponse)
                .toList();
    }

    @Override
    public StockReceiptResponse getStockReceiptById(Long id) {
        return toReceiptResponse(findReceiptById(id));
    }

    @Override
    @Transactional
    public StockReceiptResponse confirmStockReceipt(Long id) {
        // TODO: Implement – validate DRAFT status, update stock quantities,
        //       create StockMovement records, update status to CONFIRMED
        // TODO (SDD): Confirm exact confirmation workflow
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    // --- StockMovement ---

    @Override
    public List<StockMovementResponse> getMovementsByProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        return stockMovementRepository.findByProduct(product)
                .stream()
                .map(this::toMovementResponse)
                .toList();
    }

    @Override
    public List<StockMovementResponse> getMovementsByType(StockMovementType movementType) {
        return stockMovementRepository.findByMovementType(movementType)
                .stream()
                .map(this::toMovementResponse)
                .toList();
    }

    @Override
    public List<StockMovementResponse> getMovementsByReference(StockReferenceType referenceType, Long referenceId) {
        return stockMovementRepository.findByReferenceTypeAndReferenceId(referenceType, referenceId)
                .stream()
                .map(this::toMovementResponse)
                .toList();
    }

    // --- StockCheck ---

    @Override
    public List<StockCheckResponse> getAllStockChecks() {
        return stockCheckRepository.findAll()
                .stream()
                .map(this::toCheckResponse)
                .toList();
    }

    @Override
    public StockCheckResponse getStockCheckById(Long id) {
        return toCheckResponse(findCheckById(id));
    }

    @Override
    public List<StockCheckResponse> getStockChecksByProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        return stockCheckRepository.findByProduct(product)
                .stream()
                .map(this::toCheckResponse)
                .toList();
    }

    // --- helpers ---

    private StockReceipt findReceiptById(Long id) {
        return stockReceiptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StockReceipt not found: " + id));
    }

    private StockCheck findCheckById(Long id) {
        return stockCheckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StockCheck not found: " + id));
    }

    private StockReceiptResponse toReceiptResponse(StockReceipt sr) {
        return new StockReceiptResponse(
                sr.getStockReceiptId(),
                sr.getReceiptNumber(),
                sr.getSupplier().getSupplierId(),
                sr.getSupplier().getSupplierName(),
                sr.getPurchaseOrder() != null ? sr.getPurchaseOrder().getOrderId() : null,
                sr.getPurchaseOrder() != null ? sr.getPurchaseOrder().getPoNumber() : null,
                sr.getCreatedBy().getUserId(),
                sr.getCreatedBy().getUsername(),
                sr.getTotalCost(),
                sr.getStatus(),
                sr.getReceivedAt(),
                sr.getCreatedAt(),
                List.of() // TODO: load detail lines via StockReceiptDetailRepository
        );
    }

    private StockMovementResponse toMovementResponse(StockMovement sm) {
        return new StockMovementResponse(
                sm.getMovementId(),
                sm.getProduct().getProductId(),
                sm.getProduct().getProductName(),
                sm.getProduct().getProductCode(),
                sm.getMovementType(),
                sm.getQuantityChange(),
                sm.getReferenceType(),
                sm.getReferenceId(),
                sm.getReason(),
                sm.getCreatedBy().getUserId(),
                sm.getCreatedBy().getUsername(),
                sm.getCreatedAt()
        );
    }

    private StockCheckResponse toCheckResponse(StockCheck sc) {
        return new StockCheckResponse(
                sc.getStockCheckId(),
                sc.getCheckNumber(),
                sc.getProduct().getProductId(),
                sc.getProduct().getProductName(),
                sc.getProduct().getProductCode(),
                sc.getCheckedBy().getUserId(),
                sc.getCheckedBy().getUsername(),
                sc.getSystemQuantity(),
                sc.getActualQuantity(),
                sc.getDifference(),
                sc.getReason(),
                sc.getStatus(),
                sc.getCheckedAt()
        );
    }
}
