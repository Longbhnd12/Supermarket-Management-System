package com.sms.backend.service.serviceImpl;

import com.sms.backend.dto.response.PageResponse;
import com.sms.backend.dto.response.SaleListItemResponse;
import com.sms.backend.dto.sale.SaleItemRequest;
import com.sms.backend.dto.sale.SaleResponse;
import com.sms.backend.dto.sale.CreateSaleRequest;
import com.sms.backend.dto.sale.SaleProductResponse;
import com.sms.backend.dto.sale.SaleItemResponse;
import com.sms.backend.dto.sale.UpdateSaleItemRequest;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.SaleItem;
import com.sms.backend.entity.User;
import com.sms.backend.enums.AccountStatus;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.repository.PaymentRepository;
import com.sms.backend.repository.SaleItemRepository;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleServiceImpl implements SaleService {

    private static final int MAX_PAGE_SIZE = 100;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final com.sms.backend.service.SaleCompletionService completion;
    private final com.sms.backend.service.SalePromotionCalculator promotions;

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

    @Override
    @Transactional
    public SaleResponse createSale(CreateSaleRequest request) {
        if (request == null || request.cashierId() == null || request.cashierId() <= 0) {
            throw new BadRequestException("A positive cashier ID is required");
        }
        User cashier = userRepository.findById(request.cashierId())
                .orElseThrow(() -> new ResourceNotFoundException("Cashier not found: " + request.cashierId()));
        if (cashier.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("Cashier account must be active");
        }
        Sale sale = new Sale();
        sale.setSaleNumber("SALE-" + UUID.randomUUID());
        sale.setCashier(cashier);
        sale.setStatus(SaleStatus.DRAFT);
        sale.setSubtotal(new BigDecimal("0.00"));
        sale.setDiscountAmount(new BigDecimal("0.00"));
        sale.setTotalAmount(new BigDecimal("0.00"));
        sale.setCreatedAt(LocalDateTime.now());
        return toResponse(saleRepository.save(sale));
    }

    @Override
    public List<SaleProductResponse> searchProducts(String barcode, String keyword) {
        boolean hasBarcode = barcode != null && !barcode.isBlank();
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        if (hasBarcode == hasKeyword) {
            throw new BadRequestException("Provide exactly one of barcode or keyword");
        }
        List<Product> products;
        if (hasBarcode) {
            if (barcode.strip().length() > 100) throw new BadRequestException("Barcode exceeds 100 characters");
            products = productRepository.findByBarcode(barcode.strip())
                    .filter(p -> p.getStatus() == GeneralStatus.ACTIVE).stream().toList();
        } else {
            String query = keyword.strip();
            if (query.length() > 200) throw new BadRequestException("Keyword exceeds 200 characters");
            products = productRepository
                    .findTop50ByStatusAndProductNameContainingIgnoreCaseOrStatusAndProductCodeContainingIgnoreCaseOrderByProductIdAsc(
                            GeneralStatus.ACTIVE, query, GeneralStatus.ACTIVE, query);
        }
        return products.stream().map(p -> new SaleProductResponse(p.getProductId(), p.getProductCode(),
                p.getBarcode(), p.getProductName(), p.getUnit(), p.getSellingPrice(), p.getStockQuantity())).toList();
    }

    @Override
    @Transactional
    public SaleResponse addItemToSale(Long saleId, SaleItemRequest itemRequest) {
        Sale sale = findDraftForUpdate(saleId);
        if (itemRequest == null || itemRequest.productId() == null || itemRequest.productId() <= 0
                || itemRequest.quantity() == null || itemRequest.quantity().signum() <= 0
                || itemRequest.quantity().stripTrailingZeros().scale() > 3
                || itemRequest.quantity().compareTo(new BigDecimal("999999999.999")) > 0) {
            throw new BadRequestException("Invalid product ID or quantity");
        }
        if (itemRequest.promotionId() != null) throw new BadRequestException("Promotions are selected automatically; omit promotionId");
        Product product = productRepository.findById(itemRequest.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemRequest.productId()));
        if (product.getStatus() != GeneralStatus.ACTIVE) throw new BadRequestException("Product must be active");
        if (product.getSellingPrice() == null || product.getSellingPrice().signum() < 0) {
            throw new BadRequestException("Product has no valid selling price");
        }
        List<SaleItem> items = new java.util.ArrayList<>(saleItemRepository.findBySale(sale));
        SaleItem item = items.stream()
                .filter(line -> product.getProductId().equals(line.getProduct().getProductId()))
                .min(java.util.Comparator.comparing(SaleItem::getSaleItemId))
                .orElse(null);
        validateStock(product, items, null, itemRequest.quantity());
        if (item == null) {
            item = new SaleItem();
            item.setSale(sale);
            item.setProduct(product);
            item.setQuantity(itemRequest.quantity());
            item.setUnitPrice(product.getSellingPrice());
            item.setDiscountAmount(new BigDecimal("0.00"));
            items.add(item);
        } else {
            BigDecimal quantity = item.getQuantity().add(itemRequest.quantity());
            validateQuantity(quantity);
            item.setQuantity(quantity);
        }
        recalculateTotals(sale, items);
        saleItemRepository.saveAll(items);
        return toResponse(saleRepository.save(sale));
    }

    @Override
    @Transactional
    public SaleResponse updateItemQuantity(Long saleId, Long saleItemId, UpdateSaleItemRequest request) {
        Sale sale = findDraftForUpdate(saleId);
        if (request == null) throw new BadRequestException("Quantity is required");
        validateQuantity(request.quantity());
        List<SaleItem> items = saleItemRepository.findBySale(sale);
        SaleItem item = findItem(items, saleItemId);
        validateStock(item.getProduct(), items, item, request.quantity());
        BigDecimal lineTotal = item.getUnitPrice().multiply(request.quantity()).setScale(2, RoundingMode.HALF_UP);
        validateMoney(lineTotal);
        item.setQuantity(request.quantity());
        item.setSubtotal(lineTotal);
        recalculateTotals(sale, items);
        saleItemRepository.saveAll(items);
        return toResponse(saleRepository.save(sale));
    }

    @Override
    @Transactional
    public SaleResponse removeItemFromSale(Long saleId, Long saleItemId) {
        Sale sale = findDraftForUpdate(saleId);
        List<SaleItem> items = new java.util.ArrayList<>(saleItemRepository.findBySale(sale));
        SaleItem item = findItem(items, saleItemId);
        items.remove(item);
        recalculateTotals(sale, items);
        saleItemRepository.delete(item);
        saleItemRepository.saveAll(items);
        return toResponse(saleRepository.save(sale));
    }

    @Override
    @Transactional
    public SaleResponse calculateSaleTotal(Long saleId) {
        Sale sale = findDraftForUpdate(saleId);
        List<SaleItem> items = saleItemRepository.findBySale(sale);
        recalculateTotals(sale, items);
        saleItemRepository.saveAll(items);
        return toResponse(saleRepository.save(sale));
    }

    @Override
    @Transactional
    public SaleResponse completeSale(Long saleId) {
        completion.complete(saleId);
        return getSaleById(saleId);
    }

    @Override @Transactional
    public SaleResponse cancelSale(Long saleId) { throw new UnsupportedOperationException("Not yet implemented – pending SDD review"); }

    private Optional<SaleStatus> parseStatus(String status) {
        if (status == null || status.isBlank()) return Optional.empty();
        if ("PENDING".equalsIgnoreCase(status.trim())) return Optional.of(SaleStatus.DRAFT);
        try { return Optional.of(SaleStatus.valueOf(status.trim().toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException exception) { throw new BadRequestException("Unsupported sale status: " + status); }
    }

    private Sale findDraftForUpdate(Long id) {
        Sale sale = saleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + id));
        if (sale.getStatus() != SaleStatus.DRAFT) throw new BadRequestException("Only DRAFT sales can be modified");
        if (paymentRepository.hasBlockingPayment(sale)) throw new BadRequestException("Sale is locked by a pending, successful or unresolved payment");
        return sale;
    }

    private SaleItem findItem(List<SaleItem> items, Long id) {
        return items.stream().filter(item -> id != null && id.equals(item.getSaleItemId())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item not found in this sale: " + id));
    }

    // Validate the aggregate product quantity, including legacy duplicate lines.
    // Drafts do not reserve stock; payment/completion still revalidate FEFO stock.
    private void validateStock(Product product, List<SaleItem> items, SaleItem replaced, BigDecimal quantity) {
        BigDecimal requested = quantity;
        for (SaleItem line : items) {
            if (line != replaced && product.getProductId().equals(line.getProduct().getProductId())) {
                requested = requested.add(line.getQuantity());
            }
        }
        BigDecimal available = product.getStockQuantity() == null ? BigDecimal.ZERO : product.getStockQuantity();
        if (requested.compareTo(available) > 0) {
            throw new BadRequestException("Không đủ tồn kho cho " + product.getProductCode()
                    + ": yêu cầu " + requested.stripTrailingZeros().toPlainString()
                    + ", tồn kho " + available.stripTrailingZeros().toPlainString()
                    + ". Vui lòng giảm số lượng.");
        }
    }

    private void validateQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 3
                || quantity.compareTo(new BigDecimal("999999999.999")) > 0) {
            throw new BadRequestException("Quantity must be positive with at most 9 integer and 3 decimal digits");
        }
    }

    private void validateMoney(BigDecimal amount) {
        if (amount == null || amount.signum() < 0 || amount.stripTrailingZeros().scale() > 2
                || amount.compareTo(new BigDecimal("9999999999999.99")) > 0) {
            throw new BadRequestException("Sale amount exceeds database precision");
        }
    }

    private void recalculateTotals(Sale sale, List<SaleItem> items) {
        BigDecimal subtotal = new BigDecimal("0.00");
        BigDecimal discount = new BigDecimal("0.00");
        for (SaleItem item : items) {
            validateQuantity(item.getQuantity());
            validateMoney(item.getUnitPrice());
            BigDecimal lineSubtotal = item.getUnitPrice().multiply(item.getQuantity()).setScale(2, RoundingMode.HALF_UP);
            validateMoney(lineSubtotal);
            item.setSubtotal(lineSubtotal);
            subtotal = subtotal.add(lineSubtotal);
        }
        validateMoney(subtotal);
        promotions.apply(items);
        for (SaleItem item : items) {
            validateMoney(item.getDiscountAmount());
            discount = discount.add(item.getDiscountAmount());
        }
        validateMoney(discount);
        BigDecimal total = subtotal.subtract(discount);
        validateMoney(total);
        sale.setSubtotal(subtotal);
        sale.setDiscountAmount(discount);
        sale.setTotalAmount(total);
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

    private SaleResponse toResponse(Sale s) {
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
                saleItemRepository.findBySale(s).stream().map(item -> new SaleItemResponse(
                        item.getSaleItemId(), item.getProduct().getProductId(), item.getProduct().getProductName(),
                        item.getProduct().getProductCode(), item.getPromotion() == null ? null : item.getPromotion().getPromotionId(),
                        item.getQuantity(), item.getUnitPrice(), item.getDiscountAmount(), item.getSubtotal())).toList()
        );
    }
}
