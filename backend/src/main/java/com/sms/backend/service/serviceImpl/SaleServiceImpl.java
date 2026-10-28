package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.sale.SaleItemRequest;
import com.sms.backend.dto.sale.SaleResponse;
import com.sms.backend.entity.Sale;
import com.sms.backend.enums.SaleStatus;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.SaleRepository;
import com.sms.backend.service.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleServiceImpl implements SaleService {

    private final SaleRepository saleRepository;

    @Override
    public List<SaleResponse> getAllSales() {
        return saleRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<SaleResponse> getSalesByStatus(SaleStatus status) {
        return saleRepository.findByStatus(status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public SaleResponse getSaleById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    public SaleResponse getSaleBySaleNumber(String saleNumber) {
        return saleRepository.findBySaleNumber(saleNumber)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with number: " + saleNumber));
    }

    @Override
    @Transactional
    public SaleResponse createSale() {
        // TODO: Implement – get authenticated cashier, auto-generate saleNumber,
        //       create DRAFT Sale
        // TODO (SDD): Confirm saleNumber generation pattern
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public SaleResponse addItemToSale(Long saleId, SaleItemRequest itemRequest) {
        // TODO: Implement – validate product availability, apply promotion,
        //       add SaleItem, recalculate totals
        // TODO (SDD): Confirm promotion resolution logic
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public SaleResponse removeItemFromSale(Long saleId, Long saleItemId) {
        // TODO: Implement – remove SaleItem, recalculate totals
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public SaleResponse completeSale(Long saleId) {
        // TODO: Implement – validate items, update stock via StockMovement,
        //       update status to COMPLETED
        // TODO (SDD): Confirm stock decrement and payment workflow
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    @Override
    @Transactional
    public SaleResponse cancelSale(Long saleId) {
        // TODO (SDD): Confirm cancellation rules and stock reversal
        throw new UnsupportedOperationException("Not yet implemented – pending SDD review");
    }

    // --- helpers ---

    private Sale findById(Long id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + id));
    }

    private SaleResponse toResponse(Sale s) {
        // TODO: Implement full mapping including SaleItem list
        // Returning partial response; items list requires SaleItemRepository lookup
        return new SaleResponse(
                s.getSaleId(),
                s.getSaleNumber(),
                s.getInvoiceNumber(),
                s.getCashier().getUserId(),
                s.getCashier().getUsername(),
                s.getSubtotal(),
                s.getDiscountAmount(),
                s.getTotalAmount(),
                s.getStatus(),
                s.getCreatedAt(),
                s.getCompletedAt(),
                List.of() // TODO: load SaleItems via SaleItemRepository
        );
    }
}
