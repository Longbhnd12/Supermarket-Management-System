package com.sms.backend.dto.payment;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CashChangeRequest(
        @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal receivedAmount,
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal expectedTotal
) {}
