package com.yashmerino.ecommerce.model;

/*++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 + MIT License
 +
 + Copyright (c) 2023 Artiom Bozieac
 +
 + Permission is hereby granted, free of charge, to any person obtaining a copy
 + of this software and associated documentation files (the "Software"), to deal
 + in the Software without restriction, including without limitation the rights
 + to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 + copies of the Software, and to permit persons to whom the Software is
 + furnished to do so, subject to the following conditions:
 +
 + The above copyright notice and this permission notice shall be included in all
 + copies or substantial portions of the Software.
 +
 + THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 + IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 + FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 + AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 + LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 + OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 + SOFTWARE.
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.yashmerino.ecommerce.model.base.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

/**
 * JPA Entity for cart's item.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity(name = "cart_items")
@Table(name = "cart_items")
public class CartItem extends BaseEntity {

    /**
     * The cart item's product.
     */
    @JsonBackReference
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    /**
     * Partner offer reference (null for legacy product-only items).
     */
    @Column(name = "offer_id")
    private Long offerId;

    /**
     * Optional partner ID (denormalized from offer for query convenience).
     */
    @Column(name = "partner_id")
    private Long partnerId;

    /**
     * Cart Item's name.
     */
    private String name;

    /**
     * Cart Item's price.
     */
    @jakarta.persistence.Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    public void setPrice(Double price) {
        this.price = price == null ? null : BigDecimal.valueOf(price).setScale(2, java.math.RoundingMode.HALF_UP);
    }
    public void setPrice(BigDecimal price) { this.price = price == null ? null : price.setScale(2, java.math.RoundingMode.HALF_UP); }

    /**
     * The cart item's cart.
     */
    @JsonBackReference
    @ManyToOne
    @JoinColumn(name = "cart_id")
    private Cart cart;

    /**
     * Quantity of cart's item.
     */
    private Integer quantity;
}
