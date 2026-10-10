// CashChangeResponse - calculates required change and bill breakdown
package com.sms.backend.dto.payment;

import java.math.BigDecimal;

public record CashChangeResponse(Long saleId, BigDecimal totalAmount, BigDecimal receivedAmount,
                                 BigDecimal changeAmount, BigDecimal shortfallAmount, boolean sufficient) {}
