package com.yashmerino.ecommerce.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yashmerino.ecommerce.exceptions.ConflictException;
import com.yashmerino.ecommerce.exceptions.InsufficientPointsException;
import com.yashmerino.ecommerce.exceptions.InsufficientStockException;
import com.yashmerino.ecommerce.kafka.events.PaymentResultEventV2;
import com.yashmerino.ecommerce.kafka.events.PaymentReviewRequiredEventV2;
import com.yashmerino.ecommerce.model.Order;
import com.yashmerino.ecommerce.model.Payment;
import com.yashmerino.ecommerce.model.User;
import com.yashmerino.ecommerce.model.dto.CancelRequestDTO;
import com.yashmerino.ecommerce.model.dto.CheckoutRequestDTO;
import com.yashmerino.ecommerce.model.dto.CheckoutResponseDTO;
import com.yashmerino.ecommerce.model.dto.PaymentInitiationRequestDTO;
import com.yashmerino.ecommerce.model.dto.PaymentInitiationResponseDTO;
import com.yashmerino.ecommerce.repositories.OrderRepository;
import com.yashmerino.ecommerce.repositories.PaymentRepository;
import com.yashmerino.ecommerce.services.interfaces.CommissionService;
import com.yashmerino.ecommerce.services.interfaces.UserService;
import com.yashmerino.ecommerce.utils.OrderStatus;
import com.yashmerino.ecommerce.utils.PaymentStatus;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckoutService {
    private static final BigDecimal POINT_VALUE = new BigDecimal("1.00");
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final JdbcTemplate jdbc;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final UserService users;
    private final ObjectMapper objectMapper;
    private final InboxService inboxService;
    private final CommissionService commissionService;
    private final OutboxService outboxService;

    @Value("${checkout.reservation-ttl:PT15M}")
    private Duration reservationTtl;

    @Transactional
    public CheckoutResponseDTO checkout(UUID idempotencyKey, CheckoutRequestDTO request) {
        User user = currentUser();
        String currency = normalizedCurrency(request.currency());
        String requestHash = hash(request.requestedPoints() + "|" + normalizedCoupon(request.couponCode()) + "|" + currency);
        jdbc.update("INSERT INTO checkout_requests(user_id,idempotency_key,request_hash,status) VALUES (?,?,?,'IN_PROGRESS') " +
                        "ON DUPLICATE KEY UPDATE id=id", user.getId(), idempotencyKey.toString(), requestHash);
        RequestRow stored = jdbc.queryForObject("SELECT request_hash,status,response_snapshot FROM checkout_requests " +
                        "WHERE user_id=? AND idempotency_key=? FOR UPDATE",
                (rs, n) -> new RequestRow(rs.getString(1), rs.getString(2), rs.getString(3)),
                user.getId(), idempotencyKey.toString());
        if (!requestHash.equals(stored.hash())) throw new ConflictException("idempotency_key_payload_mismatch");
        if ("COMPLETED".equals(stored.status())) return read(stored.response(), CheckoutResponseDTO.class);

        List<CartLine> lines = jdbc.query(
                "SELECT ci.product_id," +
                "       COALESCE(p.name,'') AS name," +
                "       COALESCE(po.price,p.price) AS price," +
                "       ci.quantity," +
                "       CASE WHEN ci.offer_id IS NOT NULL THEN po.on_hand_quantity ELSE p.on_hand_quantity END AS on_hand," +
                "       CASE WHEN ci.offer_id IS NOT NULL THEN po.reserved_quantity ELSE p.reserved_quantity END AS reserved," +
                "       CASE WHEN ci.offer_id IS NOT NULL THEN (po.status='APPROVED' AND p.active AND pr.status='APPROVED') ELSE p.active END AS active," +
                "       ci.offer_id," +
                "       po.partner_id AS partner_id," +
                "       po.partner_sku AS partner_sku," +
                "       COALESCE(pr.business_name, pr.name) AS partner_name," +
                "       po.product_id AS offer_product_id " +
                "FROM cart_items ci " +
                "JOIN products p ON p.id=ci.product_id " +
                "LEFT JOIN partner_offers po ON po.id=ci.offer_id " +
                "LEFT JOIN partners pr ON pr.id=po.partner_id " +
                "WHERE ci.cart_id=? ORDER BY ci.id FOR UPDATE",
                (rs, n) -> new CartLine(
                        rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), rs.getInt(4),
                        rs.getInt(5), rs.getInt(6), rs.getBoolean(7),
                        rs.getObject(8, Long.class), rs.getObject(9, Long.class),
                        rs.getString(10), rs.getString(11), rs.getObject(12, Long.class)),
                user.getCart().getId());
        for (CartLine line : lines) {
            if (line.offerId() != null) {
                if (line.partnerId() == null) {
                    throw new ConflictException("offer_partner_not_found");
                }
                if (line.offerProductId() != null && !line.offerProductId().equals(line.productId())) {
                    throw new ConflictException("offer_product_mismatch");
                }
            }
        }
        if (lines.isEmpty()) throw new ConflictException("cart_is_empty");
        lines.sort((a, b) -> {
            Long ao = a.offerId(), bo = b.offerId();
            if (ao == null && bo == null) return 0;
            if (ao == null) return 1;
            if (bo == null) return -1;
            return ao.compareTo(bo);
        });
        for (CartLine line : lines) {
            if (!line.active() || line.quantity() <= 0 || line.onHand() - line.reserved() < line.quantity()) {
                throw new InsufficientStockException();
            }
        }

        BigDecimal subtotal = lines.stream().map(l -> money(l.price().multiply(BigDecimal.valueOf(l.quantity()))))
                .reduce(ZERO, BigDecimal::add);
        Promotion promotion = claimPromotion(request.couponCode(), currency, user.getId());
        BigDecimal couponDiscount = promotion == null ? ZERO : promotion.discount().min(subtotal);
        BigDecimal pointValue = POINT_VALUE.multiply(BigDecimal.valueOf(request.requestedPoints())).setScale(2, RoundingMode.HALF_UP);
        BigDecimal qualifyingBeforePoints = subtotal.subtract(couponDiscount).max(ZERO);
        if (pointValue.compareTo(qualifyingBeforePoints) > 0) throw new InsufficientPointsException();
        MoneyCalculator.Totals totals = MoneyCalculator.calculate(subtotal, ZERO, ZERO, couponDiscount, pointValue, ZERO);
        BigDecimal total = totals.grandTotal();
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plus(reservationTtl);

        Order order = new Order();
        order.setUser(user); order.setSubtotal(subtotal); order.setCouponDiscount(couponDiscount);
        order.setRedeemedPointValue(pointValue); order.setTotalAmount(total); order.setCurrency(currency);
        order.setReservationExpiresAt(expiresAt); order.setStatus(OrderStatus.CREATED);
        order = orders.saveAndFlush(order);

        for (CartLine line : lines) {
            BigDecimal lineTotal = money(line.price().multiply(BigDecimal.valueOf(line.quantity())));
            BigDecimal qualifying = lineTotal;
            jdbc.update("INSERT INTO order_items(order_id,product_id,offer_id,partner_id,name,unit_price,quantity,line_total,qualifying_amount,is_gift,currency,partner_name,partner_sku) VALUES (?,?,?,?,?,?,?,?,?,false,?,?,?)",
                    order.getId(), line.productId(), line.offerId(), line.partnerId(), line.name(),
                    money(line.price()), line.quantity(), lineTotal, qualifying, currency, line.partnerName(), line.partnerSku());
            if (line.offerId() != null) {
                int changed = jdbc.update(
                        "UPDATE partner_offers SET reserved_quantity=reserved_quantity+?,version=version+1 " +
                        "WHERE id=? AND status='APPROVED' AND on_hand_quantity-reserved_quantity>=?",
                        line.quantity(), line.offerId(), line.quantity());
                if (changed != 1) throw new InsufficientStockException();
            } else {
                int changed = jdbc.update("UPDATE products SET reserved_quantity=reserved_quantity+?,version=version+1 " +
                                "WHERE id=? AND active=true AND on_hand_quantity-reserved_quantity>=?",
                        line.quantity(), line.productId(), line.quantity());
                if (changed != 1) throw new InsufficientStockException();
            }
            String sourceType = line.offerId() != null ? "OFFER" : "PRODUCT";
            jdbc.update("INSERT INTO inventory_reservations(product_id,order_id,quantity,status,expires_at,inventory_source_type,offer_id,idempotency_key) VALUES (?,?,?,'RESERVED',?,?,?,?)",
                    line.productId(), order.getId(), line.quantity(), expiresAt,
                    sourceType, line.offerId(),
                    "inventory:reserve:" + order.getId() + ":" + line.productId() + ":" + (line.offerId() != null ? line.offerId() : "0"));
        }
        if (promotion != null) {
            jdbc.update("INSERT INTO promotion_reservations(promotion_id,user_id,order_id,discount_amount,status,expires_at,idempotency_key) VALUES (?,?,?,?,'RESERVED',?,?)",
                    promotion.id(), user.getId(), order.getId(), couponDiscount, expiresAt, "promotion:reserve:" + promotion.id() + ":" + order.getId());
        }
        if (request.requestedPoints() > 0) reservePoints(user.getId(), order.getId(), request.requestedPoints(), pointValue, currency, expiresAt);

        createPartnerOrdersAtCheckout(order.getId());

        Payment payment = new Payment(order, total, PaymentStatus.AWAITING_PAYMENT_METHOD);
        payment.setCurrency(currency);
        payment = payments.saveAndFlush(payment);
        CheckoutResponseDTO response = new CheckoutResponseDTO(order.getId(), payment.getId(), total, currency,
                payment.getStatus(), expiresAt.toInstant(ZoneOffset.UTC));
        jdbc.update("UPDATE checkout_requests SET order_id=?,response_snapshot=?,status='COMPLETED',updated_at=CURRENT_TIMESTAMP(6) WHERE user_id=? AND idempotency_key=?",
                order.getId(), write(response), user.getId(), idempotencyKey.toString());
        return response;
    }

    @Transactional
    public PaymentInitiationResponseDTO initiate(Long paymentId, UUID idempotencyKey, PaymentInitiationRequestDTO request) {
        User user = currentUser();
        String requestHash = hash(request.paymentMethodId());
        jdbc.update("INSERT INTO payment_initiation_requests(user_id,payment_id,idempotency_key,request_hash,status) VALUES (?,?,?,?,'IN_PROGRESS') " +
                        "ON DUPLICATE KEY UPDATE id=id", user.getId(), paymentId, idempotencyKey.toString(), requestHash);
        RequestRow stored = jdbc.queryForObject("SELECT request_hash,status,response_snapshot FROM payment_initiation_requests " +
                        "WHERE user_id=? AND idempotency_key=? FOR UPDATE",
                (rs, n) -> new RequestRow(rs.getString(1), rs.getString(2), rs.getString(3)), user.getId(), idempotencyKey.toString());
        if (!requestHash.equals(stored.hash())) throw new ConflictException("idempotency_key_payload_mismatch");
        if ("COMPLETED".equals(stored.status())) return read(stored.response(), PaymentInitiationResponseDTO.class);

        PaymentLock row = jdbc.query("SELECT p.id,p.amount,p.currency,p.status,o.id,o.status,o.user_id,o.reservation_expires_at " +
                        "FROM payments p JOIN orders o ON o.id=p.order_id WHERE p.id=? FOR UPDATE",
                rs -> rs.next() ? new PaymentLock(rs.getLong(1), rs.getBigDecimal(2), rs.getString(3), rs.getString(4),
                        rs.getLong(5), rs.getString(6), rs.getLong(7), rs.getTimestamp(8).toLocalDateTime()) : null, paymentId);
        if (row == null) throw new EntityNotFoundException("payment_not_found");
        if (!user.getId().equals(row.userId())) throw new AccessDeniedException("payment_not_owned");
        if (!PaymentStatus.AWAITING_PAYMENT_METHOD.name().equals(row.paymentStatus()) || !OrderStatus.CREATED.name().equals(row.orderStatus()))
            throw new ConflictException("payment_state_not_initiatable");
        if (!row.expiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) throw new ConflictException("checkout_reservation_expired");

        jdbc.update("UPDATE payments SET status='PENDING',version=version+1 WHERE id=? AND status='AWAITING_PAYMENT_METHOD'", paymentId);
        jdbc.update("UPDATE orders SET status='PAYMENT_PENDING',payment_deadline=?,version=version+1 WHERE id=? AND status='CREATED'",
                LocalDateTime.now(ZoneOffset.UTC).plus(Duration.ofMinutes(15)), row.orderId());
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.ofEntries(
                Map.entry("eventId", eventId), Map.entry("eventType", "PAYMENT_REQUESTED"), Map.entry("eventVersion", 2),
                Map.entry("occurredAt", Instant.now().toString()), Map.entry("correlationId", eventId),
                Map.entry("aggregateId", paymentId), Map.entry("producer", "main-server"),
                Map.entry("idempotencyKey", "payment-request:" + paymentId), Map.entry("paymentId", paymentId),
                Map.entry("orderId", row.orderId()), Map.entry("amount", row.amount().toPlainString()),
                Map.entry("currency", row.currency()), Map.entry("paymentMethodId", request.paymentMethodId()));
        jdbc.update("INSERT INTO outbox_events(event_id,aggregate_type,aggregate_id,event_type,event_version,topic,event_key,payload,idempotency_key,status) " +
                        "VALUES (?,'PAYMENT',?,'PAYMENT_REQUESTED',2,'payment.requested.v2',?,?,?,'PENDING')",
                eventId, paymentId.toString(), paymentId.toString(), write(payload), "payment-request:" + paymentId);
        PaymentInitiationResponseDTO response = new PaymentInitiationResponseDTO(row.orderId(), paymentId, money(row.amount()), row.currency(), PaymentStatus.PENDING);
        jdbc.update("UPDATE payment_initiation_requests SET response_snapshot=?,status='COMPLETED',updated_at=CURRENT_TIMESTAMP(6) WHERE user_id=? AND idempotency_key=?",
                write(response), user.getId(), idempotencyKey.toString());
        return response;
    }

    @Transactional
    public void cancelOrder(Long orderId, UUID idempotencyKey, CancelRequestDTO request) {
        User user = currentUser();
        CancelLock row = jdbc.query("SELECT o.id,o.status,o.user_id,p.id,p.status " +
                        "FROM orders o JOIN payments p ON p.order_id=o.id WHERE o.id=? FOR UPDATE",
                rs -> rs.next() ? new CancelLock(rs.getLong(1), rs.getString(2), rs.getLong(3),
                        rs.getLong(4), rs.getString(5)) : null, orderId);
        if (row == null) throw new EntityNotFoundException("order_not_found");
        if (!user.getId().equals(row.userId())) throw new AccessDeniedException("order_not_owned");
        if (OrderStatus.CANCELLED.name().equals(row.orderStatus())) return;
        if (!OrderStatus.CREATED.name().equals(row.orderStatus()) ||
                !PaymentStatus.AWAITING_PAYMENT_METHOD.name().equals(row.paymentStatus()))
            throw new ConflictException("order_state_not_cancellable");

        int paymentChanged = jdbc.update("UPDATE payments SET status='CANCELLED',version=version+1 WHERE id=? AND status='AWAITING_PAYMENT_METHOD'", row.paymentId());
        if (paymentChanged != 1) throw new ConflictException("payment_state_changed");

        jdbc.query("SELECT product_id,offer_id,quantity,inventory_source_type FROM inventory_reservations WHERE order_id=? AND status='RESERVED' FOR UPDATE",
                rs -> {
                    while (rs.next()) {
                        String sourceType = rs.getString("inventory_source_type");
                        int qty = rs.getInt("quantity");
                        if ("OFFER".equals(sourceType)) {
                            long offerId = rs.getLong("offer_id");
                            jdbc.update("UPDATE partner_offers SET reserved_quantity=reserved_quantity-?,version=version+1 WHERE id=? AND reserved_quantity>=?",
                                    qty, offerId, qty);
                        } else {
                            long productId = rs.getLong("product_id");
                            jdbc.update("UPDATE products SET reserved_quantity=reserved_quantity-?,version=version+1 WHERE id=? AND reserved_quantity>=?",
                                    qty, productId, qty);
                        }
                    }
                    return null;
                }, orderId);
        jdbc.update("UPDATE inventory_reservations SET status='EXPIRED',version=version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE order_id=? AND status='RESERVED'", orderId);

        jdbc.query("SELECT id,promotion_id,user_id FROM promotion_reservations WHERE order_id=? AND status='RESERVED' FOR UPDATE", rs -> {
            while (rs.next()) {
                long reservationId = rs.getLong(1), promotionId = rs.getLong(2), promoUserId = rs.getLong(3);
                jdbc.update("UPDATE promotions SET remaining_usage=CASE WHEN remaining_usage IS NULL THEN NULL ELSE remaining_usage+1 END,version=version+1 WHERE id=?", promotionId);
                jdbc.update("UPDATE promotion_usage_counters SET reserved_orders=reserved_orders-1,version=version+1 WHERE promotion_id=? AND user_id=? AND reserved_orders>0", promotionId, promoUserId);
                jdbc.update("UPDATE promotion_reservations SET status='EXPIRED',version=version+1 WHERE id=? AND status='RESERVED'", reservationId);
            }
            return null;
        }, orderId);

        jdbc.query("SELECT id,account_id,total_points,currency FROM point_reservations WHERE order_id=? AND status='PENDING_PAYMENT' FOR UPDATE", rs -> {
            if (!rs.next()) return null;
            long reservationId = rs.getLong(1), accountId = rs.getLong(2); int total = rs.getInt(3); String currency = rs.getString(4);
            int returned = jdbc.queryForObject("SELECT COALESCE(SUM(a.reserved_points),0) FROM point_reservation_allocations a JOIN point_lots l ON l.id=a.point_lot_id WHERE a.reservation_id=? AND l.expires_at>CURRENT_TIMESTAMP(6)", Integer.class, reservationId);
            jdbc.update("UPDATE point_lots l JOIN point_reservation_allocations a ON a.point_lot_id=l.id SET l.remaining_points=l.remaining_points+a.reserved_points,l.version=l.version+1 WHERE a.reservation_id=? AND l.expires_at>CURRENT_TIMESTAMP(6)", reservationId);
            jdbc.query("SELECT a.point_lot_id,a.reserved_points FROM point_reservation_allocations a JOIN point_lots l ON l.id=a.point_lot_id WHERE a.reservation_id=? AND l.expires_at<=CURRENT_TIMESTAMP(6)", expired -> {
                while (expired.next()) jdbc.update("INSERT IGNORE INTO loyalty_transactions(account_id,order_id,reservation_id,point_lot_id,transaction_type,points,value,currency,balance_after,idempotency_key) SELECT ?,?,?,?,'EXPIRED',?,0,?,available_points,? FROM loyalty_accounts WHERE id=?",
                        accountId, orderId, reservationId, expired.getLong(1), -expired.getInt(2), currency,
                        "loyalty:expired:" + expired.getLong(1) + ":" + reservationId, accountId);
                return null;
            }, reservationId);
            jdbc.update("UPDATE loyalty_accounts SET available_points=available_points+?,reserved_points=reserved_points-?,version=version+1 WHERE id=? AND reserved_points>=?", returned, total, accountId, total);
            String status = returned == 0 ? "EXPIRED" : "RELEASED";
            jdbc.update("UPDATE point_reservations SET status=?,version=version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND status='PENDING_PAYMENT'", status, reservationId);
            if (returned > 0) jdbc.update("INSERT INTO loyalty_transactions(account_id,order_id,reservation_id,transaction_type,points,value,currency,balance_after,idempotency_key) SELECT ?,?,?,'RELEASED',?,0,?,available_points,? FROM loyalty_accounts WHERE id=?",
                    accountId, orderId, reservationId, returned, currency, "loyalty:released:" + reservationId, accountId);
            return null;
        }, orderId);

        cancelAwaitingPartnerOrders(orderId);

        int orderChanged = jdbc.update("UPDATE orders SET status='CANCELLED',version=version+1 WHERE id=? AND status='CREATED'", orderId);
        if (orderChanged != 1) throw new ConflictException("order_state_changed");
    }

    private Promotion claimPromotion(String coupon, String currency, Long userId) {
        if (coupon == null || coupon.isBlank()) return null;
        Promotion p = jdbc.query("SELECT id,discount_amount FROM promotions WHERE code=? AND currency=? AND status='ACTIVE' " +
                        "AND start_at<=CURRENT_TIMESTAMP(6) AND end_at>CURRENT_TIMESTAMP(6) FOR UPDATE",
                rs -> rs.next() ? new Promotion(rs.getLong(1), rs.getBigDecimal(2)) : null,
                coupon.toUpperCase(Locale.ROOT), currency);
        if (p == null) throw new ConflictException("coupon_not_available");
        int changed = jdbc.update("UPDATE promotions SET remaining_usage=CASE WHEN remaining_usage IS NULL THEN NULL ELSE remaining_usage-1 END,version=version+1 " +
                "WHERE id=? AND (remaining_usage IS NULL OR remaining_usage>0)", p.id());
        if (changed != 1) throw new ConflictException("coupon_usage_exhausted");
        jdbc.update("INSERT INTO promotion_usage_counters(promotion_id,user_id,reserved_orders,consumed_orders) VALUES (?,?,1,0) " +
                "ON DUPLICATE KEY UPDATE reserved_orders=reserved_orders+1,version=version+1", p.id(), userId);
        return p;
    }

    private void reservePoints(Long userId, Long orderId, int points, BigDecimal value, String currency, LocalDateTime expires) {
        Account account = jdbc.query("SELECT id,available_points,reserved_points FROM loyalty_accounts WHERE user_id=? FOR UPDATE",
                rs -> rs.next() ? new Account(rs.getLong(1), rs.getInt(2), rs.getInt(3)) : null, userId);
        if (account == null || account.available() < points) throw new InsufficientPointsException();
        List<Lot> lots = jdbc.query("SELECT id,remaining_points FROM point_lots WHERE account_id=? AND remaining_points>0 AND expires_at>CURRENT_TIMESTAMP(6) ORDER BY expires_at,id FOR UPDATE",
                (rs, n) -> new Lot(rs.getLong(1), rs.getInt(2)), account.id());
        int remaining = points;
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO point_reservations(account_id,order_id,total_points,total_value,currency,status,expires_at,idempotency_key) VALUES (?,?,?,?,?,'PENDING_PAYMENT',?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, account.id()); ps.setLong(2, orderId); ps.setInt(3, points); ps.setBigDecimal(4, value); ps.setString(5, currency);
            ps.setTimestamp(6, Timestamp.valueOf(expires)); ps.setString(7, "points:reserve:" + orderId); return ps;
        }, keys);
        long reservationId = keys.getKey().longValue();
        for (Lot lot : lots) {
            if (remaining == 0) break;
            int allocated = Math.min(remaining, lot.remaining());
            jdbc.update("UPDATE point_lots SET remaining_points=remaining_points-?,version=version+1 WHERE id=?", allocated, lot.id());
            jdbc.update("INSERT INTO point_reservation_allocations(reservation_id,point_lot_id,reserved_points) VALUES (?,?,?)", reservationId, lot.id(), allocated);
            remaining -= allocated;
        }
        if (remaining != 0) throw new InsufficientPointsException();
        int after = account.available() - points;
        jdbc.update("UPDATE loyalty_accounts SET available_points=?,reserved_points=reserved_points+?,version=version+1 WHERE id=?", after, points, account.id());
        jdbc.update("INSERT INTO loyalty_transactions(account_id,order_id,reservation_id,transaction_type,points,value,currency,balance_after,idempotency_key) VALUES (?,?,?,'RESERVED',?,?,?,?,?)",
                account.id(), orderId, reservationId, -points, value, currency, after, "loyalty:reserved:" + reservationId);
    }

    @Transactional
    public void processPaymentResultV2(PaymentResultEventV2 event) {
        if (inboxService.isAlreadyProcessed("main-server", event.eventId())) {
            return;
        }
        Payment payment = payments.findById(event.paymentId())
            .orElseThrow(() -> new EntityNotFoundException("payment_not_found"));
        Order order = payment.getOrder();
        boolean mismatch = !order.getId().equals(event.orderId())
                || payment.getAmount().compareTo(new BigDecimal(event.amount())) != 0
                || !payment.getCurrency().equals(event.currency())
                || (payment.getExternalPaymentId() != null && event.externalPaymentId() != null
                    && !payment.getExternalPaymentId().equals(event.externalPaymentId()));
        if (mismatch) {
            payments.updateStatusAndVersion(payment.getId(), PaymentStatus.PENDING, PaymentStatus.REVIEW, payment.getVersion());
            orders.updateOrderStatusAndVersion(order.getId(), OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_REVIEW, order.getVersion());
            jdbc.update("INSERT IGNORE INTO operations_alerts(aggregate_type,aggregate_id,alert_type,severity,status,details_redacted,idempotency_key,created_at,updated_at) VALUES ('PAYMENT',?,'RECONCILIATION_MISMATCH','HIGH','OPEN','Payment result did not match snapshot',?,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
                    payment.getId(), "payment-mismatch:" + payment.getId());
            inboxService.markProcessed("main-server", event.eventId(), "PaymentResultEventV2", event.correlationId(), event.aggregateId());
            return;
        }
        if ("SUCCEEDED".equals(event.status())) {
            if (event.externalPaymentId() == null || event.externalPaymentId().isBlank()) throw new ConflictException("external_payment_id_required");
            jdbc.update("UPDATE payments SET external_payment_id=? WHERE id=? AND (external_payment_id IS NULL OR external_payment_id=?)",
                    event.externalPaymentId(), payment.getId(), event.externalPaymentId());
            int paymentUpdated = payments.updateStatusAndVersion(
                payment.getId(), PaymentStatus.PENDING, PaymentStatus.SUCCEEDED, payment.getVersion());
            if (paymentUpdated == 0) throw new OptimisticLockException("Payment version conflict");
            int orderUpdated = orders.updateOrderStatusAndVersion(
                order.getId(), OrderStatus.PAYMENT_PENDING, OrderStatus.PAID, order.getVersion());
            if (orderUpdated == 0) throw new OptimisticLockException("Order version conflict");
            commitReservationsAndEarn(order, payment, event.externalPaymentId());
        } else if ("FAILED".equals(event.status())) {
            int paymentUpdated = payments.updateStatusAndVersion(
                payment.getId(), PaymentStatus.PENDING, PaymentStatus.FAILED, payment.getVersion());
            if (paymentUpdated == 0) throw new OptimisticLockException("Payment version conflict");
            int orderUpdated = orders.updateOrderStatusAndVersion(
                order.getId(), OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_FAILED, order.getVersion());
            if (orderUpdated == 0) throw new OptimisticLockException("Order version conflict");
            releaseReservations(order.getId());
            cancelAwaitingPartnerOrders(order.getId());
        }
        inboxService.markProcessed("main-server", event.eventId(), "PaymentResultEventV2",
            event.correlationId(), event.aggregateId());
    }

    private void commitReservationsAndEarn(Order order, Payment payment, String externalPaymentId) {
        jdbc.query("SELECT product_id,offer_id,quantity,inventory_source_type FROM inventory_reservations WHERE order_id=? AND status='RESERVED' FOR UPDATE", rs -> {
            while (rs.next()) {
                String sourceType = rs.getString("inventory_source_type");
                int qty = rs.getInt("quantity");
                if ("OFFER".equals(sourceType)) {
                    long offerId = rs.getLong("offer_id");
                    int changed = jdbc.update(
                        "UPDATE partner_offers SET on_hand_quantity=on_hand_quantity-?,reserved_quantity=reserved_quantity-?,version=version+1 WHERE id=? AND on_hand_quantity>=? AND reserved_quantity>=?",
                        qty, qty, offerId, qty, qty);
                    if (changed != 1) throw new ConflictException("offer_inventory_commit_conflict");
                } else {
                    long productId = rs.getLong("product_id");
                    int changed = jdbc.update("UPDATE products SET on_hand_quantity=on_hand_quantity-?,reserved_quantity=reserved_quantity-?,version=version+1 WHERE id=? AND on_hand_quantity>=? AND reserved_quantity>=?",
                            qty, qty, productId, qty, qty);
                    if (changed != 1) throw new ConflictException("inventory_commit_conflict");
                }
            }
            return null;
        }, order.getId());
        jdbc.update("UPDATE inventory_reservations SET status='COMMITTED',version=version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE order_id=? AND status='RESERVED'", order.getId());
        activatePartnerOrdersAfterPayment(order.getId());
        jdbc.query("SELECT promotion_id,user_id FROM promotion_reservations WHERE order_id=? AND status='RESERVED' FOR UPDATE", rs -> {
            while (rs.next()) jdbc.update("UPDATE promotion_usage_counters SET reserved_orders=reserved_orders-1,consumed_orders=consumed_orders+1,version=version+1 WHERE promotion_id=? AND user_id=? AND reserved_orders>0", rs.getLong(1), rs.getLong(2));
            return null;
        }, order.getId());
        jdbc.update("UPDATE promotion_reservations SET status='CONSUMED',version=version+1 WHERE order_id=? AND status='RESERVED'", order.getId());
        jdbc.query("SELECT id,account_id,total_points,total_value,currency FROM point_reservations WHERE order_id=? AND status='PENDING_PAYMENT' FOR UPDATE", rs -> {
            if (!rs.next()) return null;
            long reservationId=rs.getLong(1), accountId=rs.getLong(2); int points=rs.getInt(3);
            jdbc.update("UPDATE loyalty_accounts SET reserved_points=reserved_points-?,version=version+1 WHERE id=? AND reserved_points>=?", points, accountId, points);
            jdbc.update("UPDATE point_reservations SET status='CONSUMED',version=version+1 WHERE id=? AND status='PENDING_PAYMENT'", reservationId);
            jdbc.update("INSERT INTO loyalty_transactions(account_id,order_id,reservation_id,transaction_type,points,value,currency,balance_after,idempotency_key) SELECT ?,?,?,'REDEEMED',?, ?,?,available_points,? FROM loyalty_accounts WHERE id=?",
                    accountId,order.getId(),reservationId,-points,rs.getBigDecimal(4),rs.getString(5),"loyalty:redeemed:"+reservationId,accountId);
            return null;
        }, order.getId());
        BigDecimal qualifying = order.getSubtotal().subtract(order.getProductDiscount()).subtract(order.getOrderDiscount())
                .subtract(order.getCouponDiscount()).subtract(order.getRedeemedPointValue()).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        int earned = qualifying.setScale(0, RoundingMode.DOWN).intValueExact();
        Long userId = order.getUser().getId();
        jdbc.update("INSERT INTO loyalty_accounts(user_id) VALUES (?) ON DUPLICATE KEY UPDATE user_id=user_id", userId);
        Long accountId = jdbc.queryForObject("SELECT id FROM loyalty_accounts WHERE user_id=? FOR UPDATE", Long.class, userId);
        int debt = jdbc.queryForObject("SELECT loyalty_debt FROM loyalty_accounts WHERE id=?", Integer.class, accountId);
        int debtPaid = Math.min(debt, earned), credited = earned-debtPaid;
        if (credited > 0) jdbc.update("INSERT INTO point_lots(account_id,source_order_id,original_points,remaining_points,expires_at,lot_type) VALUES (?,?,?,?,DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 1 YEAR),'EARNED')",
                accountId,order.getId(),credited,credited);
        jdbc.update("UPDATE loyalty_accounts SET available_points=available_points+?,lifetime_points=lifetime_points+?,loyalty_debt=loyalty_debt-?,version=version+1 WHERE id=?", credited,earned,debtPaid,accountId);
        if (credited > 0) {
            Long lotId=jdbc.queryForObject("SELECT id FROM point_lots WHERE source_order_id=? AND lot_type='EARNED'",Long.class,order.getId());
            jdbc.update("INSERT INTO loyalty_transactions(account_id,order_id,point_lot_id,transaction_type,points,value,currency,balance_after,idempotency_key) SELECT ?,?,?,'EARNED',?,0,?,available_points,? FROM loyalty_accounts WHERE id=?",
                    accountId,order.getId(),lotId,credited,order.getCurrency(),"loyalty:earned:"+lotId+":"+order.getId(),accountId);
        }
        jdbc.update("INSERT INTO spend_ledger(user_id,order_id,amount,currency,transaction_type,external_reference,idempotency_key) VALUES (?,?,?,?,'QUALIFIED_SPEND',?,?)",
                userId,order.getId(),qualifying,order.getCurrency(),externalPaymentId,"spend:qualified:"+order.getId());
    }

    private void createPartnerOrdersAtCheckout(Long orderId) {
        List<CommissionService.CommissionRequest> requests = jdbc.query(
                "SELECT oi.product_id,oi.offer_id,oi.partner_id,oi.line_total,o.currency," +
                "       (SELECT GROUP_CONCAT(DISTINCT pc.categories_id ORDER BY pc.categories_id) FROM products_categories pc WHERE pc.product_id=oi.product_id) AS category_ids " +
                "FROM order_items oi " +
                "JOIN orders o ON o.id=oi.order_id " +
                "WHERE oi.order_id=? AND oi.partner_id IS NOT NULL FOR UPDATE",
                (rs, n) -> {
                    String catIds = rs.getString("category_ids");
                    Set<Long> categories = new java.util.HashSet<>();
                    if (catIds != null && !catIds.isEmpty()) {
                        for (String c : catIds.split(",")) {
                            categories.add(Long.valueOf(c.trim()));
                        }
                    }
                    return new CommissionService.CommissionRequest(
                            rs.getLong("product_id"),
                            rs.getObject("offer_id", Long.class),
                            categories,
                            rs.getLong("partner_id"),
                            rs.getBigDecimal("line_total"),
                            rs.getString("currency"));
                },
                orderId);

        List<CommissionService.CommissionResult> results = commissionService.resolveOrderItemCommissions(requests);

        for (CommissionService.CommissionResult r : results) {
            jdbc.update("UPDATE order_items SET " +
                            "commission_rule_id=?,commission_rate=?,commission_fixed_fee=?," +
                            "commission_amount=?,partner_payable_amount=? " +
                            "WHERE order_id=? AND product_id=? AND (offer_id <=> ?)",
                    r.commissionRuleId(), r.rate(), r.fixedFee(),
                    r.commissionAmount(), r.partnerPayable(),
                    orderId, r.productId(), r.offerId());
        }

        jdbc.query("SELECT partner_id,SUM(line_total) AS subtotal," +
                    "SUM(commission_amount) AS total_commission," +
                    "SUM(partner_payable_amount) AS total_payable,currency " +
                    "FROM order_items WHERE order_id=? AND partner_id IS NOT NULL " +
                    "GROUP BY partner_id, currency FOR UPDATE",
                rs -> {
                    while (rs.next()) {
                        Long partnerId = rs.getLong("partner_id");
                        BigDecimal subtotal = rs.getBigDecimal("subtotal");
                        BigDecimal commissionAmount = rs.getBigDecimal("total_commission");
                        BigDecimal payable = rs.getBigDecimal("total_payable");
                        String currency = rs.getString("currency");

                        jdbc.update("INSERT INTO partner_orders(order_id,partner_id,status,subtotal,discount_allocation,shipping_allocation,commission_amount,partner_payable_amount,currency,settlement_status,version,created_at,updated_at) " +
                                    "VALUES (?,?,'AWAITING_PAYMENT',?,0,0,?,?,?,?,'UNSETTLED',0,NOW(),NOW())",
                                orderId, partnerId, subtotal, commissionAmount, payable, currency);
                    }
                    return null;
                }, orderId);

        jdbc.update("UPDATE order_items oi " +
                    "JOIN partner_orders po ON po.order_id=oi.order_id AND po.partner_id=oi.partner_id " +
                    "SET oi.partner_order_id=po.id " +
                    "WHERE oi.order_id=?", orderId);
    }

    private void activatePartnerOrdersAfterPayment(Long orderId) {
        jdbc.update("UPDATE partner_orders SET status='NEW',updated_at=NOW() " +
                    "WHERE order_id=? AND status='AWAITING_PAYMENT'", orderId);
        List<Long> poIds = jdbc.query("SELECT id FROM partner_orders WHERE order_id=? AND status='NEW'",
                (rs, n) -> rs.getLong(1), orderId);
        for (Long poId : poIds) {
            outboxService.saveOutboxEvent(UUID.randomUUID().toString(), "PARTNER_ORDER", poId,
                    "PARTNER_ORDER_ACTIVATED", "partner.order.activated", poId.toString(),
                    Map.of("partnerOrderId", poId, "orderId", orderId, "status", "NEW"),
                    "partner-order:activate:" + poId);
        }
    }

    private void cancelAwaitingPartnerOrders(Long orderId) {
        List<Long> poIds = jdbc.query("SELECT id FROM partner_orders WHERE order_id=? AND status='AWAITING_PAYMENT'",
                (rs, n) -> rs.getLong(1), orderId);
        jdbc.update("UPDATE partner_orders SET status='CANCELLED',cancelled_at=NOW(),cancel_reason='payment_failed_or_expired',updated_at=NOW() " +
                    "WHERE order_id=? AND status='AWAITING_PAYMENT'", orderId);
        for (Long poId : poIds) {
            outboxService.saveOutboxEvent(UUID.randomUUID().toString(), "PARTNER_ORDER", poId,
                    "PARTNER_ORDER_CANCELLED", "partner.order.cancelled", poId.toString(),
                    Map.of("partnerOrderId", poId, "orderId", orderId, "status", "CANCELLED", "reason", "payment_failed_or_expired"),
                    "partner-order:cancel:" + poId + ":payment");
        }
    }

    private void releaseReservations(Long orderId) {
        jdbc.query("SELECT product_id,offer_id,quantity,inventory_source_type FROM inventory_reservations WHERE order_id=? AND status='RESERVED' FOR UPDATE",
                rs->{while(rs.next()){String st=rs.getString("inventory_source_type");int q=rs.getInt("quantity");if("OFFER".equals(st)){long oid=rs.getLong("offer_id");jdbc.update("UPDATE partner_offers SET reserved_quantity=reserved_quantity-?,version=version+1 WHERE id=? AND reserved_quantity>=?",q,oid,q);}else{long pid=rs.getLong("product_id");jdbc.update("UPDATE products SET reserved_quantity=reserved_quantity-?,version=version+1 WHERE id=? AND reserved_quantity>=?",q,pid,q);}}return null;},orderId);
        jdbc.update("UPDATE inventory_reservations SET status='RELEASED',version=version+1 WHERE order_id=? AND status='RESERVED'",orderId);
        jdbc.query("SELECT promotion_id,user_id FROM promotion_reservations WHERE order_id=? AND status='RESERVED' FOR UPDATE",rs->{while(rs.next()){jdbc.update("UPDATE promotions SET remaining_usage=CASE WHEN remaining_usage IS NULL THEN NULL ELSE remaining_usage+1 END,version=version+1 WHERE id=?",rs.getLong(1));jdbc.update("UPDATE promotion_usage_counters SET reserved_orders=reserved_orders-1,version=version+1 WHERE promotion_id=? AND user_id=? AND reserved_orders>0",rs.getLong(1),rs.getLong(2));}return null;},orderId);
        jdbc.update("UPDATE promotion_reservations SET status='RELEASED',version=version+1 WHERE order_id=? AND status='RESERVED'",orderId);
        jdbc.query("SELECT id,account_id,total_points,currency FROM point_reservations WHERE order_id=? AND status='PENDING_PAYMENT' FOR UPDATE",rs->{if(!rs.next())return null;long rid=rs.getLong(1),aid=rs.getLong(2);int total=rs.getInt(3);String currency=rs.getString(4);int returned=jdbc.queryForObject("SELECT COALESCE(SUM(a.reserved_points),0) FROM point_reservation_allocations a JOIN point_lots l ON l.id=a.point_lot_id WHERE a.reservation_id=? AND l.expires_at>CURRENT_TIMESTAMP(6)",Integer.class,rid);jdbc.update("UPDATE point_lots l JOIN point_reservation_allocations a ON a.point_lot_id=l.id SET l.remaining_points=l.remaining_points+a.reserved_points,l.version=l.version+1 WHERE a.reservation_id=? AND l.expires_at>CURRENT_TIMESTAMP(6)",rid);jdbc.update("UPDATE loyalty_accounts SET available_points=available_points+?,reserved_points=reserved_points-?,version=version+1 WHERE id=? AND reserved_points>=?",returned,total,aid,total);jdbc.update("UPDATE point_reservations SET status=?,version=version+1 WHERE id=? AND status='PENDING_PAYMENT'",returned==0?"EXPIRED":"RELEASED",rid);if(returned>0)jdbc.update("INSERT INTO loyalty_transactions(account_id,order_id,reservation_id,transaction_type,points,value,currency,balance_after,idempotency_key) SELECT ?,?,?,'RELEASED',?,0,?,available_points,? FROM loyalty_accounts WHERE id=?",aid,orderId,rid,returned,currency,"loyalty:released:"+rid,aid);return null;},orderId);
    }

    @Transactional
    public void handlePaymentReview(PaymentReviewRequiredEventV2 event) {
        if (inboxService.isAlreadyProcessed("main-server", event.eventId())) {
            return;
        }
        Payment payment = payments.findById(event.paymentId())
            .orElseThrow(() -> new EntityNotFoundException("payment_not_found"));
        Order order = payment.getOrder();
        int paymentUpdated = payments.updateStatusAndVersion(
            payment.getId(), PaymentStatus.PENDING, PaymentStatus.REVIEW, payment.getVersion());
        if (paymentUpdated == 0) throw new OptimisticLockException("Payment version conflict");
        int orderUpdated = orders.updateOrderStatusAndVersion(
            order.getId(), OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_REVIEW, order.getVersion());
        if (orderUpdated == 0) throw new OptimisticLockException("Order version conflict");
        inboxService.markProcessed("main-server", event.eventId(), "PaymentReviewRequiredEventV2",
            event.correlationId(), event.aggregateId());
    }

    private User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserDetails details)) throw new AccessDeniedException("authentication_required");
        return users.getByUsername(details.getUsername());
    }
    private String normalizedCurrency(String value) { return value == null ? "EUR" : value.toUpperCase(Locale.ROOT); }
    private String normalizedCoupon(String value) { return value == null ? "" : value.toUpperCase(Locale.ROOT); }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("serialization_failed", e); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return objectMapper.readValue(value, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("stored_response_invalid", e); }
    }

    private record RequestRow(String hash, String status, String response) {}
    private record CartLine(long productId, String name, BigDecimal price, int quantity, int onHand, int reserved, boolean active, Long offerId, Long partnerId, String partnerSku, String partnerName, Long offerProductId) {}
    private record Promotion(long id, BigDecimal discount) {}
    private record Account(long id, int available, int reserved) {}
    private record Lot(long id, int remaining) {}
    private record PaymentLock(long paymentId, BigDecimal amount, String currency, String paymentStatus,
                               long orderId, String orderStatus, Long userId, LocalDateTime expiresAt) {}
    record CancelLock(long orderId, String orderStatus, Long userId, long paymentId, String paymentStatus) {}
}
