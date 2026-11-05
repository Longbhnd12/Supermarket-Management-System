package com.sms.backend.service.serviceImpl;

import com.sms.backend.service.SaleCompletionService;
import com.sms.backend.service.FefoStockService;

import com.sms.backend.dto.sale.InvoiceResponse;
import com.sms.backend.entity.*;
import com.sms.backend.enums.*;
import com.sms.backend.exception.*;
import com.sms.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.math.*;
import java.time.*;
import java.util.*;

@Service("saleCompletionService")
@RequiredArgsConstructor
public class SaleCompletionServiceImpl implements SaleCompletionService {
    private final SaleRepository sales;
    private final SaleItemRepository items;
    private final PaymentRepository payments;
    private final ProductRepository products;
    private final FefoStockService stock;
    private final StockMovementRepository movements;
    private final SystemLogRepository logs;
    private final SystemSettingRepository settings;
    private final ObjectMapper mapper;
    private final Clock clock;

    /** Separate transaction: payment evidence must survive an inventory failure. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public InvoiceResponse complete(Long id) {
        Sale sale = sales.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + id));
        if (sale.getStatus() == SaleStatus.COMPLETED) return readSnapshot(sale);
        if (sale.getStatus() != SaleStatus.DRAFT) throw new BadRequestException("Only a paid DRAFT sale can be completed");
        List<Payment> attempts = payments.findBySaleOrderByPaymentIdDesc(sale);
        List<Payment> successful = attempts.stream().filter(p -> p.getPaymentStatus() == PaymentStatus.SUCCESS).toList();
        if (successful.size() != 1 || attempts.stream().anyMatch(p -> p.isReconciliationRequired() || p.getPaymentStatus() == PaymentStatus.PENDING))
            throw new BadRequestException("Exactly one successful payment and no unresolved attempts are required");
        Payment payment = successful.getFirst();
        if (sale.getTotalAmount() == null || sale.getTotalAmount().signum() <= 0 || payment.getAmount().compareTo(sale.getTotalAmount()) != 0)
            throw new BadRequestException("Successful payment does not match sale total");
        List<SaleItem> lines = items.findBySale(sale).stream().sorted(Comparator.comparing(SaleItem::getSaleItemId)).toList();
        validateTotals(sale, lines);

        LocalDateTime now = LocalDateTime.now(clock);
        Map<Long, List<SaleItem>> grouped = new TreeMap<>();
        for (SaleItem line : lines) grouped.computeIfAbsent(line.getProduct().getProductId(), key -> new ArrayList<>()).add(line);
        // All writers acquire product locks in ID order, then lock that product's batches.
        for (var group : grouped.entrySet()) {
            Product product = products.findByIdForUpdate(group.getKey()).orElseThrow();
            BigDecimal required = group.getValue().stream().map(SaleItem::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<StockReceiptDetail> eligible = stock.lockEligibleBatches(product);
            BigDecimal available = stock.available(product, eligible);
            if (available.compareTo(required) < 0)
                throw new BadRequestException("Insufficient eligible FEFO stock for " + product.getProductCode() + "; payment remains successful. Receive confirmed batches before retrying completion.");
            for (SaleItem line : group.getValue()) {
                BigDecimal remaining = line.getQuantity();
                for (StockReceiptDetail batch : eligible) {
                    BigDecimal take = remaining.min(batch.getRemainingQuantity());
                    if (take.signum() == 0) continue;
                    batch.setRemainingQuantity(batch.getRemainingQuantity().subtract(take));
                    StockMovement movement = new StockMovement();
                    movement.setProduct(product); movement.setStockReceiptDetail(batch);
                    movement.setMovementType(StockMovementType.SALE); movement.setQuantityChange(take.negate());
                    movement.setReferenceType(StockReferenceType.SALE_ITEM); movement.setReferenceId(line.getSaleItemId());
                    movement.setCreatedBy(sale.getCashier()); movement.setCreatedAt(now); movement.setReason("UC30 FEFO sale completion");
                    movements.save(movement);
                    remaining = remaining.subtract(take);
                    if (remaining.signum() == 0) break;
                }
            }
            product.setStockQuantity(product.getStockQuantity().subtract(required));
        }
        sale.setInvoiceNumber("INV-" + UUID.randomUUID());
        sale.setCompletedAt(now); sale.setStatus(SaleStatus.COMPLETED); sale.setCompletionRequested(false);
        String store = settings.findBySettingKey("store_name").map(SystemSetting::getSettingValue)
            .filter(v -> !v.isBlank()).orElse("Supermarket Management System");
        InvoiceResponse invoice = new InvoiceResponse(id, sale.getInvoiceNumber(), sale.getSaleNumber(), store,
            sale.getCashier().getUsername(), now, payment.getPaymentMethod().name(), payment.getTxnRef(),
            payment.getReceivedAmount(), payment.getChangeAmount(), sale.getSubtotal(), sale.getDiscountAmount(), sale.getTotalAmount(),
            lines.stream().map(l -> new InvoiceResponse.Line(l.getSaleItemId(), l.getProduct().getProductCode(),
                l.getProduct().getProductName(), l.getProduct().getUnit(), l.getQuantity(), l.getUnitPrice(), l.getDiscountAmount(), l.getSubtotal())).toList());
        sale.setInvoiceSnapshot(mapper.writeValueAsString(invoice));
        SystemLog log = new SystemLog(); log.setUser(sale.getCashier()); log.setAction("COMPLETE_SALE");
        log.setEntityType("SALE"); log.setEntityId(id); log.setCreatedAt(now);
        log.setDescription("System UC30 completed paid sale; invoice " + sale.getInvoiceNumber() + "; payment " + payment.getPaymentId());
        logs.save(log);
        sales.saveAndFlush(sale);
        return invoice;
    }

    private void validateTotals(Sale sale, List<SaleItem> lines) {
        if (lines.isEmpty()) throw new BadRequestException("Cannot complete an empty sale");
        BigDecimal subtotal = BigDecimal.ZERO, discount = BigDecimal.ZERO;
        for (SaleItem l : lines) {
            if (l.getQuantity() == null || l.getQuantity().signum() <= 0 || l.getQuantity().stripTrailingZeros().scale() > 3
                || l.getUnitPrice() == null || l.getUnitPrice().signum() < 0 || l.getDiscountAmount() == null || l.getDiscountAmount().signum() < 0)
                throw new BadRequestException("Invalid sale item amounts");
            BigDecimal calculated = l.getUnitPrice().multiply(l.getQuantity()).setScale(2, RoundingMode.HALF_UP);
            if (calculated.compareTo(l.getSubtotal()) != 0 || l.getDiscountAmount().compareTo(calculated) > 0)
                throw new BadRequestException("Sale item totals do not match payment");
            subtotal = subtotal.add(calculated); discount = discount.add(l.getDiscountAmount());
        }
        if (subtotal.compareTo(sale.getSubtotal()) != 0 || discount.compareTo(sale.getDiscountAmount()) != 0
            || subtotal.subtract(discount).compareTo(sale.getTotalAmount()) != 0) throw new BadRequestException("Sale totals are inconsistent");
    }

    @Transactional(readOnly = true)
    @Override
    public InvoiceResponse invoice(Long id) {
        return readSnapshot(sales.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + id)));
    }
    private InvoiceResponse readSnapshot(Sale sale) {
        if (sale.getStatus() != SaleStatus.COMPLETED || sale.getInvoiceSnapshot() == null)
            throw new BadRequestException("Invoice is only available after sale completion");
        return mapper.readValue(sale.getInvoiceSnapshot(), InvoiceResponse.class);
    }
}
