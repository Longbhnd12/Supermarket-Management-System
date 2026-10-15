// CashChangeCalculator - greedy coin/note change distribution algorithm
package com.sms.backend.service;

import com.sms.backend.exception.BadRequestException;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

/** Shared UC29 calculation, reused by UC28. Does not complete a sale. */
@Component
public class CashChangeCalculator {
    public BigDecimal calculate(BigDecimal amount, BigDecimal received) {
        Comparison comparison = compare(amount, received);
        if (!comparison.sufficient()) throw new BadRequestException("Received amount is less than sale total");
        return comparison.changeAmount();
    }

    /** Pure comparison for UC29 preview; insufficient input is a business result, not a failed payment. */
    public Comparison compare(BigDecimal amount, BigDecimal received) {
        BigDecimal maximum = new BigDecimal("9999999999999.99");
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2 || amount.compareTo(maximum) > 0
                || received == null || received.signum() < 0
                || received.stripTrailingZeros().scale() > 2
                || received.compareTo(maximum) > 0) {
            throw new BadRequestException("Valid receivedAmount with at most 2 decimals is required for CASH");
        }
        if (received.compareTo(amount) < 0) return new Comparison(false, null, amount.subtract(received).setScale(2));
        return new Comparison(true, received.subtract(amount).setScale(2), new BigDecimal("0.00"));
    }

    public record Comparison(boolean sufficient, BigDecimal changeAmount, BigDecimal shortfallAmount) {}
}
