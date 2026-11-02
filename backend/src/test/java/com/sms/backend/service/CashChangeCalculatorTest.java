package com.sms.backend.service;

import com.sms.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class CashChangeCalculatorTest {
    private final CashChangeCalculator calculator = new CashChangeCalculator();

    @ParameterizedTest
    @CsvSource({"64000,100000,36000.00,0.00,true", "64000,64000,0.00,0.00,true",
            "64000,60000,,4000.00,false", "64000,0,,64000.00,false", "10.01,20.02,10.01,0.00,true"})
    void comparesMoneyPreciselyWithoutNegativeChange(String total, String received, String change, String shortfall, boolean sufficient) {
        var result = calculator.compare(new BigDecimal(total), new BigDecimal(received));
        assertEquals(sufficient, result.sufficient());
        assertEquals(change == null ? null : new BigDecimal(change), result.changeAmount());
        assertEquals(new BigDecimal(shortfall), result.shortfallAmount());
        if (sufficient) assertEquals(result.changeAmount(), calculator.calculate(new BigDecimal(total), new BigDecimal(received)));
        else assertThrows(BadRequestException.class, () -> calculator.calculate(new BigDecimal(total), new BigDecimal(received)));
    }

    @Test void rejectsInvalidMoneyAndPreservesMaximumPrecision() {
        for (var invalid : Arrays.asList(null, new BigDecimal("-1"), new BigDecimal("1.001"), new BigDecimal("10000000000000"))) {
            assertThrows(BadRequestException.class, () -> calculator.compare(BigDecimal.ONE, invalid));
        }
        for (var invalid : Arrays.asList(null, BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("1.001"), new BigDecimal("10000000000000"))) {
            assertThrows(BadRequestException.class, () -> calculator.compare(invalid, BigDecimal.ONE));
        }
        assertEquals(new BigDecimal("9999999999999.98"), calculator.calculate(new BigDecimal("0.01"), new BigDecimal("9999999999999.99")));
    }
}
