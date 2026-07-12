package com.yashmerino.ecommerce.service;

import com.stripe.exception.StripeException;
import com.yashmerino.ecommerce.model.stripe.StripePaymentResult;
import com.yashmerino.ecommerce.model.stripe.StripeRefundResult;

import java.math.BigDecimal;

public interface StripePaymentService {

    StripePaymentResult charge(BigDecimal amount, String currency, String paymentMethodId) throws StripeException;

    StripePaymentResult charge(BigDecimal amount, String currency, String paymentMethodRef, String idempotencyKey) throws StripeException;

    StripeRefundResult refund(String paymentIntentId, BigDecimal amount, String idempotencyKey) throws StripeException;
}

