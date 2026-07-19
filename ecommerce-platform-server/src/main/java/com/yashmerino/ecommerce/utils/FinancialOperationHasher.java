package com.yashmerino.ecommerce.utils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class FinancialOperationHasher {

    private FinancialOperationHasher() {}

    public static String hash(CanonicalPayload payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(payload.toCanonicalString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public static class CanonicalPayload {
        private final StringBuilder sb = new StringBuilder();

        public CanonicalPayload() {}

        public CanonicalPayload field(String name, Object value) {
            if (sb.length() > 0) sb.append('|');
            sb.append(name).append('=');
            if (value == null) {
                sb.append("<null>");
            } else if (value instanceof BigDecimal bd) {
                sb.append(bd.stripTrailingZeros().toPlainString());
            } else {
                sb.append(value.toString().toUpperCase().trim());
            }
            return this;
        }

        public String toCanonicalString() {
            return sb.toString();
        }

        public String build() {
            return sb.toString();
        }

        public static CanonicalPayload refundOperation(Long refundId, String operationType,
                                                        Long orderId, Long paymentId,
                                                        Long partnerOrderId, Long refundItemId,
                                                        BigDecimal amount, String currency,
                                                        Integer quantity, Long sourceVersion) {
            return new CanonicalPayload()
                .field("refundId", refundId)
                .field("operationType", operationType)
                .field("orderId", orderId)
                .field("paymentId", paymentId)
                .field("partnerOrderId", partnerOrderId)
                .field("refundItemId", refundItemId)
                .field("amount", amount)
                .field("currency", currency)
                .field("quantity", quantity)
                .field("sourceVersion", sourceVersion);
        }
    }
}
