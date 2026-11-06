package com.sms.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InvoiceListItemResponse(Long saleId, String invoiceNumber, String saleNumber,
                                      LocalDateTime completedAt, String cashierUsername, BigDecimal totalAmount) {}
