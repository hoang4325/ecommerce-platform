package com.yashmerino.ecommerce.services;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyCalculatorTest {
    @Test
    void pointDiscountIsSubtractedExactlyOnceAndShippingDoesNotQualify() {
        MoneyCalculator.Totals totals = MoneyCalculator.calculate(new BigDecimal("100.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20.00"), new BigDecimal("10.00"));
        assertEquals(new BigDecimal("90.00"), totals.grandTotal());
        assertEquals(new BigDecimal("80.00"), totals.qualifyingAmount());
    }

    @Test
    void multiplierIsAppliedBeforeFinalPointRounding() {
        assertEquals(100, MoneyCalculator.earnedPoints(new BigDecimal("80.00"), new BigDecimal("1.00"), new BigDecimal("1.2500")));
        assertEquals(3, MoneyCalculator.earnedPoints(new BigDecimal("2.99"), new BigDecimal("1.00"), new BigDecimal("1.2500")));
    }
}
