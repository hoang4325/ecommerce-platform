package com.yashmerino.ecommerce.model.stripe;

import lombok.Getter;

@Getter
public class StripePaymentResult {

    private final String paymentIntentId;
    private final String status;
    private final String failureCode;
    private final String failureMessage;
    private final boolean terminal;

    public StripePaymentResult(String paymentIntentId, String status) {
        this(paymentIntentId, status, null, null);
    }

    public StripePaymentResult(String paymentIntentId, String status, String failureCode, String failureMessage) {
        this.paymentIntentId = paymentIntentId;
        this.status = status;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.terminal = "SUCCEEDED".equals(status) || "FAILED".equals(status);
    }

    public boolean isTerminal() {
        return terminal;
    }
}
