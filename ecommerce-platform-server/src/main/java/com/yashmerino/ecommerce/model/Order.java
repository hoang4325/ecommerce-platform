package com.yashmerino.ecommerce.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.yashmerino.ecommerce.model.base.BaseEntity;
import com.yashmerino.ecommerce.utils.OrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA Entity for order.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity(name = "orders")
@Table(name = "orders")
public class Order extends BaseEntity {
    /**
     * Order's user.
     */
    @JsonBackReference
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * Total amount of the order to be paid.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    /**
     * Order's status.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal productDiscount = BigDecimal.ZERO.setScale(2);
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal orderDiscount = BigDecimal.ZERO.setScale(2);
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal couponDiscount = BigDecimal.ZERO.setScale(2);
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal redeemedPointValue = BigDecimal.ZERO.setScale(2);
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO.setScale(2);
    @Column(nullable = false, columnDefinition = "CHAR(3)")
    private String currency = "EUR";
    private LocalDateTime reservationExpiresAt;
    private LocalDateTime paymentDeadline;
    @Version
    private Long version;
}
