package com.sms.backend.service;

import com.sms.backend.entity.*;
import com.sms.backend.enums.StockReceiptStatus;
import com.sms.backend.exception.BadRequestException;
import com.sms.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FefoStockService {
    private final ProductRepository products;
    private final StockReceiptDetailRepository batches;
    private final Clock clock;

    /** Caller holds the sale lock; inspect stock without reserving or consuming it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void validateBeforePayment(List<SaleItem> lines) {
        Map<Long, BigDecimal> required = new TreeMap<>();
        for (SaleItem line : lines)
            required.merge(line.getProduct().getProductId(), line.getQuantity(), BigDecimal::add);
        for (var entry : required.entrySet()) {
            Product product = products.findByIdForUpdate(entry.getKey()).orElseThrow();
            BigDecimal available = available(product, lockEligibleBatches(product));
            if (available.compareTo(entry.getValue()) < 0)
                throw new BadRequestException("Không đủ tồn kho FEFO cho " + product.getProductCode()
                    + ": cần " + entry.getValue().stripTrailingZeros().toPlainString()
                    + ", có thể bán " + available.stripTrailingZeros().toPlainString()
                    + ". Chưa tạo thanh toán. Vui lòng giảm số lượng hoặc nhập và xác nhận lô hàng hợp lệ.");
        }
    }

    /** Shared eligibility rules for pre-payment validation and UC30 allocation. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<StockReceiptDetail> lockEligibleBatches(Product product) {
        LocalDate today = LocalDate.now(clock);
        return batches.lockBatches(product.getProductId()).stream()
            .filter(b -> b.getStockReceipt().getStatus() == StockReceiptStatus.CONFIRMED)
            .filter(b -> b.getRemainingQuantity() != null && b.getRemainingQuantity().signum() > 0)
            .filter(b -> b.getExpirationDate() == null ? !Boolean.TRUE.equals(product.getTrackExpiry()) : !b.getExpirationDate().isBefore(today))
            .sorted(Comparator.comparing(StockReceiptDetail::getExpirationDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(b -> b.getStockReceipt().getReceivedAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StockReceiptDetail::getStockReceiptItemId)).toList();
    }

    public BigDecimal available(Product product, List<StockReceiptDetail> eligible) {
        BigDecimal batchStock = eligible.stream().map(StockReceiptDetail::getRemainingQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal stock = product.getStockQuantity() == null ? BigDecimal.ZERO : product.getStockQuantity();
        return stock.min(batchStock).max(BigDecimal.ZERO);
    }
}