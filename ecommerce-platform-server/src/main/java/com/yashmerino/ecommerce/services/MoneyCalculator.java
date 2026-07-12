package com.yashmerino.ecommerce.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyCalculator {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private MoneyCalculator() {}

    public static Totals calculate(BigDecimal subtotal, BigDecimal productDiscount, BigDecimal orderDiscount,
                                   BigDecimal couponDiscount, BigDecimal redeemedPointValue, BigDecimal shippingFee) {
        BigDecimal qualifying = subtotal.subtract(productDiscount).subtract(orderDiscount)
                .subtract(couponDiscount).subtract(redeemedPointValue).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal grandTotal = qualifying.add(shippingFee).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        return new Totals(grandTotal, qualifying);
    }

    public static int earnedPoints(BigDecimal qualifyingAmount, BigDecimal moneyPerPoint, BigDecimal multiplier) {
        return qualifyingAmount.divide(moneyPerPoint, 8, RoundingMode.DOWN).multiply(multiplier)
                .setScale(0, RoundingMode.DOWN).intValueExact();
    }

    public record Totals(BigDecimal grandTotal, BigDecimal qualifyingAmount) {}
}
