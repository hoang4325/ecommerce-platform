package com.yashmerino.ecommerce.model.order;

import com.yashmerino.ecommerce.model.Order;
import com.yashmerino.ecommerce.model.partner.Partner;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "partner_orders", uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "partner_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PartnerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PartnerOrderStatus status = PartnerOrderStatus.NEW;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_allocation", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAllocation = BigDecimal.ZERO;

    @Column(name = "shipping_allocation", nullable = false, precision = 19, scale = 2)
    private BigDecimal shippingAllocation = BigDecimal.ZERO;

    @Column(name = "commission_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    @Column(name = "partner_payable_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal partnerPayableAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(length = 1000)
    private String rejectionReason;

    @Column(name = "packed_at")
    private LocalDateTime packedAt;

    @Column(name = "ready_to_ship_at")
    private LocalDateTime readyToShipAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 1000)
    private String cancelReason;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void createTimestamps() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}
