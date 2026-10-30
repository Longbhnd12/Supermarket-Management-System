package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.response.PageResponse;
import com.sms.backend.dto.response.SaleListItemResponse;
import com.sms.backend.dto.sale.SaleItemRequest;
import com.sms.backend.dto.sale.SaleResponse;
import com.sms.backend.entity.Sale;
import com.sms.backend.entity.User;
import com.sms.backend.enums.SaleStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.exception.ResourceNotFoundException;
import com.sms.backend.repository.SaleRepository;
import com.sms.backend.service.SaleService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleServiceImpl implements SaleService {

    private static final int MAX_PAGE_SIZE = 100;
    private final SaleRepository saleRepository;

    @Override
    public PageResponse<SaleListItemResponse> getSales(String search, String status, LocalDateTime fromDate, LocalDateTime toDate, int page, int size) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BadRequestException("From date cannot be after To date");
        }
        Optional<SaleStatus> saleStatus = parseStatus(status);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<Sale> specification = (root, query, builder) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                Join<Sale, User> cashier = root.join("cashier", JoinType.LEFT);
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("saleNumber")), pattern),
                        builder.like(builder.lower(root.get("invoiceNumber")), pattern),
                        builder.like(builder.lower(cashier.get("username")), pattern)
                ));
            }
            saleStatus.ifPresent(value -> predicates.add(builder.equal(root.get("status"), value)));
            if (fromDate != null) predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            if (toDate != null) predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), toDate));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<SaleListItemResponse> result = saleRepository.findAll(specification, pageable).map(this::toListItem);
        return PageResponse.from(result);
    }

    @Override
    public List<SaleResponse> getAllSales() { return saleRepository.findAll().stream().map(this::toResponse).toList(); }

    @Override
    public List<SaleResponse> getSalesByStatus(SaleStatus status) { return saleRepository.findByStatus(status).stream().map(this::toResponse).toList(); }

    @Override
    public SaleResponse getSaleById(Long id) { return toResponse(findById(id)); }

    @Override
    public SaleResponse getSaleBySaleNumber(String saleNumber) {
        return saleRepository.findBySaleNumber(saleNumber).map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with number: " + saleNumber));
    }

    @Override @Transactional
    public SaleResponse createSale() { throw new UnsupportedOperationException("Not yet implemented – pending SDD review"); }

    @Override @Transactional
    public SaleResponse addItemToSale(Long saleId, SaleItemRequest itemRequest) { throw new UnsupportedOperationException("Not yet implemented – pending SDD review"); }

    @Override @Transactional
    public SaleResponse removeItemFromSale(Long saleId, Long saleItemId) { throw new UnsupportedOperationException("Not yet implemented – pending SDD review"); }

    @Override @Transactional
    public SaleResponse completeSale(Long saleId) { throw new UnsupportedOperationException("Not yet implemented – pending SDD review"); }

    @Override @Transactional
    public SaleResponse cancelSale(Long saleId) { throw new UnsupportedOperationException("Not yet implemented – pending SDD review"); }

    private Optional<SaleStatus> parseStatus(String status) {
        if (status == null || status.isBlank()) return Optional.empty();
        if ("PENDING".equalsIgnoreCase(status.trim())) return Optional.of(SaleStatus.DRAFT);
        try { return Optional.of(SaleStatus.valueOf(status.trim().toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException exception) { throw new BadRequestException("Unsupported sale status: " + status); }
    }

    private Sale findById(Long id) {
        return saleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + id));
    }

    private SaleListItemResponse toListItem(Sale sale) {
        User cashier = sale.getCashier();
        return new SaleListItemResponse(sale.getSaleId(), sale.getSaleNumber(), sale.getInvoiceNumber(),
                cashier != null ? cashier.getUserId() : null, cashier != null ? cashier.getUsername() : null,
                sale.getTotalAmount(), sale.getStatus(), sale.getCreatedAt(), sale.getCompletedAt());
    }

    private SaleResponse toResponse(Sale sale) {
        return new SaleResponse(sale.getSaleId(), sale.getSaleNumber(), sale.getInvoiceNumber(),
                sale.getCashier().getUserId(), sale.getCashier().getUsername(), sale.getSubtotal(),
                sale.getDiscountAmount(), sale.getTotalAmount(), sale.getStatus(), sale.getCreatedAt(),
                sale.getCompletedAt(), List.of());
    }
}
