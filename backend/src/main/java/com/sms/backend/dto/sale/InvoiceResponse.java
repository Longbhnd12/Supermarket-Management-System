package com.sms.backend.dto.sale;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Document persisted at completion, independent of mutable products and accounts. */
public record InvoiceResponse(Long saleId, String invoiceNumber, String saleNumber, String storeName,
        String cashier, LocalDateTime completedAt, String paymentMethod, String transactionReference,
        BigDecimal receivedAmount, BigDecimal changeAmount, BigDecimal subtotal,
        BigDecimal discountAmount, BigDecimal totalAmount, List<Line> items) {
    public record Line(Long saleItemId, String productCode, String productName, String unit,
            BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountAmount, BigDecimal subtotal) {}
}
