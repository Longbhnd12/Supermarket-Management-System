// CreatePaymentRequest - payload DTO for initiating payment transactions
package com.sms.backend.dto.payment;

import com.sms.backend.enums.PaymentMethod;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotNull PaymentMethod paymentMethod,
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal expectedTotal,
        @Digits(integer = 13, fraction = 2) BigDecimal receivedAmount,
        @Size(max = 20) String bankCode,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,100}") String idempotencyKey
) {}
