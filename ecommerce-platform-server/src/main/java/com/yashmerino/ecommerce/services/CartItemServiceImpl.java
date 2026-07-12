package com.yashmerino.ecommerce.services;

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

import com.yashmerino.ecommerce.model.Cart;
import com.yashmerino.ecommerce.model.CartItem;
import com.yashmerino.ecommerce.model.Product;
import com.yashmerino.ecommerce.model.User;
import com.yashmerino.ecommerce.model.offer.PartnerOffer;
import com.yashmerino.ecommerce.model.offer.PartnerOfferStatus;
import com.yashmerino.ecommerce.repositories.CartItemRepository;
import com.yashmerino.ecommerce.repositories.CartRepository;
import com.yashmerino.ecommerce.repositories.PartnerOfferRepository;
import com.yashmerino.ecommerce.repositories.UserRepository;
import com.yashmerino.ecommerce.services.interfaces.CartItemService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Implementation for cart item service.
 */
@Service
public class CartItemServiceImpl implements CartItemService {

    /**
     * Cart Item repository.
     */
    private final CartItemRepository cartItemRepository;

    /**
     * Partner Offer repository.
     */
    private final PartnerOfferRepository partnerOfferRepository;

    /**
     * User repository.
     */
    private final UserRepository userRepository;

    /**
     * Cart repository.
     */
    private final CartRepository cartRepository;

    /**
     * Access denied message translation key.
     */
    private static final String ACCESS_DENIED_MESSAGE = "access_denied";

    /**
     * Cart item not found message translation key.
     */
    private static final String CART_ITEM_NOT_FOUND_MESSAGE = "cartitem_not_found";

    /**
     * Constructor to inject dependencies.
     *
     * @param cartItemRepository     is the cart item repository.
     * @param partnerOfferRepository is the partner offer repository.
     * @param userRepository         is the user repository.
     * @param cartRepository         is the cart repository.
     */
    public CartItemServiceImpl(CartItemRepository cartItemRepository, PartnerOfferRepository partnerOfferRepository,
                               UserRepository userRepository, CartRepository cartRepository) {
        this.cartItemRepository = cartItemRepository;
        this.partnerOfferRepository = partnerOfferRepository;
        this.userRepository = userRepository;
        this.cartRepository = cartRepository;
    }

    /**
     * Deletes a cart item.
     *
     * @param id is the cart item's id.
     */
    @Override
    public void deleteCartItem(final Long id) {
        Optional<CartItem> cartItemOptional = cartItemRepository.findById(id);

        if (cartItemOptional.isPresent()) {
            CartItem cartItem = cartItemOptional.get();

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUserUsername = auth.getName();

            if (!cartItem.getCart().getUser().getUsername().equals(currentUserUsername)) {
                throw new AccessDeniedException(ACCESS_DENIED_MESSAGE);
            }
        } else {
            throw new EntityNotFoundException(CART_ITEM_NOT_FOUND_MESSAGE);
        }

        cartItemRepository.deleteById(id);
    }

    /**
     * Changes the quantity of a cart item.
     *
     * @param id       is the cart item's id.
     * @param quantity is the cart item's quantity.
     */
    @Override
    public void changeQuantity(final Long id, final Integer quantity) {
        Optional<CartItem> cartItemOptional = cartItemRepository.findById(id);

        if (cartItemOptional.isPresent()) {
            CartItem cartItem = cartItemOptional.get();

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUserUsername = auth.getName();

            if (!cartItem.getCart().getUser().getUsername().equals(currentUserUsername)) {
                throw new AccessDeniedException(ACCESS_DENIED_MESSAGE);
            }

            cartItem.setQuantity(quantity);
            cartItemRepository.save(cartItem);
        } else {
            throw new EntityNotFoundException(CART_ITEM_NOT_FOUND_MESSAGE);
        }
    }

    /**
     * Returns a cart item.
     *
     * @param id is the cart item's id.
     * @return <code>CartItem</code>
     */
    @Override
    public CartItem getCartItem(final Long id) {
        Optional<CartItem> cartItemOptional = cartItemRepository.findById(id);

        if (cartItemOptional.isPresent()) {
            CartItem cartItem = cartItemOptional.get();

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUserUsername = auth.getName();

            if (!cartItem.getCart().getUser().getUsername().equals(currentUserUsername)) {
                throw new AccessDeniedException(ACCESS_DENIED_MESSAGE);
            }

            return cartItem;
        } else {
            throw new EntityNotFoundException(CART_ITEM_NOT_FOUND_MESSAGE);
        }
    }

    /**
     * Returns all the cart items.
     *
     * @param username is the user's username.
     * @param pageable is the page object.
     *
     * @return <code>Set of CartItem</code>
     */
    @Override
    public Page<CartItem> getCartItems(String username, Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUserUsername = auth.getName();

        if (!username.equals(currentUserUsername)) {
            throw new AccessDeniedException(ACCESS_DENIED_MESSAGE);
        }

        return cartItemRepository.findAllByCartUserUsername(username, pageable);
    }

    /**
     * Calculates the total price of the cart.
     *
     * @param username is the user whose cart to use.
     *
     * @return the total cart price.
     */
    @Override
    public double getTotalCartPrice(String username) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUserUsername = auth.getName();

        if (!username.equals(currentUserUsername)) {
            throw new AccessDeniedException("access_denied");
        }


        java.math.BigDecimal total = cartItemRepository.getTotalPriceByUsername(username);
        return total != null ? total.doubleValue() : 0.0;
    }

    /**
     * Saves cart item.
     *
     * @param cartItem is the cart item.
     */
    @Override
    public void save(final CartItem cartItem) {
        cartItemRepository.save(cartItem);
    }

    /**
     * Adds an offer-based product to the user's cart.
     *
     * @param offerId  is the partner offer's id.
     * @param quantity is the quantity to add.
     * @return the created or updated <code>CartItem</code>
     */
    @Override
    public CartItem addOfferToCart(final Long offerId, final int quantity) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        PartnerOffer offer = partnerOfferRepository.findById(offerId)
                .orElseThrow(() -> new EntityNotFoundException("Offer not found"));

        if (offer.getStatus() != PartnerOfferStatus.APPROVED) {
            throw new IllegalArgumentException("Offer is not approved");
        }

        Product product = offer.getProduct();
        if (!product.getActive()) {
            throw new IllegalArgumentException("Product is not active");
        }

        int availableStock = offer.getOnHandQuantity() - offer.getReservedQuantity();
        if (availableStock < quantity) {
            throw new IllegalArgumentException("Insufficient stock");
        }

        Cart cart = user.getCart();
        if (cart == null) {
            cart = new Cart();
            cart = cartRepository.save(cart);
            user.setCart(cart);
            userRepository.save(user);
        }

        Optional<CartItem> existingItem = cartItemRepository
                .findByCartIdAndProductIdAndOfferId(cart.getId(), product.getId(), offerId);
        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + quantity);
            return cartItemRepository.save(item);
        }

        CartItem cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.setProduct(product);
        cartItem.setOfferId(offerId);
        cartItem.setPartnerId(offer.getPartner().getId());
        cartItem.setName(product.getName());
        cartItem.setPrice(offer.getPrice());
        cartItem.setQuantity(quantity);

        return cartItemRepository.save(cartItem);
    }
}
