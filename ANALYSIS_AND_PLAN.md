# Smart Cart — Phân tích và kế hoạch triển khai

> **Status: READY_FOR_OWNER_DECISIONS**
>
> Chỉ là thiết kế; chưa implementation, chưa tạo source code hoặc migration.
> Repository được đối chiếu ngày 2026-07-11. Base package thực tế: `com.yashmerino.ecommerce`.

## 1. Scope MVP và deferred

MVP gồm authoritative pricing, checkout idempotency, order snapshot, reservation tồn kho/promotion/điểm, payment initiation riêng, Stripe server-confirm flow hiện có được làm bền vững, outbox/inbox theo từng service, full refund, loyalty/spend ledger và Notification Inbox/send operation.

Deferred: partial refund, customer cancel sau payment initiation, Stripe client-secret/`confirmCardPayment`/webhook/SCA, nested promotion groups, multi-currency conversion, return-merchandise workflow đầy đủ và exactly-once delivery.

Nguyên tắc:

- Main Server sở hữu cart/order/pricing/inventory/promotion/loyalty/refund business state. Payment Service sở hữu Stripe operations. Notification Service sở hữu send operations. Không service nào đọc database service khác.
- Service phát critical Kafka event cần local outbox. Service nhận critical event cần local inbox/processed marker. Service không phát event không bắt buộc có outbox.
- DB + Kafka/Stripe/SMTP không tạo exactly-once. Cam kết là at-least-once với idempotency và reconciliation; SMTP timeout có thể không xác định được email đã gửi hay chưa.
- Controller không nhận hoặc tự đặt authoritative price/total/status.

## 2. Kiến trúc hiện tại đã xác minh

| Module | Dữ kiện từ source hiện tại |
|---|---|
| `ecommerce-platform-server` | MySQL `ecommerce_platform`; `POST /api/order` nhận `OrderDTO.totalAmount/status`; `POST /api/payment/{orderId}` nhận `PaymentDTO`; gửi trực tiếp `payment.requested`, nhận `payment.result` |
| `ecommerce-platform-payment-service` | MySQL riêng; nhận request; `StripePaymentServiceImpl` gọi `PaymentIntent.create(confirm=true)`, amount × 100, currency hardcode `EUR`; gửi result trực tiếp |
| `ecommerce-platform-notification-service` | MySQL riêng; V1 chỉ có `notifications`; listener `notification.requested` catch-and-swallow; service gửi SMTP trong transaction/retry, chưa có inbox/business key |
| `ecommerce-platform-ui` | Stripe Elements gọi `createPaymentMethod`; gửi `paymentMethod.id` trong field cũ tên `stripeToken`; không gọi `confirmCardPayment` |

Class/package thực tế:

- Main: `model.Product`, `CartItem`, `Order`, `Payment`; `services.OrderServiceImpl`, `PaymentServiceImpl`; `controllers.OrderController`, `PaymentController`; `kafka.PaymentEventProducer`, `PaymentEventListener`; `kafka.events.PaymentRequestedEvent`, `PaymentResultEvent`.
- Payment: `model.Payment`; `service.impl.PaymentServiceImpl`, `StripePaymentServiceImpl`; `kafka.PaymentEventListener`, `PaymentResultProducer`; event records tương ứng.
- Notification: `model.Notification`; `service.impl.NotificationServiceImpl`, `EmailNotificationSender`; `kafka.NotificationEventListener`; `kafka.events.NotificationRequestedEvent`.
- Main status: order `CREATED, PAYMENT_PENDING, PAID, PAYMENT_FAILED`; payment `PENDING, SUCCEEDED, FAILED`. Payment Service status `SUCCEEDED, FAILED`. Notification status `PENDING, SENT, FAILED`.
- Main migrations V1–V3; Payment và Notification mỗi module chỉ có V1. Main product/cart item price là `DOUBLE`; order/payment money là `DECIMAL(19,2)`.

Không có checkout endpoint/idempotency, reservations, promotion/loyalty, outbox/inbox, webhook hoặc refund implementation.

## 3. Kiến trúc mục tiêu và API

### 3.1 Checkout — chưa phát payment event

```http
POST /api/orders/checkout
Idempotency-Key: <UUID>
Content-Type: application/json

{"requestedPoints":20,"couponCode":"OPTIONAL","currency":"EUR"}
```

Không nhận price, total, order status hoặc payment status. Main transaction:

1. Authenticate user; insert/lock checkout idempotency record và so request hash.
2. Lock/read cart, product; reprice từ DB.
3. Validate/claim promotion, inventory và point lots theo lock order.
4. Tạo `Order(CREATED)`, immutable `OrderItem` snapshot và ba loại reservation cùng expiry.
5. Tạo `Payment(AWAITING_PAYMENT_METHOD)` với amount/currency snapshot.
6. Lưu response snapshot rồi commit. **Không tạo `PaymentRequestedEventV2` và không ghi PaymentMethod ID.**

```json
{
  "orderId":100,
  "paymentId":200,
  "amount":"100.00",
  "currency":"EUR",
  "paymentStatus":"AWAITING_PAYMENT_METHOD",
  "reservationExpiresAt":"2026-07-11T15:00:00Z"
}
```

### 3.2 Payment initiation — đây mới là nơi phát event

```http
POST /api/payments/{paymentId}/initiate
Idempotency-Key: <UUID>
Content-Type: application/json

{"paymentMethodId":"pm_xxx"}
```

Main transaction:

1. Insert/lock payment initiation idempotency record; authenticate và verify payment/order thuộc user.
2. Lock payment, order và reservation headers; yêu cầu `Payment=AWAITING_PAYMENT_METHOD`, `Order=CREATED`, chưa hết checkout deadline.
3. Verify payment amount/currency bằng immutable order snapshot; không tin request.
4. Conditional/version update Payment `AWAITING_PAYMENT_METHOD -> PENDING`, Order `CREATED -> PAYMENT_PENDING`.
5. Insert Main outbox `PaymentRequestedEventV2`; event payload chứa PaymentMethod ID theo security policy.
6. Store response snapshot đã loại PaymentMethod ID; commit. Publisher phát sau commit.

Response trả `paymentId`, `orderId`, status `PENDING` và cùng amount/currency snapshot; tuyệt đối không echo PaymentMethod ID. HTTP 409 áp dụng khi payment đã expired/cancelled, state không cho phép, hoặc cùng idempotency key khác payload.

Cùng user+Idempotency-Key+cùng canonical payload trả response cũ; cùng key khác payload trả 409. Key khác cho payment đã initiated trả current state, không tạo request/charge mới; business constraint `UNIQUE(payment_id)` trên initiation operation và outbox business key bảo vệ lớp cuối.

Cancel trước initiation dùng `POST /api/orders/{orderId}/cancel` với Idempotency-Key UUID và request body chỉ có reason. Nó chỉ chấp nhận `CREATED/AWAITING_PAYMENT_METHOD`; sau initiation trả conflict/operation-not-supported cho customer trong MVP.

### 3.3 Stripe flow duy nhất

UI tạo PaymentMethod bằng Elements → gọi payment initiation → Main outbox → Payment Service tạo/confirm PaymentIntent server-side với Stripe idempotency key ổn định → Payment Service outbox terminal result → Main inbox. Không trộn `confirmCardPayment`/webhook trong MVP. `requires_action` hoặc network ambiguity đi `UNKNOWN`/reconciliation, không được coi là success/failure.

## 4. Money và công thức điểm

Money Java/DB dùng `BigDecimal`/`DECIMAL(19,2)`. Input money `setScale(2, HALF_UP)`; line base = unit price × integer quantity rồi scale 2 `HALF_UP`; percentage/multiplier lưu scale 4; line discount nhân rồi scale 2 `HALF_UP`. Tổng cộng/trừ scale 2. Không tạo `BigDecimal` từ `double`.

```text
subtotal = Σ(unitPrice × paidQuantity); gift unit không tính

grandTotal = max(0,
  subtotal - productDiscount - orderDiscount - couponDiscount
  - redeemedPointValue + shippingFee)

qualifyingAmount = max(0,
  subtotal - productDiscount - orderDiscount - couponDiscount
  - redeemedPointValue)

earnedPoints = floor((qualifyingAmount / moneyPerPoint) × membershipMultiplier)
```

`redeemedPointValue = points × pointValue`, scale 2 `HALF_UP`, capped bởi qualifying merchandise. Chia với scale 8 `DOWN`, nhân multiplier scale 4 và chỉ làm tròn point một lần ở cuối bằng scale 0 `DOWN`. Không kiếm điểm cho shipping, gift hoặc phần trả bằng điểm.

Ví dụ: subtotal 100.00, discounts 0, redeemed 20.00, shipping 10.00 → grand total 90.00, qualifying 80.00, không phải 70. Với `moneyPerPoint=1.00`, multiplier 1.25 → 100 earned points.

Money API dùng decimal string:

```json
{"amount":"1000000.00","currency":"EUR"}
```

Frontend chỉ format/display; không dùng JavaScript `number` để quyết định total. Stripe minor units phải theo currency metadata, không mặc định mọi currency có hai decimal places.

## 5. Main Server logical schema

### 5.1 Order, payment, idempotency và audit

| Table | Cột/constraint chính |
|---|---|
| `checkout_requests` | `id,user_id,idempotency_key,request_hash,order_id,response_snapshot JSON,status,lease_until,timestamps`; unique `(user_id,idempotency_key)` |
| `payment_initiation_requests` | `id,user_id,payment_id,idempotency_key,request_hash,response_snapshot JSON,status,timestamps`; unique `(user_id,idempotency_key)`, unique `payment_id`; request hash là HMAC/canonical digest để không lưu PaymentMethod ID rõ |
| `orders` | current fields + `subtotal,product_discount,order_discount,coupon_discount,redeemed_point_value,shipping_fee,total_amount,currency,status,reservation_expires_at,payment_deadline,version` |
| `order_items` | `order_id,product_id,name,unit_price,quantity,discount allocations,redeemed allocation,line_total,qualifying_amount,is_gift`; immutable snapshot |
| `payments` | `order_id,amount,currency,status,external_payment_id,version,timestamps`; unique active payment/order policy |
| `refunds` | `id,order_id,payment_id,amount,currency,reason,requested_by,status,request_idempotency_key,external_refund_id,version,timestamps`; unique request key |
| `audit_events` | aggregate/action/before/after/reason/correlation/actor/timestamp; không chứa PaymentMethod ID |
| `operations_alerts` | aggregate/type/severity/status/details-redacted/idempotency_key/timestamps; unique key |

Mọi enum do service set; request DTO không có status.

### 5.2 Inventory

`products` thêm `price DECIMAL(19,2), on_hand_quantity, reserved_quantity, active, version`; `available = on_hand - reserved` là derived, không lưu. Checks nonnegative và reserved ≤ on-hand.

`inventory_reservations(id,product_id,order_id,quantity,status RESERVED|COMMITTED|RELEASED|EXPIRED,expires_at,idempotency_key,version,timestamps)`; unique `(order_id,product_id)` và key, quantity > 0.

Checkout lock products theo ID, tăng reserved. Payment success `RESERVED -> COMMITTED`, giảm on-hand và reserved. Checkout expiry/failure release giảm reserved, không đổi on-hand. Full financial refund **không tự restock**. Admin restock dùng:

`inventory_adjustments(id,product_id,order_id,adjustment_type,quantity,reason,idempotency_key,created_by,created_at)`; unique key. Hàng vật lý chỉ tăng on-hand sau nhận/kiểm tra hoặc admin quyết định; sản phẩm số không restock. Return-merchandise workflow đầy đủ deferred.

### 5.3 Point reservation, immutable allocations và ledger

| Table | Cột/constraint chính |
|---|---|
| `loyalty_accounts` | user unique, available/reserved/lifetime points, tier, `loyalty_debt`, version |
| `point_lots` | account/source order/original/remaining/expires/type/version; remaining không âm |
| `point_reservations` | account/order/total points/value/currency/status `PENDING_PAYMENT|CONSUMED|RELEASED|EXPIRED`, expires/key/version; unique order/key |
| `point_reservation_allocations` | reservation, point lot, reserved points, created; unique `(reservation_id,point_lot_id)`, positive; **immutable, không có status** |
| `loyalty_transactions` | account/order/payment/reservation/lot/type/signed points/value/currency/balance after/idempotency key/created; unique key |

Checkout lock account và non-expired lots ordered `(expires_at,id) FOR UPDATE`, trừ remaining, giảm available, tăng reserved; tạo reservation/allocations và một tổng ledger `RESERVED` trong cùng order transaction.

Ledger rules:

- Một dòng tổng, `point_lot_id=NULL`: `RESERVED`, `REDEEMED`, `RELEASED`, `REDEEMED_REFUNDED`; key `loyalty:<operation>:<reservationId>` (refund return thêm refund business ID nếu một reservation có thể có nhiều reversals).
- Một dòng mỗi lot: `EARNED`, `EARNED_REVERSED`, `EXPIRED`, `ADJUSTMENT`; key `loyalty:<operation>:<lotId>:<businessOperationId>`. Không dùng một key cho nhiều lot.
- `DEBT_CREATED/DEBT_REPAID` key theo debt operation/account; không giả làm lot transaction.

Success conditional reservation `PENDING_PAYMENT -> CONSUMED`, ghi tổng `REDEEMED`, sau đó earned lot/transactions:

```text
reservedPoints  = reservedPoints - redeemedPoints
availablePoints = availablePoints + earnedPointsAfterDebt
lifetimePoints  = lifetimePoints + grossEarnedPoints
```

Release đọc immutable allocations. Lot còn hạn: cộng lại đúng `reserved_points`, tăng available. Lot hết hạn: không trả available/remaining, ghi `EXPIRED` theo lot. Giảm toàn bộ reserved; reservation conditional sang `RELEASED` (còn ít nhất một point được trả) hoặc `EXPIRED` (không point nào được trả; mixed case vẫn `RELEASED` và ledger thể hiện phần expired). Không bao giờ “ghi status cho allocation”.

### 5.4 Promotion

MVP chỉ root groups; conditions trong group là AND, groups là OR. Nested/cycle deferred.

`promotions` có code/type/priority/stackable/start/end/status/max discount/currency/`usage_limit`/`remaining_usage nullable`/per-customer limit/version. Reward có target product/category, percent/fixed/cap/currency/reward quantity/fixed combo/max applications per order.

`promotion_reservations(promotion_id,user_id,order_id,applications,discount_amount,status RESERVED|CONSUMED|RELEASED|EXPIRED,expires_at,idempotency_key,version)` unique promotion+order/key. `promotion_usage_counters(promotion_id,user_id,reserved_orders,consumed_orders,version)` unique pair.

MVP semantics đề xuất, chờ owner xác nhận: `usage_limit` và `per_customer_limit` đếm **order**, còn `applications` là số reward repeats trong một order và bị cap bởi `max_applications_per_order`. Checkout mỗi promotion/order reserve đúng một global/per-user slot dù applications > 1. Finite remaining decrement 1 atomically; NULL là unlimited và không decrement. Release trả đúng một slot; success chuyển reserved order counter sang consumed.

### 5.5 Spend ledger, outbox/inbox

`spend_ledger` dùng signed amount: `QUALIFIED_SPEND` dương, `REFUND` âm, `ADJUSTMENT` hai dấu; có currency, external reference, unique business key. Rolling total là `SUM(amount)` trong window, không trừ refund lần hai.

Main `outbox_events`: `id,event_id,aggregate_type,aggregate_id,event_type,event_version,topic,event_key,payload JSON,idempotency_key,status PENDING|PROCESSING|RETRY|PUBLISHED|DEAD_LETTER,retry_count,max_retries,next_retry_at,claimed_by,claimed_at,created_at,published_at,last_error`; unique event ID và business key.

Main `processed_events`: `id,event_id,consumer_name,event_type,correlation_id,aggregate_id,processed_at`; unique `(consumer_name,event_id)`. Business processing và insert marker cùng transaction.

## 6. Payment Service logical schema và operations

`payment_operations(id,main_payment_id,order_id,amount,currency,payment_method_ref,request_idempotency_key,stripe_idempotency_key,stripe_payment_intent_id,status RECEIVED|PROCESSING|SUCCEEDED|FAILED|UNKNOWN,failure_code,failure_message,claimed_by,claimed_at,version,timestamps)`; unique main payment/request key/Stripe key/external ID.

`refund_operations(id,refund_id,main_payment_id,order_id,amount,currency,external_payment_id,stripe_refund_id,request_idempotency_key,stripe_idempotency_key,status RECEIVED|PROCESSING|SUCCEEDED|FAILED|UNKNOWN,failure_code,failure_message,claimed_by,claimed_at,created_at,updated_at,version)`; unique refund ID/request key/Stripe key and unique nullable Stripe refund ID.

Payment Service có local `processed_events` và đầy đủ `outbox_events` như Main. V1 `payments` được additive backfill/cutover thành một source of truth rõ ràng; không để `payments` và `payment_operations` cùng authoritative.

Receive transaction insert inbox + create/find operation rồi commit/ack. Worker claim bằng lease, gọi Stripe **ngoài** DB transaction với stable Stripe idempotency key, sau đó transaction lưu result và terminal result outbox. Crash/`UNKNOWN` được query/reconcile, không blind retry charge/refund.

## 7. Notification Service Inbox và send operation

Notification MVP thêm:

- `processed_events(id,event_id,consumer_name,processed_at)`, unique `(consumer_name,event_id)`.
- Mở rộng `notifications` hoặc tạo `send_operations`: `event_id,business_idempotency_key,contact,notification_type,payload,status RECEIVED|SENDING|SENT|FAILED|UNKNOWN|DEAD,attempt_count,next_retry_at,last_error,sent_at,claimed_by,claimed_at,timestamps`; unique event ID và business key.

Listener transaction insert processed marker và durable send operation; commit rồi Kafka ack. Duplicate event/business key không tạo lần gửi thứ hai. Worker claim operation, gửi SMTP ngoài transaction, rồi mark result. Retry bounded/backoff; DEAD alert. SMTP timeout sau server chấp nhận có thể là `UNKNOWN`: không thể bảo đảm không gửi trùng nếu retry, nên mặc định chuyển review/manual retry thay vì tự gửi lại. Không tuyên bố exactly-once email.

Notification Service không phát critical event trong MVP nên không bắt buộc outbox. Listener/service không catch-and-swallow lỗi trước durable commit. Contact/payload/log được redact theo privacy policy.

## 8. Event contracts V2

Envelope chung: `eventId` UUID (một message), `eventType`, `eventVersion=2`, `occurredAt`, `correlationId` (toàn flow), `aggregateId`, `producer`, `idempotencyKey` (business operation). Các trường không dùng lẫn nhau.

| Event/topic/key | Payload nghiệp vụ |
|---|---|
| `PaymentRequestedEventV2` / `payment.requested.v2` / key `paymentId` | paymentId, orderId, amount string, currency, PaymentMethod ID |
| `PaymentResultEventV2` / `payment.result.v2` / key `paymentId` | paymentId, orderId, amount, currency, externalPaymentId/Stripe PaymentIntent ID, status `SUCCEEDED|FAILED`, failureCode/message |
| `PaymentReviewRequiredEventV2` / `payment.review.required.v2` / key `paymentId` | payment/order/external IDs, reason code, redacted detail, observedAt |
| `RefundRequestedEventV2` / `payment.refund.requested.v2` / key `paymentId` | refundId, orderId, paymentId, externalPaymentId, amount, currency, reason, requestedBy, requestIdempotencyKey |
| `RefundResultEventV2` / `payment.refund.result.v2` / key `paymentId` | refundId, orderId, paymentId, externalPaymentId, stripeRefundId, amount, currency, status `SUCCEEDED|FAILED`, failureCode/message |
| `NotificationRequestedEventV2` / `notification.requested.v2` / key aggregate ID | envelope + notification type, contact type/contact, minimal payload; business key unique per notification purpose |

Key luôn là payment ID cho payment/refund ordering. `refundId` vẫn là aggregate ID của refund event. Payment Service **chỉ phát terminal** `PaymentResultEventV2`/`RefundResultEventV2`. `UNKNOWN` được giữ và reconcile trong Payment Service; nếu cần Main visibility/alert, phát riêng `PaymentReviewRequiredEventV2`, không giả UNKNOWN là FAILED.

Main khi nhận review/mismatch: Payment `-> REVIEW`, Order `-> PAYMENT_REVIEW`, không consume/release reservations, không earn/spend/commit inventory; ghi audit và operations alert. Refund UNKNOWN giữ `REFUND_PENDING`, không reverse loyalty/spend/inventory.

Cancel protocol events không thuộc MVP vì customer không được cancel sau initiation. Do đó không tạo `PaymentCancelRequestedEventV2`/`PaymentCancelResultEventV2` hoặc acceptance criteria tương ứng.

## 9. State machines

### 9.1 Main Order và Payment

Order statuses MVP: `CREATED, PAYMENT_PENDING, PAID, PAYMENT_FAILED, PAYMENT_REVIEW, CANCELLED, EXPIRED, REFUND_PENDING, REFUNDED, REFUND_FAILED`. `CANCEL_REQUESTED` không cần vì post-initiation cancel deferred.

Payment statuses MVP: `AWAITING_PAYMENT_METHOD, PENDING, SUCCEEDED, FAILED, REVIEW, CANCELLED, REFUND_PENDING, REFUNDED, REFUND_FAILED, EXPIRED`. `CANCELLED` chỉ dùng cho cancel trước initiation. `PROCESSING` là Payment Service operation state; Main không cần phản ánh từng worker lease. `CANCEL_REQUESTED` và cancel PaymentIntent sau initiation deferred.

| Order / Payment transition | Actor và precondition | Atomic side effect | Terminal? / version guard |
|---|---|---|---|
| create `CREATED/AWAITING_PAYMENT_METHOD` | checkout service; reservations complete | snapshots + reservations | no / insert uniques |
| `CREATED/AWAITING -> PAYMENT_PENDING/PENDING` | owning customer initiate; before checkout TTL | Main payment request outbox | no / both rows conditional+version |
| `CREATED/AWAITING -> CANCELLED/CANCELLED` | owning customer cancel before initiation | release all reservations; notification optional | yes / order+payment+reservations guarded |
| `CREATED/AWAITING -> EXPIRED/EXPIRED` | checkout expiry job | release all reservations; no payment request | yes / order+payment+reservations guarded |
| `PAYMENT_PENDING/PENDING -> PAID/SUCCEEDED` | terminal reconciled result | commit inventory/promo, consume redeem, earn, spend | order paid not final due refund / guard |
| `PAYMENT_PENDING/PENDING -> PAYMENT_FAILED/FAILED` | reconciled terminal failure | release reservations | terminal payment attempt / guard |
| pending states `-> PAYMENT_REVIEW/REVIEW` | review event or mismatch | audit/alert; retain reservations | no / guard |
| `PAID/SUCCEEDED -> REFUND_PENDING/REFUND_PENDING` | authorized full refund request | refund row + refund outbox | no / guard |
| refund pending `-> REFUNDED/REFUNDED` | reconciled refund success | negative spend, point reversals/returns; **no auto-restock** | yes / guard |
| refund pending `-> REFUND_FAILED/REFUND_FAILED` | terminal failure | no financial/loyalty reversal | retry/manual state / guard |

Controller không thể set status. Mọi update dùng expected status và `@Version`/conditional affected-row check.

### 9.2 Full refund policy

Main tạo unique refund operation and outbox only from `PAID/SUCCEEDED`. Payment Service receives durably, creates refund operation, calls Stripe outside transaction with stable idempotency key, stores terminal result/outbox. Main reverses only on `SUCCEEDED`.

Earned points reversal first drains remaining source earned lot, then other available lots according to approved policy and creates loyalty debt for shortfall; no lot negative and refund is not blocked. Redeemed points return policy when original lots expired is owner decision. Financial refund never proves physical return: no stock change in handler; admin adjustment is separate audited/idempotent operation.

## 10. Reservation deadlines và race analysis

Hai deadlines độc lập:

- `reservationExpiresAt`: chỉ áp dụng `AWAITING_PAYMENT_METHOD`. Expiry job locks payment/order; if still awaiting, moves to `EXPIRED`, releases inventory/promotion and points according to expiry policy. Không phát payment request.
- `paymentDeadline`: áp dụng sau `PENDING`. Checkout TTL no longer releases anything. Quá deadline triggers Payment Service reconciliation; terminal failed/not-created releases, success commits, ambiguity produces review and retains reservations.

Race rules:

| Race | Lock/expected-state winner và kết quả |
|---|---|
| Initiation đúng lúc checkout expiry | Cả hai lock payment/order theo same order. Initiate thắng: state PENDING nên expiry no-op. Expiry thắng: EXPIRED nên initiate trả 409/expired và không outbox. |
| Payment success cùng customer cancel | Customer cancel post-initiation không hỗ trợ; success proceeds. Pre-initiation cancel cannot coexist with request because state guard serializes. |
| Timeout/reconciliation result cùng result consumer | Lock payment/order + expected state/business keys; một transition commits, duplicate/stale result becomes no-op or review on contradiction. |
| Hai expiry workers | `FOR UPDATE SKIP LOCKED`, lease/conditional state update; chỉ một releases. |
| Independent point/inventory/promo jobs | Job phải lock parent order/payment first; chỉ expire when `AWAITING_PAYMENT_METHOD`, không tự release pending payment. |

## 11. Transaction matrix đầy đủ

Quy ước mọi event có fresh Event ID; business key nêu riêng. “RB” = exception rollback toàn local transaction; retry uses bounded deadlock/backoff; crash recovery relies on persisted state/lease/outbox.

| # / use case | Service/transaction boundary | Lock/read → write; state/reservation | Event + IDs/keys; rollback, retry, crash recovery |
|---|---|---|---|
| 1 Cart Summary preview | Main read-only | DB price/promo/account; no writes/reserve | no event/key; cache fallback; retry read |
| 2 Checkout | Main single Tx | idempotency, cart/products/promos/account/lots → order/items/payment awaiting + 3 RESERVED types | **no payment event**; key client checkout UUID; RB all; deadlock retry; request lease recovers crash |
| 3 Duplicate checkout | Main Tx | lock `(user,key)`, compare hash/read snapshot | same returns prior, mismatch 409; concurrent waits; no duplicate order |
| 4 Checkout reservation expiry | Main batch Tx | skip-locked order/payment awaiting + all reservations → order/payment EXPIRED, reservations RELEASED/EXPIRED | expiry business keys per reservation/order; RB all; lease/state guard recovers |
| 5 Payment initiation | Main Tx | initiation request, order/payment/reservation headers → pending states + Main outbox | PaymentRequested V2 Event ID; key `payment-init:<paymentId>`; RB all; outbox recovers |
| 6 Duplicate initiation | Main Tx | lock key/payment operation, compare hash | prior response; mismatch 409; unique payment/business outbox key prevents second event |
| 7 Payment receive | Payment Tx A | insert processed + payment operation RECEIVED | input Event ID; `payment-request:<paymentId>`; RB means Kafka retry; ack after commit |
| 8 Execute Stripe payment | Payment claim Tx, external call, result Tx | lease operation → PROCESSING → terminal/UNKNOWN + terminal outbox if known | stable Stripe key; terminal result fresh Event ID; retry query before charge; stale lease recovery |
| 9 Payment success | Main one Tx | inbox + lock payment/order/reservations/account/lots → paid/succeeded, COMMITTED/CONSUMED, earn/spend | key `payment-result:<paymentId>:SUCCEEDED` plus side-effect keys; notification outbox; RB/retry; duplicate safe |
| 10 Payment failure | Main one Tx | same → failed; release all reservations/ledger | key `payment-result:<paymentId>:FAILED`; notification outbox; RB/retry guarded |
| 11 Payment unknown/reconciliation | Payment recovery Tx(s); Main review Tx if alerted | query Stripe; keep UNKNOWN or terminal; Main review retains reservations | Review event fresh ID/key `payment-review:<paymentId>:<reason>`; leases/retry/manual alert |
| 12 Cancel before initiation | Main one Tx | lock ownership/order/payment awaiting → CANCELLED and release reservations | `cancel-before-init:<orderId>:<requestId>`; optional notification outbox; RB/retry guarded |
| 13 Cancel after initiation | **Deferred** | API rejects customer cancellation; admin uses reconciliation/refund after result | no cancel events/criteria in MVP |
| 14 Point reservation timeout | Main batch Tx | lock parent awaiting then account/reservation/allocations/lots → precise release/expire | ledger keys per rules; RB/retry; skip-locked/state guard |
| 15 Inventory timeout | Main batch Tx | lock parent awaiting/product/reservation → reserved counter--, EXPIRED | `inventory:expire:<reservationId>`; RB/retry/guard |
| 16 Promotion timeout | Main batch Tx | parent awaiting/promo reservation/counter → restore one order slot | `promotion:expire:<reservationId>`; RB/retry/guard |
| 17 Point lot expiry | Main batch Tx | unreserved remaining lots/account → remaining 0, available--, per-lot EXPIRED ledger | `loyalty:expired:<lotId>:<jobWindow>`; RB/retry/skip-locked |
| 18 Full refund request | Main one Tx | idempotency + lock paid order/payment → refund pending + refund row/outbox | RefundRequested Event ID; key `refund-request:<refundId>`; RB/retry/outbox recovery |
| 19 Execute refund | Payment receive Tx + claim/external/result Tx | inbox/refund op → processing → terminal/UNKNOWN + outbox if known | stable Stripe refund key; terminal Event ID/key `refund-result:<refundId>:<status>`; reconcile before retry |
| 20 Refund success | Main one Tx | inbox + lock refund/order/payment/account/lots/spend → refunded, negative spend, reverse/return/debt | unique refund side-effect keys + notification outbox; RB/retry; no stock update |
| 21 Refund failed | Main one Tx | inbox + lock refund/order/payment → REFUND_FAILED | key `refund-result:<refundId>:FAILED`; no reversal; notification/alert outbox; RB/retry |
| 22 Refund unknown/reconciliation | Payment recovery; Main alert Tx | keep UNKNOWN; query Stripe; Main stays refund pending | review event/key `refund-review:<refundId>:<reason>`; no reversal; lease/manual recovery |
| 23 Admin inventory restock | Main one Tx | authorize; lock product/order → adjustment + on-hand increase | key client/admin UUID; audit; RB/retry; duplicate adjustment blocked |
| 24 Main Outbox publish | Main claim Tx + send + result Tx | skip-locked → PROCESSING; ACK → PUBLISHED or retry/dead | preserves Event ID/business key; ACK required; lease; duplicate possible |
| 25 Payment Outbox publish | Payment same 3 boundaries | same local table/state | same ACK/lease rules; no cross-DB transaction |
| 26 Notification consume/send | Notification receive Tx then worker external send/result Tx | inbox + send op RECEIVED; lease SENDING → SENT/FAILED/UNKNOWN/DEAD | input Event ID + notification business key; receive RB/Kafka retry; bounded send retry; SMTP ambiguity manual |

## 12. Outbox, inbox và acknowledgements

Per publishing service:

1. Short claim transaction uses eligible PENDING/RETRY rows ordered by ID `FOR UPDATE SKIP LOCKED`, sets PROCESSING/claimed owner/time, commit.
2. Outside transaction send `topic,event_key,payload`; wait `CompletableFuture` broker ACK with producer `acks=all` and timeout.
3. Result transaction guarded by row/status/claimer: ACK → PUBLISHED; failure → RETRY/backoff/count; exhausted → database **Outbox DEAD_LETTER state** + alert.
4. Lease recovery returns stale PROCESSING to RETRY. Crash after ACK before DB update may duplicate.

Consumer inserts `processed_events` and all local durable business writes in one transaction; unique `(consumer_name,event_id)`. Offset ack only after commit; failure escapes listener. Poison messages after configured attempts go to Kafka **Dead Letter Topic (DLT)**, distinct from Outbox DEAD_LETTER state.

Published outbox retention proposed 30 days online + 90-day archive; owner/security/legal confirm. DEAD_LETTER never auto-deleted. Replay creates fresh Event ID, preserves business key and audit link.

## 13. UNKNOWN và reconciliation

UNKNOWN means Stripe outcome cannot be proven, not FAILED. Recovery queries by external ID when present, Stripe idempotency key/request replay semantics, and payment/order/refund metadata. It may become SUCCEEDED/FAILED or remain UNKNOWN. No blind second charge/refund.

Payment Service emits only terminal result events. It may emit `PaymentReviewRequiredEventV2` for visibility. Main review state freezes reservations and disallows earn/spend/inventory commit/release. Operational runbook must set maximum review age, escalation and manual reconciliation; age alone never authorizes release.

## 14. PaymentMethod ID data policy

Baseline bắt buộc:

- Never log it or include in exception, audit, notification, checkout/initiation response snapshot, metrics tags or operations alert.
- APM/tracing/HTTP body capture redacts field; Kafka/admin Outbox DEAD_LETTER UI masks it.
- Main outbox/payment operation storage uses platform/database encryption at rest with least-privilege access; backups inherit encryption/access controls.
- TLS for UI→Main and service/Kafka transport; Kafka topic ACL limits producer/consumer/admin.
- Payload retention is shortest recovery window; terminal operation cleanup redacts PaymentMethod ID and published payload after owner-approved window. DEAD/UNKNOWN retains only encrypted/masked data until reconciliation then redacts.
- Secret scanning/log tests enforce no leakage. PaymentMethod ID is not full card data but is treated as sensitive operational data.

Security owner must confirm encryption implementation, key rotation, exact retention, privileged break-glass access and whether Kafka payload-level encryption is required.

## 15. Refund, points và restock

Full refund financial result, point effects and stock are separate. Only terminal refund success creates signed negative spend, reverses earned points and returns redeemed points. Earned points already spent create debt rather than negative lot or rejected refund. Refund UNKNOWN/FAILED makes no reversals.

No automatic restock in refund handler. MVP offers audited admin `inventory_adjustments`; physical return receipt/inspection is outside scope. Owner must define product types eligible for restock, roles/reasons/evidence and whether refund can precede return.

## 16. Promotion reward examples

- Buy 2 get 1: minimum quantity 3, `FREE_ITEM`, reward quantity 1, repeated only up to max applications; free item snapshot is gift.
- Buy A, 15% off B: condition product A; target product B; percentage 15 and optional cap.
- Category discount: target category plus percentage/fixed amount/currency/cap.
- Fixed combo: product/quantity conditions and fixed combo price; repetitions capped.

Applications calculate discount quantity only; under proposed order-based usage semantics they do not consume extra global/customer usage slots.

## 17. Cache policy

Preview may cache promotion definitions `promotion:def:v2:<id>:<version>` TTL 60s and active list `promotion:active:v2:<currency>` TTL 30s; evict on admin mutation. Recommendation TTL proposed 5m. Checkout/initiation never trust cache for price, active/date/limits, stock, point balance/lots, order/payment/reservation state or amount/currency.

## 18. Flyway plan theo module

Không sửa migration đã apply. Version numbers are planned and must be rechecked immediately before implementation.

- Main after V3: money/order/idempotency; order items/inventory; promotion; loyalty/allocation/debt; refund/spend/audit/alerts/adjustments; outbox/inbox; indexes/checks/seeds.
- Payment after V1: additive payment operation/backfill; refund operations; outbox/inbox; uniqueness/index/retention hardening.
- Notification after V1: processed events; extend notifications/send operations with unique business key, state/lease/retry indexes.

Roll-forward: backup → additive columns/tables → preflight duplicates/invalid money → chunked backfill → validate counts/checksums → constraints → feature cutover. Failure never edits applied scripts; next migration repairs. Test clean databases and upgrades from Main V3, Payment V1, Notification V1; preserve old price values with explicit rounding exception report.

## 19. V1 → V2 rollout và rollback

1. Backup; apply additive Main/Payment/Notification migrations.
2. Deploy Payment with V2 consumer while V1 remains during grace.
3. Deploy Notification V2 inbox/consumer.
4. Deploy Main V2 paths with producer feature flag OFF.
5. Stage smoke/end-to-end/concurrency/security tests.
6. Enable Main V2 producer for canary then ramp; monitor consumer lag, outbox pending/retry/dead, payment/refund rates, duplicates, mismatch/review and notification UNKNOWN.
7. Stop V1 production; retain V1 consumers for grace/in-flight drain; remove V1 mapping/topics in later release.

No dual production for one business operation: Main selects exactly V1 **or** V2 by deterministic feature flag at operation creation and persists `protocol_version`. Payment Service dual-consumes during grace, but shared business key `payment-request:<mainPaymentId>` and unique main payment/Stripe idempotency keys ensure V1+V2 duplicate cannot charge twice. Notification uses shared purpose business key across protocol versions.

Rollback conditions: elevated double-operation/mismatch/review, sustained lag/outbox dead state, migration/data integrity failure or payment success regression beyond agreed threshold. Response: stop new V2 selection, leave durable V2 consumers/outbox recovery running for already-tagged V2 operations, route new operations to V1 only if backward-compatible and safe, never replay V2 as V1 with a new business key. Database rollback is roll-forward correction; do not drop additive schema until grace/audit complete.

## 20. Testing strategy

Testcontainers MySQL matching production major proves JSON/DECIMAL, constraints, `FOR UPDATE`, `SKIP LOCKED`, migration, multi-worker claims and concurrency. H2 may remain for isolated unit tests only.

Required tests:

- Formula 100/20/10 → qualifying 80; rounding/cap/gift/shipping/multiplier; no double subtraction.
- Checkout emits no payment request; payment initiation emits it; duplicate initiation produces one outbox row; mismatched key payload 409.
- Concurrent checkout same key; stock/point/promotion contention; checkout expiry releases exactly once.
- Initiation vs expiry job; terminal success vs stale timeout; two workers per reservation; UNKNOWN never commits/releases/earns/spends.
- Immutable multi-lot allocations: valid-lot release, expired-lot ledger key per lot, mixed release, no duplicate ledger.
- Promotion order-based usage independent from applications and unlimited NULL semantics.
- Payment receive/execute crashes; stable Stripe key; duplicate V1/V2 input does not double charge; result mismatch goes review.
- Duplicate full refund request/result; refund UNKNOWN makes no reversals; spent earned points create debt; returned redeemed points follow approved expiry policy.
- Refund success does not restock; duplicate authorized admin restock changes stock once.
- Notification duplicate event/business key creates one send operation; deterministic pre-send duplicate does not send twice; SMTP timeout becomes UNKNOWN rather than claimed exactly-once.
- PaymentMethod ID absent from log, exceptions, response snapshots, audit, notification, APM fixtures and unmasked admin display; ACL/encryption/cleanup config tests.
- Main/Payment/Notification clean and upgrade migrations; old prices preserved; roll-forward rehearsal.

Build gates after implementation:

```text
mvn clean verify
mvn clean verify -pl ecommerce-platform-server -am
npm ci
npm run build
npm test -- --runInBand
```

## 21. Acceptance criteria theo phase

### Phase 0 — owner/security decisions

Checklist mục D được signed off and configuration values documented. Status can then move to `IMPLEMENTATION_READY`; no code starts before required decisions.

### Phase 1 — Main transaction foundation

- Checkout/initiation split exactly as API contracts; checkout has no payment event.
- Backend money/pricing authoritative; idempotency and all reservations atomic/concurrency-tested.
- Checkout deadline releases only awaiting payments; initiation/expiry race passes.

### Phase 2 — payment/refund reliability

- Main and Payment local inbox/outbox/operations, ACK/lease/retry/reconciliation complete.
- V2 payment and full refund event contracts/reconciliation pass, including UNKNOWN/review and duplicates.
- No post-initiation customer cancel protocol or criteria.

### Phase 3 — loyalty/refund/notification/security

- Ledger granularity/keys, debt, signed spend and owner-approved point expiry/return policies pass.
- Financial refund does not auto-restock; admin adjustment audited/idempotent.
- Notification durable inbox/send operation handles duplicate/UNKNOWN; PaymentMethod baseline enforced.

### Phase 4 — rollout

- Additive migrations and staging smoke pass; deterministic V1/V2 selection cannot double charge.
- Monitoring/alerts/runbooks/rollback triggers validated; V1 grace/drain completed before removal.

## 22. File list theo package thực tế

Tên mới dưới đây là planned, không phải class đang tồn tại.

Main, `ecommerce-platform-server/src/main/java/com/yashmerino/ecommerce/`:

- Sửa `model/{Product,CartItem,Order,Payment}.java`, DTOs, `services/{OrderServiceImpl,PaymentServiceImpl}.java`, interfaces, `controllers/{OrderController,PaymentController}.java`, `kafka/PaymentEventListener.java`, current producers during cutover, `utils/{OrderStatus,PaymentStatus}.java`.
- Tạo entity/repository/service/controller/DTO cho checkout request, payment initiation request, order item, inventory reservation/adjustment, promotion reservation/counter/rules, loyalty account/lot/reservation/allocation/transaction/debt, spend/refund/audit/alerts, outbox/processed event; expiry/reconciliation/outbox jobs; V2 event records under `kafka/events/`.
- New migrations under `ecommerce-platform-server/src/main/resources/db/migration/`; mirror tests under `src/test/java/`.

Payment, `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/`:

- Sửa `model/Payment.java`, `service/impl/{PaymentServiceImpl,StripePaymentServiceImpl}.java`, `kafka/{PaymentEventListener,PaymentResultProducer}.java`, event records/status.
- Tạo payment/refund operation, outbox/processed event models/repos/services, workers/recovery/publishers and V2 events. Migrations under module resources.

Notification, `ecommerce-platform-notification-service/src/main/java/com/yashmerino/ecommerce/`:

- Sửa `model/Notification.java`, `service/impl/NotificationServiceImpl.java`, `EmailNotificationSender.java`, `kafka/NotificationEventListener.java`, V2 event/status.
- Tạo processed-event repository/model and send-operation worker/recovery. Migration after Notification V1.

UI:

- Sửa `src/app/api/PaymentRequest.ts`, order API/types, `src/app/components/pages/cart/CartContainer.tsx`, `src/app/components/pages/orders/MyOrdersPage.tsx`; add distinct checkout/initiate calls and decimal-string types. Continue `createPaymentMethod`; no `confirmCardPayment` in MVP.

## 23. Owner decision checklist

Decisions marked “proposed” are not silently treated as approved.

## A. Dữ kiện đã xác minh từ repository

- Current endpoints, packages, entities/events/statuses/migrations and Stripe/UI flow are listed in section 2 from local source.
- Notification V1 has `notifications`, direct listener/service send and no inbox/business idempotency.
- Main listener and Notification listener catch exceptions; current direct Kafka sends do not establish DB/Kafka atomicity.

## B. Suy luận thiết kế

- Split checkout/payment initiation follows when PaymentMethod ID becomes available.
- Choose server-confirm Stripe flow as minimum safe MVP delta; Payment Service operation row is external-call recovery boundary.
- Choose no customer cancel after initiation to avoid adding a cancel protocol; admin resolves through reconciliation/refund.
- Include Notification Inbox/send operation because end-to-end duplicate-safe durable acceptance otherwise cannot be claimed.
- Separate financial refund from inventory restock.

## C. Quyết định nghiệp vụ đã chốt trong kế hoạch

- Checkout does not emit payment request; initiation does.
- Full refund is MVP; partial refund is not.
- Customer can directly cancel only `CREATED/AWAITING_PAYMENT_METHOD`; post-initiation customer cancel is rejected/deferred.
- Payment/Refund Service publishes terminal result only; uncertainty uses local UNKNOWN and optional review event.
- Point allocations are immutable; reservation is source of state; ledger granularity/keys follow section 5.3.
- Notification Inbox/send operation is MVP; email exactly-once is not claimed.
- Refund success does not automatically restock; stock adjustment is separate admin operation.

## D. Quyết định đang chờ chủ dự án

1. Settlement currency (EUR/VND), point value/`moneyPerPoint`, currency minor-unit support and future multi-currency tier rule.
2. Money `HALF_UP` and final earned-point `DOWN` rounding policy.
3. Exact checkout reservation TTL, payment deadline, Payment/Refund UNKNOWN escalation/recovery window.
4. Points expiring while reserved: no return after expiry versus grace extension; mixed reservation status semantics approval.
5. Redeemed points returned on full refund when original lot expired: no credit, original expiry, or new grace lot/duration.
6. Loyalty debt allowed and future earned points automatically repay debt; whether `lifetime_points` is gross historical earned or net of reversals.
7. Promotion semantics: global/per-customer limit counts orders; max applications counts reward repeats; root groups OR/groups conditions AND.
8. Who may request full refund, valid reasons/time window and whether refund may precede physical return.
9. Admin restock roles/evidence/reason policy and product types eligible for restock.
10. Security owner: at-rest/payload encryption mechanism, key rotation, PaymentMethod retention/redaction window, Kafka ACL/TLS and privileged access.
11. Outbox/audit/archive retention, retry limits/backoff, Kafka DLT and Outbox DEAD_LETTER operational ownership.
12. V2 canary thresholds, grace period, rollback alert thresholds and review/manual reconciliation SLA.

## E. Deferred khỏi MVP

- Partial refund/proration; customer cancel protocol after initiation.
- PaymentIntent client secret, `confirmCardPayment`, SCA completion and signed webhook.
- Nested promotion conditions; multi-currency conversion/tax/split capture.
- Full returns/RMA workflow; automatic restock.

## F. Rủi ro còn lại

- Stripe/SMTP ambiguity requires reconciliation/manual handling and can never be solved by a local transaction alone.
- DOUBLE→DECIMAL may expose already-lost precision; migration needs exception report/approval.
- Reservation contention/deadlock needs consistent lock order: parent order/payment, product IDs, promotion IDs, loyalty account, point lot IDs; bounded retry.
- Long review can hold scarce inventory/promotion/points; owner must set operational SLA without unsafe time-only release.
- V1/V2 overlap is safe only if shared business/Stripe keys and persisted protocol selection are implemented exactly.

## G. Điều kiện rollback/cutover

Cutover requires migration validation, V2 staging smoke/concurrency/security tests, dashboards/alerts/runbooks and owner decision sign-off. Roll back new traffic selection on thresholds in section 19, but continue processing already-durable V2 operations. Never rollback schema destructively or replay with a new business key.

## H. Implementation readiness verdict

Checkout/initiation contradiction, refund protocol/schema, reservation exact release, service reliability scope and rollout design are resolved at design level. Required financial/customer/security/operational decisions in section D are not yet confirmed.

> **Status: READY_FOR_OWNER_DECISIONS**
