package com.sms.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySalesResponse(
        LocalDate date,
        long completedTransactions,
        BigDecimal netSales
) {
}
