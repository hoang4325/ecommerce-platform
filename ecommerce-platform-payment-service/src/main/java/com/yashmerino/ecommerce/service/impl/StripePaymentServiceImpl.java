package com.yashmerino.ecommerce.service.impl;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.net.RequestOptions;
import com.yashmerino.ecommerce.model.stripe.StripePaymentResult;
import com.yashmerino.ecommerce.model.stripe.StripeRefundResult;
import com.yashmerino.ecommerce.service.StripePaymentService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class StripePaymentServiceImpl implements StripePaymentService {

    public StripePaymentResult charge(
            BigDecimal amount,
            String currency,
            String paymentMethodId) throws com.stripe.exception.StripeException {

        com.stripe.param.PaymentIntentCreateParams params =
            com.stripe.param.PaymentIntentCreateParams.builder()
                .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue())
                .setCurrency(currency)
                .setPaymentMethod(paymentMethodId)
                .setConfirm(true)
                .setAutomaticPaymentMethods(
                    com.stripe.param.PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .setAllowRedirects(
                            com.stripe.param.PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER
                        )
                        .build()
                )
                .build();

        com.stripe.model.PaymentIntent intent = com.stripe.model.PaymentIntent.create(params);

        return new StripePaymentResult(intent.getId(), intent.getStatus());
    }

    public StripePaymentResult charge(
            BigDecimal amount,
            String currency,
            String paymentMethodRef,
            String idempotencyKey) throws com.stripe.exception.StripeException {

        com.stripe.param.PaymentIntentCreateParams params =
            com.stripe.param.PaymentIntentCreateParams.builder()
                .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue())
                .setCurrency(currency)
                .setPaymentMethod(paymentMethodRef)
                .setConfirm(true)
                .setAutomaticPaymentMethods(
                    com.stripe.param.PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .setAllowRedirects(
                            com.stripe.param.PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER
                        )
                        .build()
                )
                .build();

        com.stripe.net.RequestOptions options = com.stripe.net.RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        com.stripe.model.PaymentIntent intent = com.stripe.model.PaymentIntent.create(params, options);

        String failureCode = intent.getLastPaymentError() != null ? intent.getLastPaymentError().getCode() : null;
        String failureMessage = intent.getLastPaymentError() != null ? intent.getLastPaymentError().getMessage() : null;

        return new StripePaymentResult(intent.getId(), intent.getStatus(), failureCode, failureMessage);
    }

    public StripeRefundResult refund(String paymentIntentId, BigDecimal amount, String idempotencyKey) throws com.stripe.exception.StripeException {
        com.stripe.param.RefundCreateParams params = com.stripe.param.RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue())
                .build();

        com.stripe.net.RequestOptions options = com.stripe.net.RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        com.stripe.model.Refund refund = com.stripe.model.Refund.create(params, options);

        String failureCode = refund.getFailureReason() != null ? refund.getFailureReason() : null;
        String failureMessage = refund.getFailureReason() != null ? refund.getFailureReason() : null;

        return new StripeRefundResult(refund.getId(), refund.getStatus(), failureCode, failureMessage);
    }
}