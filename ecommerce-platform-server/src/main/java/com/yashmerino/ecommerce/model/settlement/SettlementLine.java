package com.yashmerino.ecommerce.model.settlement;

import com.yashmerino.ecommerce.model.partner.Partner;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlement_lines")
@Getter
@Setter
@NoArgsConstructor
public class SettlementLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private Settlement settlement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Column(name = "line_type", nullable = false, length = 30)
    private String lineType;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "partner_order_id")
    private Long partnerOrderId;

    @Column(name = "refund_id")
    private Long refundId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(length = 500)
    private String description;

    @Column(name = "adjustment_reason", length = 1000)
    private String adjustmentReason;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "idempotency_key", length = 255, unique = true)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void setCreatedAt() {
        createdAt = LocalDateTime.now();
    }
}
