package com.yashmerino.ecommerce.model.stripe;

import lombok.Getter;

@Getter
public class StripeRefundResult {

    private final String stripeRefundId;
    private final String status;
    private final String failureCode;
    private final String failureMessage;

    public StripeRefundResult(String stripeRefundId, String status) {
        this(stripeRefundId, status, null, null);
    }

    public StripeRefundResult(String stripeRefundId, String status, String failureCode, String failureMessage) {
        this.stripeRefundId = stripeRefundId;
        this.status = status;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }
}
