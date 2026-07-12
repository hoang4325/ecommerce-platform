# Partner Product Management — Phân tích và kế hoạch phát triển hệ thống quản lý đối tác sản phẩm

> **Trạng thái:** `IMPLEMENTED — MVP Phase A–E`
>
> Tài liệu phân tích và kế hoạch; đã triển khai source, migration V11, API, và 104 unit tests.
> Repository được đối chiếu trực tiếp tại commit hiện tại. Base package thực tế là `com.yashmerino.ecommerce`.

## 1. Executive summary

Repository hiện là nền tảng thương mại điện tử ba backend module và một React UI. Commit mới nhất đã bổ sung checkout có idempotency, order-item snapshot bằng SQL, reservation tồn kho/khuyến mại/điểm, payment/refund V2 qua outbox/inbox và loyalty/spend ledger. Các phần này có source và Flyway migration thật, nhưng vẫn đang ở trạng thái chuyển tiếp: V1 và V2 chạy song song, một số thiết kế cũ vẫn tồn tại, Notification V2 chưa phải durable worker, và test suite hiện không xanh.

Miền partner chưa tồn tại. Không có entity, migration, repository, service, controller hay frontend cho `Partner`, `PartnerMember`, `PartnerOffer`, `PartnerOrder`, commission hoặc settlement. Seller hiện chỉ là `User` có system role `SELLER`; `Product.user_id` là ownership duy nhất. Mô hình đó không hỗ trợ tổ chức nhiều thành viên, phê duyệt đối tác, tenant scope, kiểm duyệt offer, chia đơn, hoa hồng hoặc đối soát.

Đề xuất kiến trúc cho MVP là **Phương án B — catalog Product dùng chung + PartnerOffer**, nhưng migration ban đầu giữ quan hệ 1:1 giữa legacy product và offer để giảm rủi ro. Không hợp nhất các legacy product giống nhau trong migration; việc deduplicate chỉ thực hiện sau bằng quy trình review. Mô hình B tốn thêm một lần join và thay đổi checkout/inventory lớn hơn Phương án A, đổi lại tránh phải viết lại order snapshot, cart, promotion và inventory khi hệ thống mở rộng thành marketplace nhiều nguồn cung.

MVP đề xuất gồm Partner foundation, membership/tenant authorization, offer moderation, inventory theo offer, split order thành `PartnerOrder`, commission snapshot, settlement tính toán–review–approve–mark-paid thủ công, audit và notification. AI chỉ hỗ trợ, chạy bất đồng bộ, không nằm trong critical transaction và có thể deferred khỏi MVP.

Trước khi bắt đầu Phase 1 phải xử lý các lỗi P0/P1: client có thể tự đăng ký system role `ADMIN`; seller có thể xóa sản phẩm của seller khác; payment V1 thiếu ownership; order V1 nhận total/status từ client; `User.products mappedBy` sai; V1/V2 chưa cutover; refund consumer dùng sai version; Notification có thể đánh dấu `SENT` dù SMTP lỗi; và test baseline đang fail.

## 2. Phạm vi và cách phân loại hiện trạng

Tài liệu dùng bốn nhãn:

| Nhãn | Ý nghĩa |
|---|---|
| **IMPLEMENTED** | Có source thực thi và migration/config tương ứng trong repository hiện tại. |
| **PARTIAL** | Có source thật nhưng thiếu một phần luồng, còn V1/V2 song song, thiếu UI/operational behavior hoặc có defect ảnh hưởng cam kết. |
| **DESIGN-ONLY** | Chỉ xuất hiện trong `ANALYSIS_AND_PLAN.md` cũ hoặc `docs/superpowers/plans/...`, không có implementation đầy đủ. |
| **MISSING** | Không tìm thấy source hoặc migration tương ứng. |

Không dùng README làm bằng chứng chức năng. Các kết luận dưới đây được đối chiếu từ entity, DTO, repository, service, controller, security config, migrations, Payment Service, Notification Service, React UI, tests và commit mới nhất.

## 3. Kiến trúc repository đã xác minh

| Module | Hiện trạng xác minh |
|---|---|
| `ecommerce-platform-server` | Main business service, MySQL `ecommerce_platform`, Spring Security/JWT, cart/product/order, checkout, inventory/promotion/loyalty reservation, main payment/refund state, outbox/inbox và Kafka V1/V2. |
| `ecommerce-platform-payment-service` | MySQL riêng; listener V1 gọi Stripe trực tiếp; V2 nhận inbox, lưu operation, worker gọi Stripe với idempotency key và phát result/review qua outbox. |
| `ecommerce-platform-notification-service` | MySQL riêng; V1 và V2 listeners. V2 có processed-events/business key nhưng vẫn gửi SMTP trong listener transaction, chưa có durable send worker. |
| `ecommerce-platform-ui` | React/Vite; customer catalog/cart/order và seller product CRUD. Cart đã gọi checkout/initiation V2. Chưa có partner, moderation, commission, settlement, refund hoặc loyalty UI. |
| `ecommerce-platform-it` | Selenium tests cho user/seller/cart/product; chưa có partner/multi-partner flow. |

Flyway hiện có Main V1–V6, Payment V1–V2, Notification V1–V2. Không được sửa các migration này; mọi thay đổi partner phải additive bằng version mới.

## 4. Hiện trạng seller đã xác minh

### 4.1 Mô hình dữ liệu và ownership

- `model.Product.user` là `@ManyToOne` với `@JoinColumn(name = "user_id")` (`Product.java:97-102`). Đây là nguồn ownership thực tế.
- `model.User.products` khai báo `@OneToMany(mappedBy = "id", cascade = ALL)` (`User.java:54-59`). Giá trị đúng phải là `mappedBy = "user"`. `mappedBy = "id"` trỏ vào primary key của `Product`, không trỏ vào association owner và có thể làm Hibernate metadata/context khởi động lỗi khi collection được validate.
- Migration V1 tạo `products.user_id` nullable, có FK tới `users.id`, nhưng không có constraint bắt buộc seller. Vì vậy dữ liệu product không seller là trạng thái hợp lệ ở DB hiện tại và phải được xử lý khi backfill.
- Không có seller profile, seller status, organization, membership hoặc tenant ID. Username chỉ là identity hiển thị/lookup, không phải tenant boundary phù hợp.

### 4.2 Role `SELLER` và API hiện tại

`SecurityConfig` cho phép:

- GET `/api/product/**`: public, bao gồm catalog, ảnh và `/seller/{username}`.
- POST/PUT/DELETE `/api/product/**`: chỉ cần authority `SELLER`.
- Không có method-level policy kiểm tra partner/member role; ownership nằm rải rác trong service.

Luồng cụ thể:

| Hành động | Source/method | Hiện trạng |
|---|---|---|
| Tạo product | `ProductServiceImpl.addProduct` | Lấy current principal theo username, gán `product.user`; ownership có gán. Không có moderation/status partner; DTO cho client gửi trực tiếp category entities. |
| Lấy theo seller | `ProductController.getSellerProducts` → `ProductServiceImpl.getSellerProducts` → `ProductRepository.getProductsBySellerId` | Path dùng username rồi đổi sang user ID; public read. Không kiểm tra người gọi là seller đó, phù hợp nếu đây là public storefront nhưng không được tái sử dụng làm API quản trị. |
| Sửa product | `ProductServiceImpl.updateProduct` | So sánh current username với `product.user.username`; có ownership nhưng scope dựa trên mutable business identifier. Client được gửi name/categories/price/description trực tiếp; không có allowlist theo moderation state. |
| Cập nhật ảnh | `ProductServiceImpl.updatePhoto` | Có cùng ownership check bằng username. File lưu BLOB trong DB; không thấy kiểm MIME, kích thước hay malware scan. |
| Xóa product | `ProductServiceImpl.delete` | Chỉ `findById` rồi `delete`; **không kiểm tra ownership**. Bất kỳ user role `SELLER` nào biết product ID đều có thể xóa product seller khác. |
| Hiển thị catalog/search | `getAllProducts`, `search` | Trả mọi product; không filter `active`, seller status hoặc moderation status. |
| Thêm giỏ | `addProductToCart` | Không kiểm `active`, stock hay seller/partner status ở lúc add; checkout mới kiểm lại active/stock. |

### 4.3 Lỗi và hướng sửa bắt buộc

1. **P0 — IDOR khi xóa:** `ProductServiceImpl.delete(Long)` phải query resource theo `(productId, currentPartnerId)` hoặc policy service kiểm `PartnerMember` + resource partner; admin có endpoint riêng. Không chỉ thêm một username comparison tạm thời rồi giữ mô hình cũ.
2. **P0 — self-register ADMIN:** `RegisterDTO.role` nhận enum gồm `ADMIN`, `AuthServiceImpl.register` lookup và gán đúng role client gửi, còn POST `/api/auth/**` là public. Public registration chỉ được tạo `USER`; partner access đến từ approved `PartnerMember`, system `ADMIN` chỉ qua trusted admin workflow.
3. **P1 — JPA mapping:** sửa `User.products mappedBy = "user"`. Sau cutover PartnerOffer, collection legacy nên deprecated rồi xóa ở phase cleanup.
4. **P1 — nullable owner:** backfill/quarantine product có `user_id IS NULL`; target offer phải có `partner_id NOT NULL`.
5. **P1 — username scope:** current user phải resolve thành immutable `userId`, rồi membership `(user_id, partner_id, status)`; không nhận username từ client để scope command.
6. **P1 — mass assignment:** command DTO tách riêng create/update/submit/admin-decision. Không nhận `status`, `partnerId`, `approvedBy`, commission, payable, stock counters hoặc entity `Category` từ client; nhận category IDs và validate server-side.
7. **P1 — file upload:** chuyển tài liệu/ảnh sang object storage metadata, enforce MIME/size/checksum/scan và signed URL; DB không giữ hồ sơ pháp lý lớn.

## 5. Nền tảng giao dịch hiện tại

### 5.1 Ma trận implemented/partial/design-only/missing

| Miền | Trạng thái | Bằng chứng và giới hạn |
|---|---|---|
| Checkout V2 | **IMPLEMENTED/PARTIAL** | `CheckoutController` và `CheckoutService.checkout`; idempotency row+hash, cart/product locks, server pricing, order/payment creation. UI gọi endpoint. Chưa có partner/offer, shipping, rich promotion hoặc multi-partner split. |
| Order snapshot | **PARTIAL** | Migration V4 có `order_items`; checkout insert qua `JdbcTemplate`. Không có Java `OrderItem` entity/repository; snapshot hiện chỉ product/name/price/quantity/discount fields, chưa có partner/offer/commission/payable. |
| Inventory reservation | **IMPLEMENTED/PARTIAL** | Product có on-hand/reserved/version; V4 có `inventory_reservations`; checkout reserve, success commit, fail/cancel/expiry release. Inventory vẫn ở Product, chưa theo PartnerOffer/kho; không có adjustment API. |
| Payment initiation V2 | **IMPLEMENTED/PARTIAL** | Ownership, state, expiry, request idempotency, Main outbox. Payment Service có inbox/operation/worker/Stripe idempotency/outbox. V1 endpoint/listener vẫn tồn tại. |
| Refund V2 | **IMPLEMENTED/PARTIAL** | Full refund request, ownership, Main outbox, Payment operation/Stripe refund, result inbox, spend/loyalty reversal. Không partial refund, return workflow hay settlement adjustment; UNKNOWN chưa có explicit reconciliation workflow. |
| Loyalty | **IMPLEMENTED/PARTIAL** | V4/V6 tables; reserve/release/consume/earn/debt/reversal bằng JDBC. UI luôn gửi `requestedPoints: 0`; không có loyalty API/dashboard. Một số edge semantics chỉ được thể hiện trong service, chưa thành domain model. |
| Promotion | **PARTIAL** | Có fixed `discount_amount` coupon, validity/usage reservation/counter. Không có product/category/partner funding, stacking/rule groups hay discount allocation hoàn chỉnh vào từng line. |
| Main outbox/inbox | **IMPLEMENTED/PARTIAL** | Outbox publisher claim/retry/dead-letter và processed event marker cho V2 result. V1 producer/consumer vẫn gửi trực tiếp và catch/swallow. |
| Payment outbox/inbox | **IMPLEMENTED/PARTIAL** | V2 durable operation/outbox/inbox. Repository claim đưa `UNKNOWN` trở lại worker sau lease; chưa có Stripe query/reconciliation job rõ ràng. V1 direct Stripe path vẫn hoạt động. |
| Notification inbox | **PARTIAL** | V2 processed-events và business key có migration/source. Gửi SMTP vẫn ở listener transaction; chưa có claim/retry worker và SMTP ambiguity handling. |
| Event V2 | **IMPLEMENTED/PARTIAL** | Payment/refund/review records có envelope gần đủ và V2 topics. Naming `eventType` chưa thống nhất giữa Main và Payment; notification V2 chưa được Main phát từ flow payment V2. |
| Partner/offer/order/commission/settlement | **IMPLEMENTED** | Partner/PartnerMember/PartnerDocument/PartnerOffer/PartnerOrder/CommissionRule/Settlement/SettlementLine entities, repositories, services, controllers, and V8–V11 Flyway migrations. 104 unit tests added. |
| AI partner/product | **MISSING** | Không có source, schema, provider adapter hoặc job. |

### 5.2 Checkout và order snapshot hiện tại

`CheckoutService.checkout`:

1. Resolve current `User` từ principal username.
2. Insert/lock `checkout_requests` theo `(user_id, idempotency_key)` và so SHA-256 request hash.
3. Lock cart lines + products `FOR UPDATE`; kiểm `active`, quantity và `on_hand - reserved`.
4. Tính subtotal từ DB price; claim fixed coupon; reserve point lots.
5. Tạo `Order(CREATED)`, insert SQL `order_items`, tăng `reserved_quantity`, tạo reservations.
6. Tạo `Payment(AWAITING_PAYMENT_METHOD)` và lưu response snapshot.

Đây là nền tảng đúng hướng cho partner checkout nhưng phải đổi cart line từ `product_id` sang selected `offer_id`, lock offers theo ID, snapshot partner/commission và tạo `PartnerOrder` trong cùng transaction.

### 5.3 Payment, refund, loyalty và event reliability

- `CheckoutService.initiate` xác minh payment/order thuộc current user, state và expiry; request chỉ có `paymentMethodId`; amount/currency lấy từ snapshot; tạo Main outbox `payment.requested.v2`.
- Payment V2 listener lưu inbox + `PaymentOperation`; worker gọi Stripe ngoài DB transaction với key `payment-{mainPaymentId}`, rồi lưu terminal state và outbox result. Non-terminal/exception chuyển `UNKNOWN` và phát review event, nhưng claim query có thể lấy `UNKNOWN` để gọi lại; cần reconciliation policy rõ thay vì dựa ngầm vào Stripe idempotency.
- Payment success ở Main commit inventory/promotion/point reservation, tạo earned lot và spend ledger. Payment failure release reservations.
- Refund MVP là full amount `order.totalAmount`; success ghi spend âm và reverse loyalty. Không tự restock — phù hợp vì financial refund không chứng minh hàng đã trả.
- `RefundResultV2Consumer` truyền `refund.getVersion()` khi optimistic-update `Order`; đây không phải `order.version`, có thể gây conflict sai hoặc update nhầm kỳ vọng. Phải load/lock Order và dùng đúng version.
- Mismatch payment đưa Main vào `REVIEW`; mismatch refund chỉ tạo alert và mark inbox, nhưng không có explicit refund review state/operational resolution.

### 5.4 V1/V2 coexistence và notification

- `/api/order` vẫn nhận `OrderDTO.totalAmount` và `OrderDTO.status`, sau đó converter gán thẳng vào entity. Đây là client-authoritative money/status và không tạo order items/reservations.
- `/api/payment/{orderId}` vẫn tạo payment theo order ID và gửi V1 event trực tiếp; không kiểm order ownership. `PaymentDTO.orderId/amount` được nhận dù service không dùng để tính amount; endpoint vẫn là IDOR command risk và xung đột unique payment/order của V4.
- Main V1 `PaymentEventListener` catch-and-swallow exception và phát notification V1 trực tiếp. Payment V2 success hiện không tạo notification request.
- Notification V1 listener catch-and-swallow. `EmailNotificationSender.send` cũng catch exception mà không rethrow, vì vậy caller có thể đánh dấu notification `SENT` dù SMTP thất bại.
- Notification V2 tạo row, gửi SMTP, rồi đánh dấu inbox trong cùng transaction; có business unique key nhưng chưa tách durable receive và external send. `claimed_by/claimed_at` được migrate nhưng chưa có worker sử dụng.

Kết luận: checkout/payment/inventory/refund/loyalty/outbox/inbox là **shared transaction foundation**, không phải mục tiêu trung tâm mới. Phase 0 phải cutover khỏi V1 và sửa defects trước khi partner settlement phụ thuộc vào dữ liệu này.

## 6. Technical debt và security backlog ưu tiên

| Mức | Vấn đề | File/class/method | Hướng xử lý |
|---|---|---|---|
| P0 | Public client tự chọn `ADMIN` | `RegisterDTO.role`, `AuthServiceImpl.register`, `SecurityConfig` | Public registration luôn USER; admin provisioning riêng, audit và rate limit. |
| P0 | Seller xóa product không sở hữu | `ProductServiceImpl.delete` | Tenant-scoped repository/policy; test Seller A/B. |
| P0 | Payment V1 thao tác order khác | `PaymentServiceImpl.pay` | Disable/cutover V1; trước cutover bắt buộc ownership/state/idempotency. |
| P0 | Order V1 nhận total/status từ client | `OrderDTO`, `OrderServiceImpl.placeOrder` | Deprecate/disable; chỉ checkout server-authoritative tạo order. |
| P1 | JPA `mappedBy` sai | `User.products` | Sửa thành `user`; sau offer cutover loại association legacy. |
| P1 | Refund optimistic version sai | `RefundResultV2Consumer.onRefundResult` | Dùng loaded `Order.version`, không dùng `Refund.version`. |
| P1 | V1/V2 cùng chạy | controllers/listeners/producers hai thế hệ | Feature flag, metrics, dual-read có kiểm soát, rồi disable V1. Không dual-charge. |
| P1 | Notification false success/delivery ambiguity | `EmailNotificationSender`, `NotificationServiceImpl` | Durable send operation; sender rethrow; bounded retry, UNKNOWN/manual review. |
| P1 | Product list không filter active/moderated | `ProductServiceImpl.getAllProducts/search` | Public query chỉ approved offer + approved partner + available policy. |
| P1 | Category/entity mass assignment | `ProductDTO.categories` | Command DTO nhận IDs; resolve/allowlist server-side. |
| P1 | Username/email thiếu DB unique constraint | Main V1 migration + `UserRepository` | Data audit rồi additive unique constraints; identity scope dùng user ID. |
| P1 | Product owner nullable | Main V1 schema | Quarantine/manual mapping khi backfill. |
| P1 | Hardcoded dev credentials | application/docker config | Secret manager/env, rotate committed JWT/DB values, tách dev sample. |
| P2 | Actuator và Swagger permit-all | `SecurityConfig` | Health tối thiểu public; admin/network policy cho phần còn lại. |
| P2 | Product/photo BLOB và upload validation | `Product.photo`, upload methods | Object storage, MIME/size/scan/checksum. |
| P2 | Test baseline fail | `ProductControllerTest` | Sửa fixture/content type; đưa reactor về xanh trước phase partner. |

Lần chạy xác minh ngày 2026-07-12 cho kết quả:

- Reactor `mvn -pl ecommerce-platform-server,ecommerce-platform-payment-service,ecommerce-platform-notification-service test` dừng ở Main: `ProductControllerTest` fail 15/29 do expected 200/400/403 nhưng nhận 415.
- Chạy riêng Payment Service: 42 tests, 22 errors do Mockito inline Byte Buddy không self-attach được vào JVM của môi trường; đây là test-runtime/tooling failure nên chưa chứng minh các test nghiệp vụ pass hay fail.
- Chạy riêng Notification Service: 36 tests pass.

Đây là baseline cần sửa/chuẩn hóa trong CI, không phải lỗi do tài liệu này và không được dùng số test đã pass để suy ra flow production hoàn chỉnh.

## 7. Quyết định mô hình Product–Partner

### 7.1 So sánh hai phương án

| Tiêu chí | A — `Product.partner_id` | B — `Product` + `PartnerOffer` |
|---|---|---|
| Ý nghĩa Product | Listing thuộc đúng một partner | Catalog dùng chung; offer chứa seller-specific commercial data |
| Giá/SKU/stock | Trực tiếp trên Product | Trên PartnerOffer |
| Ưu điểm | Migration gần mô hình hiện tại; ít join; UI và checkout đổi ít hơn | Hỗ trợ nhiều nguồn cung; tách content moderation khỏi offer; commission/inventory/price rõ theo partner; không phải đổi model lần hai |
| Nhược điểm | Trùng catalog; khó so sánh offer; muốn marketplace sau này phải đổi cart/order/inventory lần nữa | Thêm entity/state/join; cần quy tắc chọn offer và hai lớp moderation; migration/cart/frontend phức tạp hơn |
| Migration | Backfill `partner_id` từ `user_id`; chuyển stock/price ít | Tạo một catalog Product + một offer cho mỗi legacy Product; chuyển price/stock vào offer; giữ compatibility mapping |
| Checkout/cart | Cart giữ product ID; snapshot thêm partner | Cart phải giữ offer ID; checkout lock/reserve offer; product chỉ để hiển thị |
| OrderItem | product + partner snapshot | product + offer + partner snapshot, đúng nguồn cung lịch sử |
| Inventory | Product-level | Offer-level; sẵn sàng thêm warehouse sau |
| Promotion | Dễ cho product; khó partner-funded/shared | Có thể target catalog/product hoặc offer/partner và lưu funding allocation |
| Frontend | Ít thay đổi; mỗi card là listing | Product detail phải chọn/default offer; partner dashboard quản lý offer |

### 7.2 Đề xuất MVP: Phương án B với migration bảo thủ

Chọn B vì các yêu cầu cốt lõi đã bao gồm inventory theo đối tác, multi-partner order, commission snapshot và settlement. Nếu chọn A để tiết kiệm ngắn hạn, Phase marketplace sau phải thay đổi đúng các bảng có tính bất biến và rủi ro cao nhất: cart item, order item, inventory reservation, promotion allocation và refund/settlement lineage.

Trade-off được kiểm soát như sau:

- Mỗi legacy Product tạo một catalog Product riêng và một PartnerOffer tương ứng; chưa deduplicate tự động.
- Trong MVP, mỗi cart line chọn đúng một offer; hệ thống có thể hiển thị một default approved offer, chưa cần ranking phức tạp.
- Content Product có thể do partner khởi tạo nhưng sau khi approved trở thành catalog record do platform quản trị; partner chỉnh commercial fields ở Offer. Thay đổi content trọng yếu tạo submission/review thay vì ghi trực tiếp.
- Inventory chuyển sang Offer. Các cột stock/price cũ trên Product được dual-read/dual-write có thời hạn, rồi bỏ sau cutover.
- `offerId` là bắt buộc với order mới; order lịch sử có thể để nullable và đánh dấu `LEGACY` nếu không backfill chắc chắn.

## 8. Mô hình nghiệp vụ và dữ liệu mục tiêu

Tất cả money dùng `DECIMAL(19,2)`/`BigDecimal`, currency snapshot; các aggregate có `version`; timestamp lưu UTC; trạng thái do server quyết định. Các bảng nghiệp vụ quan trọng có audit log và business uniqueness.

### 8.1 Partner

```text
Partner
  id, code, name, businessName, taxCode, email, phone, address
  status: DRAFT | PENDING_REVIEW | CHANGES_REQUESTED | APPROVED | REJECTED | SUSPENDED | TERMINATED
  approvedAt, approvedBy, rejectedAt, rejectionReason
  suspendedAt, suspensionReason
  version, createdAt, updatedAt
```

Constraints/indexes:

- Unique normalized `code` và `taxCode` (policy country/format do owner xác nhận).
- Index `(status, created_at)`, `(tax_code)`, `(approved_by)`.
- Status transition bằng conditional update/version; không nhận status trong profile DTO.
- `TERMINATED` là terminal trong MVP; restore chỉ từ `SUSPENDED` về `APPROVED`.

### 8.2 PartnerMember

```text
PartnerMember
  id, partnerId, userId
  role: OWNER | MANAGER | PRODUCT_STAFF | ORDER_STAFF | FINANCE_STAFF
  status: INVITED | ACTIVE | SUSPENDED | REMOVED
  joinedAt, createdAt, updatedAt, version
```

- Unique `(partner_id, user_id)` cho membership hiện tại hoặc dùng history table nếu cần rejoin.
- Một user **nên được phép thuộc nhiều partner**; active partner context phải explicit, không suy từ username/JWT role duy nhất.
- Không được xóa OWNER cuối cùng; transfer ownership là command riêng.
- System role `PARTNER` chỉ nói user có khả năng vào partner area; quyền thực tế lấy từ active membership.

### 8.3 PartnerDocument

```text
PartnerDocument
  id, partnerId, type, status
  objectKey, originalFileName, contentType, size, checksum
  uploadedBy, reviewedBy, reviewedAt, rejectionReason
  expiresAt, version, createdAt, updatedAt
```

Loại: business license, tax document, verification, contract, bank evidence. Database chỉ lưu metadata/object key. Object storage mã hóa at rest, signed URL ngắn hạn, malware scan, retention và access audit. Không log nội dung tài liệu hoặc bank data.

### 8.4 Product và PartnerOffer

`Product` trở thành catalog content: `id, canonicalSku(optional), name, description, brand, category links, media metadata, contentStatus, version, timestamps`. Giá và stock không còn authoritative ở Product.

```text
PartnerOffer
  id, partnerId, productId, partnerSku
  price, currency
  onHandQuantity, reservedQuantity
  status: DRAFT | PENDING_REVIEW | APPROVED | REJECTED | SUSPENDED | OUT_OF_STOCK | ARCHIVED
  submittedAt, approvedAt, approvedBy, rejectionReason
  version, createdAt, updatedAt
```

Constraints:

- Unique `(partner_id, partner_sku)`; cân nhắc unique `(partner_id, product_id)` nếu MVP chỉ một offer/partner/product.
- Checks `price > 0`, `on_hand >= 0`, `reserved >= 0`, `reserved <= on_hand`.
- Public sellable predicate: partner `APPROVED` AND offer `APPROVED` AND catalog content approved AND active policy AND available quantity > 0.
- `OUT_OF_STOCK` nên là derived/display state hoặc controlled projection; không để race giữa status và quantity. Authoritative approval status tách khỏi availability nếu triển khai chi tiết.
- Soft delete qua `ARCHIVED`; không hard-delete offer có order/reservation/audit lineage.

Khuyến nghị thêm `ProductSubmission` hoặc versioned `ProductRevision` để review thay đổi content mà không ghi đè bản đang bán. Offer submission xử lý commercial fields; content submission xử lý name/description/category/media.

### 8.5 PartnerOrder và OrderItem snapshot

```text
PartnerOrder
  id, orderId, partnerId
  status
  subtotal, discountAllocation, shippingAllocation
  commissionAmount, partnerPayableAmount, currency
  acceptedAt, packedAt, readyToShipAt, shippedAt, deliveredAt, cancelledAt
  version, createdAt, updatedAt
```

- Unique `(order_id, partner_id)` trong MVP; nếu sau này nhiều fulfillment group/kho thì thêm group key.
- Partner chỉ query theo membership-scoped partner ID, không `findById` đơn thuần.
- Order tổng giữ customer/payment state; `PartnerOrder` giữ fulfillment/partner financial state.

`order_items` bổ sung immutable snapshot tối thiểu:

```text
partner_order_id
partner_id, partner_name
offer_id, partner_sku
product_id, product_name
unit_price, quantity, currency
discount_allocation, shipping_allocation
commission_rule_id, commission_rate, commission_fixed_fee
commission_amount, partner_payable_amount
```

Không tính lại commission lịch sử từ rule hiện tại. Refund/settlement dùng snapshot và append-only adjustment line.

### 8.6 CommissionRule

```text
CommissionRule
  id
  partnerId nullable
  categoryId nullable
  productId nullable
  rate, fixedFee, currency nullable
  validFrom, validTo, priority
  status: DRAFT | ACTIVE | INACTIVE | EXPIRED
  version, createdAt, updatedAt
```

Precedence đề xuất:

1. Product-specific.
2. Category-specific.
3. Partner-specific.
4. System default.

Trong cùng specificity: priority cao hơn, rồi `validFrom` mới hơn, rồi ID để deterministic. Không cho hai active rule cùng scope/priority/time range nếu gây ambiguity. Commission engine trả cả rule ID và input/output snapshot.

### 8.7 Settlement và SettlementLine

```text
Settlement
  id, partnerId, periodStart, periodEnd, currency
  grossSales, refundAmount, commissionAmount, otherFees
  manualAdjustment, payableAmount
  status: OPEN | CALCULATED | UNDER_REVIEW | APPROVED | PAID | FAILED | CANCELLED
  approvedBy, approvedAt, paidAt, paymentReference
  version, createdAt, updatedAt
```

Business key đề xuất: unique `(partner_id, period_start, period_end, currency, calculation_version)` hoặc một active settlement/kỳ và revision history riêng.

```text
SettlementLine
  id, settlementId, partnerId
  lineType: SALE | DISCOUNT | COMMISSION | REFUND | FEE | SHIPPING | ADJUSTMENT
  orderId, partnerOrderId, orderItemId, refundId nullable
  sourceEventId/businessKey, amount, currency
  description, adjustmentReason, createdBy, createdAt
```

- Mỗi dòng truy vết tới source; không chỉ lưu tổng.
- Unique business key chống ghi trùng, ví dụ `(settlement_id, line_type, source_entity, source_id, component)`.
- Adjustment thủ công bắt buộc reason, actor, before/after audit; không sửa line nguồn.
- Settlement approved/paid là immutable; sai sót sửa bằng adjustment/carry-forward, không rewrite lịch sử.

### 8.8 Audit và operational tables

`audit_events`: `id, aggregateType, aggregateId, partnerId, action, fromState, toState, actorUserId, actorType, reason, correlationId, requestId, metadataRedacted, occurredAt`.

`idempotency_requests`: có thể dùng table theo command hoặc generic table với unique `(actor_scope, operation, key)`, canonical request hash và response snapshot. Không lưu payment method, tài liệu hoặc PII nhạy cảm trong response/audit.

## 9. Quy trình nghiệp vụ mục tiêu

### 9.1 Đăng ký và phê duyệt đối tác

1. Authenticated USER tạo application `DRAFT`; server gán applicant, không nhận owner/admin IDs.
2. Validate required data, normalized tax code, email/phone, document metadata và duplicate candidates.
3. Submit bằng command idempotent: `DRAFT/CHANGES_REQUESTED/REJECTED -> PENDING_REVIEW`; đóng băng revision được review; phát event outbox.
4. Admin xem profile/documents và chọn approve, reject hoặc yêu cầu bổ sung. Yêu cầu bổ sung chuyển sang `CHANGES_REQUESTED`, lưu danh sách thiếu/reason và cho applicant tạo revision mới; không dùng `REJECTED` để biểu diễn hai ý nghĩa khác nhau.
5. Admin approve/reject bằng conditional transition/version và command key.
6. Approve tạo/activate `PartnerMember(applicant, OWNER)` trong cùng transaction và gán system `PARTNER` nếu không có membership partner nào trước đó.
7. Mọi transition ghi audit + outbox trong cùng DB transaction.

Edge cases:

- Trùng tax code: DB unique là lớp cuối; application trùng trả conflict và reference case hiện hữu cho admin, không tiết lộ hồ sơ cho applicant khác.
- User nhiều partner: cho phép theo đề xuất; mỗi request partner command resolve active membership và resource scope.
- Suspend partner: account user vẫn hoạt động cho customer/partner khác; chỉ chặn scope partner bị suspend. Đơn đang xử lý theo policy owner xác nhận.
- Restore: chỉ admin, `SUSPENDED -> APPROVED`, reason bắt buộc và re-evaluate documents nếu hết hạn.
- Terminate: không restore trong MVP; giữ data/audit/settlement obligations.
- Hai admin approve đồng thời: optimistic version + unique owner membership + idempotent event business key; loser nhận current state/409 tùy same command.

### 9.2 Đăng và kiểm duyệt sản phẩm/offer

1. `OWNER|MANAGER|PRODUCT_STAFF` tạo catalog draft/submission và PartnerOffer `DRAFT` trong partner scope.
2. Partner sửa allowed fields: partnerSku, price, stock qua inventory command, offer-specific media/attributes nếu policy cho phép. Không sửa approval actor/status/commission/payable.
3. Submit `DRAFT|REJECTED -> PENDING_REVIEW`; server snapshot revision.
4. Admin approve/reject/suspend. Chỉ approved catalog + approved offer + approved partner mới public.
5. Thay đổi trọng yếu name/brand/category/media hoặc price vượt threshold tạo revision/re-review. Stock quantity không cần moderation; price policy owner xác nhận.
6. Khi partner suspended, offers bị loại khỏi sellable projection; không cần rewrite tất cả offer status nếu predicate đã gồm partner status. Có thể phát projection events để cache/search cập nhật.
7. Cart giữ offer ID. Nếu offer/partner không còn sellable, cart line vẫn có thể hiển thị “unavailable” để user biết nhưng checkout phải từ chối; không tự đổi sang offer khác hoặc giá khác.
8. Soft delete bằng archive. Offer có reservation/order không hard-delete.

Admin-only: canonical category taxonomy, approval/suspension, protected brand/compliance flags. Partner-editable: draft content theo policy và offer commercial data. Inventory thay đổi qua delta command/reason, không `PUT` toàn entity.

### 9.3 Checkout và chia đơn theo partner

Trong một Main DB transaction:

1. Resolve each cart line to immutable selected `offer_id`; query offer + product + partner sellable state.
2. Lock offers theo ascending offer ID; validate available stock và current price.
3. Tính promotion/funding allocations và chọn commission rule deterministic cho từng line.
4. Tạo một customer `Order` tổng.
5. Group lines theo partner và tạo một `PartnerOrder(NEW)`/partner.
6. Insert immutable `OrderItem` snapshots gồm partner/offer/product/price/discount/commission/payable.
7. Reserve inventory đúng offer; uniqueness `(order_id, offer_id)`.
8. Tạo payment snapshot cho customer total; lưu idempotent checkout response.
9. Sau commit, outbox phát `PartnerOrderCreatedEvent` vào thời điểm business đã chốt. Đề xuất chỉ notify partner sau payment success để tránh đơn chưa trả; nếu cần pre-payment visibility phải có state riêng `AWAITING_PAYMENT`.

Payment Service tiếp tục xử lý dòng tiền **Customer → Platform**. Payment success commit offer reservations và kích hoạt fulfillment. Không gửi một charge cho từng partner.

Order tổng hợp trạng thái bằng projection/rules, không để partner ghi trực tiếp `Order.status`. Ví dụ:

- Tất cả PartnerOrder delivered → Order fulfillment `DELIVERED`.
- Có mix delivered/cancelled → `PARTIALLY_FULFILLED` hoặc trạng thái tổng được owner xác nhận.
- Một PartnerOrder rejected trước fulfillment → policy cancel line/partial order hoặc cancel toàn order; đây là owner decision.
- Customer payment state tách khỏi fulfillment state để tránh enum Order phình và transition mơ hồ.

### 9.4 State machine PartnerOrder

Happy path:

```text
NEW -> ACCEPTED -> PACKING -> READY_TO_SHIP -> SHIPPED -> DELIVERED
```

Error/return path:

```text
NEW -> REJECTED
ACCEPTED|PACKING|READY_TO_SHIP -> CANCELLED
DELIVERED -> RETURN_REQUESTED -> RETURNED
```

| Command | Actor | Pre-state | Side effect | Event | Duplicate/audit/order tổng |
|---|---|---|---|---|---|
| create | System checkout/payment flow | none | Snapshot totals/commission; assign partner | `PartnerOrderCreatedEvent` | Unique order+partner; audit SYSTEM; aggregate count. |
| accept | OWNER/MANAGER/ORDER_STAFF | NEW | `acceptedAt`; start SLA | `PartnerOrderAcceptedEvent` | Command key + version; duplicate same request returns current; aggregate fulfillment. |
| reject | OWNER/MANAGER/ORDER_STAFF | NEW | reason; release/cancel affected allocation per policy; refund/adjustment if paid | `PartnerOrderRejectedEvent` | Reason required; cannot repeat with new reason silently; recalc aggregate. |
| packing | OWNER/MANAGER/ORDER_STAFF | ACCEPTED | `packedAt` optional; no money mutation | `PartnerOrderPackingEvent` | Conditional update; audit. |
| ready-to-ship | OWNER/MANAGER/ORDER_STAFF | PACKING | `readyToShipAt`; handoff eligibility | `PartnerOrderReadyToShipEvent` | Conditional update; audit. |
| ship | OWNER/MANAGER/ORDER_STAFF or trusted carrier | READY_TO_SHIP | tracking snapshot, `shippedAt` | `PartnerOrderShippedEvent` | Unique carrier/tracking operation; aggregate. |
| deliver | trusted carrier/admin; partner only if policy allows | SHIPPED | `deliveredAt`; starts settlement/return window | `PartnerOrderDeliveredEvent` | Delivery evidence; business key; aggregate and settlement eligibility. |
| cancel | Admin/system; partner role only before shipment per policy | ACCEPTED/PACKING/READY_TO_SHIP | reason; compensation/refund/release rules | `PartnerOrderCancelledEvent` | Concurrent accept/cancel protected by version; aggregate/payment compensation. |
| request return | Customer/admin | DELIVERED | return case; no immediate restock/payout reversal | `PartnerOrderReturnRequestedEvent` | One active return per line/case; audit. |
| returned | Admin/warehouse | RETURN_REQUESTED | inspected inventory adjustment; refund/settlement line | `PartnerOrderReturnedEvent` | Evidence + condition; aggregate and finance adjustment. |

Mọi command kiểm `system permission AND active membership AND same partner resource AND allowed partner status`, có correlation/idempotency key, audit và outbox cùng transaction.

### 9.5 Hoa hồng, discount funding và settlement

Hai dòng tiền độc lập:

```text
Customer -> Platform  (Payment Service, charge/refund)
Platform -> Partner   (Settlement domain, MVP manual payout marking)
```

Line formula:

```text
partnerPayable =
    grossProductRevenue
    - partnerFundedDiscount
    - platformCommission
    - refundedAmount
    - partnerFees
    + shippingPayable
    + manualAdjustment
```

Order/settlement tổng là sum các immutable line components, không suy lại từ current rules.

Funding policy phải được snapshot:

- Platform-funded promotion: giảm customer total nhưng không giảm partner payable; platform chịu cost.
- Partner-funded: phân bổ vào đúng partner/order items và trừ payable.
- Shared: lưu hai allocation riêng, tổng bằng customer discount.
- Shipping: tách customer charge, carrier cost và partner share; không gộp vào merchandise commission nếu policy không nói vậy.
- Cancel/failed delivery trước eligibility: sale không vào settlement hoặc tạo reversal line nếu đã close.
- Full/partial refund: tạo refund allocation theo order item/partner; nếu settlement OPEN/CALCULATED thì cập nhật kỳ đó, nếu APPROVED/PAID thì carry-forward kỳ sau bằng adjustment/reversal line.
- Manual adjustment: admin finance only, reason + evidence + four-eyes approval nếu vượt threshold.

MVP settlement:

1. Worker idempotent chọn eligible delivered lines trong `[periodStart, periodEnd)` theo UTC và lock/business uniqueness.
2. Tạo `Settlement(CALCULATED)` + traceable lines.
3. Admin finance review, thêm adjustment hợp lệ, chuyển `UNDER_REVIEW -> APPROVED`.
4. Sau khi chuyển khoản ngoài hệ thống, admin `mark-paid` với payment reference và paidAt.
5. Không tích hợp payout tự động trong MVP.

## 10. Phân quyền và bảo mật mục tiêu

### 10.1 Hai lớp role

System roles:

```text
USER | PARTNER | ADMIN
```

Giữ `SELLER` tạm thời cho compatibility. Không map `SELLER -> PARTNER` chỉ bằng JWT; phải có backfilled active `PartnerMember`. Sau cutover, token/authorization dùng `PARTNER` capability và membership DB/policy service.

Partner roles:

```text
OWNER | MANAGER | PRODUCT_STAFF | ORDER_STAFF | FINANCE_STAFF
```

Permission matrix MVP:

| Capability | OWNER | MANAGER | PRODUCT_STAFF | ORDER_STAFF | FINANCE_STAFF | ADMIN |
|---|---:|---:|---:|---:|---:|---:|
| Profile/member management | yes | limited | no | no | no | all/review |
| Offer create/update/submit | yes | yes | yes | no | no | moderate |
| Inventory adjustment | yes | yes | yes | limited/no | no | override with audit |
| PartnerOrder fulfillment | yes | yes | no | yes | read | all/exception |
| Settlement read | yes | yes | no | no | yes | all |
| Settlement approve/mark-paid | no | no | no | no | no | admin finance permission only |

### 10.2 Authorization invariant

Mọi partner API phải thỏa:

```text
authenticated system principal has capability
AND active PartnerMember has required internal role
AND requested resource.partner_id equals membership.partner_id
AND Partner.status allows the command
AND resource state allows the command
```

Implementation nên tập trung ở `PartnerAuthorizationService`/policy component và tenant-scoped repository query, không copy username comparisons trong controller/service. Với `/me`, partner context lấy từ trusted header/path selection đã validate membership hoặc explicit partner ID; không suy từ client payload.

### 10.3 Threats phải chặn

- IDOR trên `productId`, `offerId`, `partnerId`, `partnerOrderId`, `settlementId`, document ID.
- Seller/partner A xóa hoặc sửa resource B; partner A xem order/settlement B.
- Finance staff sửa product; Product staff approve settlement; partner tự approve profile/offer.
- Client đặt `status`, `partnerId`, `approvedBy`, `price snapshot`, commission, discount funding hoặc payable.
- Over-posting nested `Category`, `Partner`, `Member` entities.
- Suspended partner tiếp tục command qua JWT cũ; authorization phải kiểm DB/current cached status và invalidation event.
- Race approve/accept/cancel/settlement bằng optimistic lock + conditional transitions.
- File upload abuse, PII/tax/bank document leakage, sensitive payload trong logs/events.
- Public registration privilege escalation; system role changes chỉ trusted admin workflow.

SecurityConfig target deny-by-default, endpoint matchers coarse-grained và method/domain policy fine-grained. Rate limit auth, application submit, upload, moderation commands; restrict actuator/Swagger; validate JWT issuer/audience khi triển khai production hardening.

## 11. API mục tiêu

Các API dưới đây là proposal, chưa tồn tại. Command DTO không chứa authoritative fields. Tất cả command quan trọng nhận `Idempotency-Key`; version có thể dùng `If-Match` hoặc body `expectedVersion` server-validated.

### 11.1 Partner application/profile

```http
POST /api/partners/applications
GET  /api/partners/me
PUT  /api/partners/me
GET  /api/partners/me/status
POST /api/partners/me/submit
```

Nếu user có nhiều partner, `/me` cần active partner context rõ; phương án sạch hơn là `/api/partners/{partnerId}/...` với membership policy, còn `/me` chỉ trả memberships/default context.

### 11.2 Partner members

```http
GET    /api/partners/me/members
POST   /api/partners/me/members
PUT    /api/partners/me/members/{memberId}
DELETE /api/partners/me/members/{memberId}
```

Invite bằng user ID/email lookup an toàn; không trả thông tin account ngoài scope; transfer-owner command riêng được khuyến nghị.

### 11.3 Admin partner management

```http
GET  /api/admin/partners
GET  /api/admin/partners/{partnerId}
POST /api/admin/partners/{partnerId}/approve
POST /api/admin/partners/{partnerId}/reject
POST /api/admin/partners/{partnerId}/suspend
POST /api/admin/partners/{partnerId}/restore
POST /api/admin/partners/{partnerId}/terminate
```

### 11.4 Partner offers và moderation

```http
POST   /api/partner/offers
PUT    /api/partner/offers/{offerId}
GET    /api/partner/offers
GET    /api/partner/offers/{offerId}
POST   /api/partner/offers/{offerId}/submit
POST   /api/partner/offers/{offerId}/archive
POST   /api/partner/offers/{offerId}/inventory-adjustments
POST   /api/partner/offers/{offerId}/approve

GET    /api/admin/product-submissions              (not yet implemented)
GET    /api/admin/product-submissions/{submissionId}
POST   /api/admin/product-submissions/{submissionId}/approve
POST   /api/admin/product-submissions/{submissionId}/reject
POST   /api/admin/product-submissions/{submissionId}/suspend
```

Không dùng generic PUT để set stock tuyệt đối nếu có concurrent reservation; inventory adjustment là delta + reason + idempotency key.

### 11.5 Partner orders

```http
GET  /api/partner/orders
GET  /api/partner/orders/{partnerOrderId}
POST /api/partner/orders/{partnerOrderId}/accept
POST /api/partner/orders/{partnerOrderId}/reject
POST /api/partner/orders/{partnerOrderId}/packing
POST /api/partner/orders/{partnerOrderId}/ready-to-ship
POST /api/partner/orders/{partnerOrderId}/ship
POST /api/partner/orders/{partnerOrderId}/deliver
POST /api/partner/orders/{partnerOrderId}/cancel
POST /api/partner/orders/{partnerOrderId}/return-request
POST /api/partner/orders/{partnerOrderId}/approve-return
```

List/detail luôn tenant-filter ở query. Transition command body chỉ chứa reason/tracking/evidence fields phù hợp, không chứa next status.

### 11.6 Settlement

```http
GET  /api/partner/settlements
GET  /api/partner/settlements/{settlementId}

GET  /api/admin/settlements
POST /api/admin/settlements/calculate
POST /api/admin/settlements/{settlementId}/approve
POST /api/admin/settlements/{settlementId}/mark-paid
POST /api/admin/settlements/{settlementId}/adjustments
```

`calculate` có business uniqueness partner+period+currency; `approve` và `mark-paid` có state/version/idempotency; payment reference không được ghi đè sau PAID.

## 12. Event và notification

### 12.1 Envelope chung

Mọi critical event dùng:

```text
eventId, eventType, eventVersion, occurredAt
correlationId, aggregateId, producer, idempotencyKey
```

- `eventId`: unique message ID.
- `idempotencyKey`: stable business operation key, không thay bằng event ID.
- Kafka key: aggregate cần ordering (`partnerId`, `offerId`, `partnerOrderId` hoặc `settlementId`).
- Producer outbox và consumer inbox marker phải commit cùng business transaction.
- Contract schema/version nằm ở shared schema artifact hoặc compatibility tests; không copy records thủ công giữa modules mà không kiểm compatibility.

### 12.2 Event catalog mới

```text
PartnerApplicationSubmittedEvent
PartnerApprovedEvent
PartnerRejectedEvent
PartnerSuspendedEvent

PartnerOfferSubmittedEvent
PartnerOfferApprovedEvent
PartnerOfferRejectedEvent
PartnerOfferSuspendedEvent

PartnerOrderCreatedEvent
PartnerOrderAcceptedEvent
PartnerOrderRejectedEvent
PartnerOrderShippedEvent
PartnerOrderDeliveredEvent

SettlementCalculatedEvent
SettlementApprovedEvent
SettlementPaidEvent
```

Event chỉ chứa IDs, snapshot tối thiểu cần thiết và redacted contact; không chứa tax document, bank proof, password, token, PaymentMethod ID ngoài payment-specific protected contract.

### 12.3 Notification target

- Admin: application/submission mới, reconciliation/settlement exception.
- Partner: approved/rejected/suspended, offer decision, order mới, SLA, settlement approved/paid.
- Customer: fulfillment changes qua aggregate/customer notification policy.

Trước khi mở rộng partner notification, hoàn thiện Notification V2 thành receive transaction → durable send operation → worker send ngoài transaction → terminal/UNKNOWN/retry/dead state. Email template phải escape user content; sender phải propagate failure. Không tuyên bố exactly-once email.

## 13. AI hỗ trợ, không nằm trong critical transaction

### 13.1 Use cases

1. **Phân loại sản phẩm:** input tên/mô tả/ảnh/attributes; output suggested category, missing attributes, keywords, confidence.
2. **Duplicate detection:** text/SKU/brand/attributes + image embedding; trả candidate IDs, similarity và explanation.
3. **Content moderation:** spam, inappropriate image, incomplete description, anomalous price, possible policy violation.
4. **Partner risk score:** cancellation, late delivery, return, complaints, violations, anomalous price changes; output score, reasons, model version.

### 13.2 Guardrails và fallback

- AI chạy async sau khi draft/submission đã durable; timeout không rollback checkout/order/payment/settlement.
- Confidence dưới threshold → `NEEDS_HUMAN_REVIEW`, không auto reject/delete/suspend.
- Provider lỗi → queue retry bounded; sau threshold bỏ AI result và tiếp tục manual review.
- Lưu model/provider/version, prompt policy version, input references, output, confidence và reviewer decision; không gửi PII/tax/bank documents nếu không có approved data-processing policy.
- AI score chỉ là signal. Admin phải quyết định moderation/suspension; mọi quyết định có reason/audit.
- MVP có thể chỉ cung cấp category suggestion và duplicate warning; risk scoring để Phase 5 khi dữ liệu đủ chất lượng.

## 14. Migration additive và cutover

Không sửa V1–V6 Main, V1–V2 Payment/Notification. Rollback theo hướng roll-forward bằng migration sửa lỗi mới và feature flag; không down-migrate destructive trên production.

### 14.1 Trình tự

1. Data audit: duplicate username/email/tax candidate, seller không hợp lệ, products null owner, orphan orders/items, stock invariant.
2. Tạo `partners`, `partner_members`, `partner_documents`, audit tables và constraints/indexes additive.
3. Backfill mỗi legacy SELLER hợp lệ thành một Partner `APPROVED` compatibility record và membership `OWNER`.
4. Tạo target catalog/offer/submission tables; tạo một offer cho mỗi legacy Product, lưu `legacy_product_id` mapping unique.
5. Dual-read: public/product/cart response thêm offer mapping nhưng giữ legacy fields. Dual-write có thời hạn cho price/stock nếu cả V1/V2 còn phục vụ.
6. Chuyển cart item sang `offer_id` nullable trước, backfill, rồi code yêu cầu offer cho cart mới; không tự chọn lại offer khi mapping ambiguity.
7. Mở rộng order items snapshot và inventory reservation với offer/partner; tạo PartnerOrder cho order mới.
8. Backfill order lịch sử chỉ khi lineage đủ chắc chắn. Nếu product→seller tại thời điểm lịch sử không chứng minh được, đánh dấu legacy/unassigned và không giả lập settlement payable.
9. Tạo commission rules, settlement/lines, indexes/unique/FK/check constraints.
10. Cutover read/write, disable V1 endpoints/listeners, theo dõi reconciliation.
11. Chỉ sau ổn định mới `NOT NULL` target columns và ngừng/loại legacy `Product.user_id`, product price/stock, `SELLER` role.

### 14.2 Backfill rules

- Seller hợp lệ: active user có role SELLER và ít nhất một product → partner code deterministic `LEGACY-{userId}`, tax code nullable/needs verification, status compatibility được policy xác nhận. Khuyến nghị `APPROVED` nhưng flag `legacyVerificationRequired` để không làm ngừng hệ thống.
- SELLER không product: vẫn có thể tạo draft/legacy partner nếu business muốn giữ quyền; nếu không, chỉ tạo membership khi user nộp application.
- Product không seller: đưa vào quarantine report; admin map partner/platform-owned hoặc archive. Không gán ngẫu nhiên.
- Username trùng: DB hiện chưa unique. Dùng immutable user ID làm key; chặn cutover constraint cho đến khi merge/rename theo manual case list.
- Một seller có nhiều account: không tự merge theo email/name. Owner quyết định cùng partner và tạo nhiều memberships sau xác minh.
- Một user thuộc nhiều partner: backfill một partner per legacy seller user trước; cho phép admin merge partner records bằng controlled tool sau, giữ redirect/mapping audit.
- Legacy orders: snapshot partner từ `order_items.product_id -> legacy_product_id -> offer -> partner` chỉ nếu mapping immutable tại cutover. Order V1 không có order_items không đủ dữ liệu thì không backfill financial settlement.

### 14.3 Roll-forward safety

- Mỗi migration nhỏ, resumable, có pre/post verification query và reconciliation counts.
- Backfill theo batches với mapping table/checkpoint; idempotent `INSERT ... SELECT` bằng unique business keys.
- Feature flags: partner read, offer cart, partner split, commission snapshot, settlement calculation.
- Nếu lỗi, tắt feature mới, giữ bảng/cột additive, deploy code sửa và migration Vnext; không sửa checksum migration đã chạy.

## 15. Kế hoạch kiểm thử

### 15.1 Unit tests

- Partner/offer/PartnerOrder/settlement state transitions và invalid transitions.
- Commission precedence, time range, rate/fixed fee, rounding và immutable snapshot.
- Settlement formula/funding/refund/carry-forward/manual adjustment.
- Ownership, membership role, partner status, last-owner invariant.
- OrderItem snapshot đầy đủ partner/offer/commission/payable.
- AI timeout/error/low-confidence/manual fallback.

### 15.2 Integration tests

- Application draft/submit/duplicate tax/approve/reject/suspend/restore.
- Approval tạo OWNER membership idempotently; concurrent admins.
- Offer create/submit/moderate/suspend/archive và public sellable predicate.
- Multi-partner checkout tạo một Order, N PartnerOrder, đúng snapshots và offer reservations.
- Payment success kích hoạt fulfillment; payment failure/expiry release đúng offer inventory.
- Full refund tạo đúng settlement reversal; partial refund khi được bật.
- Duplicate event/event business key; outbox retry/dead-letter; inbox cùng transaction.
- Notification durable receive/send worker và SMTP ambiguity.
- Migration test trên MySQL với representative legacy anomalies.

### 15.3 Security tests

- Seller A không sửa/xóa product Seller B ngay trong compatibility phase.
- Partner A không list/get/transition PartnerOrder B và không đọc Settlement/Document B.
- Finance staff không sửa offer; Product staff không approve/mark-paid settlement.
- Client không tự đặt status, partnerId, commission, partnerPayable, approval actor hoặc stock counters.
- IDOR fuzz cho mọi resource ID; mass assignment với unknown/protected fields bị reject.
- Suspended partner bị chặn partner commands nhưng user vẫn dùng customer flow và partner khác.
- Public register gửi `ADMIN`/`PARTNER` bị reject/ignored; only trusted provisioning creates ADMIN.
- Upload type/size/path/checksum access; signed URL tenant scope.

### 15.4 Concurrency và reliability tests

- Hai checkout mua offer cuối: chỉ một reservation thành công.
- Hai admin approve cùng Partner/Offer: một transition/event/membership.
- Hai workers calculate cùng settlement period: một active settlement/line set.
- Refund và settlement calculation đồng thời: deterministic lock order/carry-forward, không double payable.
- Partner accept và admin cancel đồng thời: một state thắng, side effects/event đúng một lần.
- Outbox publish thành công nhưng mark DB fail; duplicate Kafka được inbox bỏ qua.
- Worker crash sau external Stripe/SMTP call; stable idempotency/reconciliation behavior.

### 15.5 Test gates

- Phase 0: toàn bộ backend reactor và UI unit tests xanh; không chấp nhận 15 ProductController failures hiện tại.
- Mỗi migration chạy trên MySQL 8, không chỉ H2.
- Contract tests Event V2 giữa producer/consumer.
- Performance: multi-partner checkout lock/query count, tenant list pagination/index, settlement batch volume.

## 16. Lộ trình triển khai

### Phase 0 — Đồng bộ và ổn định hiện trạng

- Chốt Product–Partner model B và owner decisions blocking.
- Sửa P0/P1 ownership/security/JPA/refund/notification defects.
- Đưa tests về xanh; xác lập baseline metrics.
- Cutover checkout/payment/refund V2; feature-disable/remove V1 command paths an toàn.
- Chuẩn hóa event envelope/naming và Notification durable send.

Exit: không còn client-authoritative order/payment path; Seller A/B security tests xanh; migrations V1–V6 validate; V2 reconciliation/alerts vận hành được.

### Phase 1 — Partner foundation

- Partner, PartnerMember, PartnerDocument metadata, application/review/suspend/restore/terminate.
- Tenant authorization policy và audit.
- Backfill SELLER compatibility và frontend application/admin review cơ bản.

### Phase 2 — Partner product management

- Catalog Product + PartnerOffer, submission/moderation, inventory theo offer.
- Cart chọn offer, public sellable projection, soft archive.
- Partner product dashboard và admin moderation dashboard.

### Phase 3 — Partner orders

- PartnerOrder split trong checkout, expanded immutable OrderItem snapshot.
- Fulfillment state machine, aggregate order projection, partner order dashboard.
- Outbox events và durable notifications.

### Phase 4 — Commission và settlement

- CommissionRule engine/snapshot.
- Settlement/SettlementLine calculation, review, approve, manual mark-paid.
- Refund/cancel/failed delivery adjustments và finance reports.

### Phase 5 — AI

- Category suggestion, duplicate detection, moderation assistance.
- Risk scoring sau khi đủ data/SLA labels.
- Human review, model monitoring, fallback và audit.

## 17. Acceptance criteria

### MVP business

- Admin có thể review/approve/reject/suspend/restore partner với audit.
- Approved partner quản lý nhiều members và role-scope đúng; user có thể thuộc nhiều partner nếu owner xác nhận đề xuất.
- Partner tạo/submit offer; admin moderate; chỉ sellable offer được public/checkout.
- Một checkout chứa offer của ít nhất hai partner tạo đúng một Order và hai PartnerOrder.
- Mỗi OrderItem giữ partner/offer/price/commission/payable snapshot bất biến.
- Inventory reserve/commit/release đúng offer dưới concurrency.
- Partner chỉ thấy và transition order của mình.
- Commission rule precedence deterministic và lịch sử không đổi khi rule mới được cấu hình.
- Settlement có traceable lines; admin calculate/review/approve/mark-paid thủ công; partner read-only.
- Full refund tạo financial/settlement adjustment đúng và không auto-restock.

### Reliability/security

- Critical events dùng transactional outbox/inbox; duplicate không tạo duplicate state/line/notification operation.
- Command quan trọng idempotent và conflict payload được phát hiện.
- Không public self-register ADMIN; không V1 client-authoritative order/payment.
- IDOR/mass-assignment/tenant tests xanh.
- Suspended partner bị chặn đúng scope, không khóa toàn bộ user.
- Backend/UI/migration/contract test gates xanh; metrics/alerts cho dead-letter, reconciliation, settlement mismatch.

### AI

- AI outage không chặn transaction.
- Low-confidence/manual fallback hoạt động; không auto suspend/reject/delete trong MVP.
- Output lưu model version/confidence/explanation và reviewer decision.

## 18. File-level implementation plan

Đây là vị trí dự kiến; tên package có thể tinh chỉnh nhưng phải giữ domain boundary.

| Khu vực | Files hiện tại cần sửa | Files/package mới dự kiến |
|---|---|---|
| Security/roles | `security/SecurityConfig.java`, `services/AuthServiceImpl.java`, `model/dto/auth/RegisterDTO.java`, `utils/Role.java` | `security/PartnerAuthorizationService`, permission policies, member context resolver |
| Legacy ownership | `model/User.java`, `model/Product.java`, `services/ProductServiceImpl.java`, `repositories/ProductRepository.java`, `controllers/ProductController.java`, `model/dto/ProductDTO.java` | Compatibility mapper and tenant-scoped queries |
| Partner foundation | none | `model/partner/{Partner,PartnerMember,PartnerDocument,...}`, DTOs, repositories, services, controllers, state policies |
| Offer/catalog | `Product`, cart/product DTO/services/controllers | `model/offer/PartnerOffer`, ProductRevision/Submission, repositories/services/controllers/moderation |
| Checkout/inventory | `CheckoutService.java`, `CheckoutExpiryJob.java`, cart services/DTO | Offer inventory reservation/adjustment service; PartnerOrder creation; OrderItem domain mapping |
| Orders | `model/Order.java`, `OrderRepository`, `OrderServiceImpl`, order DTO/controller | `PartnerOrder`, repository/service/controller, state machine, aggregate projection |
| Commission/settlement | none | commission engine/rules; Settlement/Line entities, calculators, repositories, admin/partner controllers |
| Events | Main/Payment/Notification `kafka/events`, `Outbox*`, `InboxService` | Partner/offer/order/settlement contracts, schema compatibility tests |
| Refund | `RefundService`, `RefundResultV2Consumer` | Refund allocation and settlement adjustment handlers |
| Notification | `NotificationEventListener`, `NotificationServiceImpl`, `EmailNotificationSender`, repository/model | Durable send worker/claim repository, partner templates |
| Frontend | `Main.tsx`, `Header.tsx`, product/cart/order APIs/pages | partner application/dashboard, members, offers, orders, settlements, admin review/moderation/finance routes |
| Migrations | không sửa existing migrations | Main V7+ additive partner/offer/order/commission/settlement/backfill/index/constraints; Payment/Notification V3+ nếu cần |
| Tests | current server/payment/notification/UI/IT tests | partner domain/integration/security/concurrency/contract/migration suites |

## 19. Owner decision checklist

- [ ] 1. Xác nhận Product–Partner: đề xuất **nhiều partner cùng bán qua PartnerOffer (B)**.
- [ ] 2. Một user có thể thuộc nhiều partner? Đề xuất **có**, active context explicit.
- [ ] 3. Đối tác có nhiều kho trong MVP? Đề xuất **không**; một inventory pool/offer, schema không khóa đường mở rộng.
- [ ] 4. Ai chịu chi phí promotion: platform, partner hay shared? Cần policy + snapshot funding.
- [ ] 5. Commission tính trên gross hay net của partner-funded discount/refund/tax/shipping?
- [ ] 6. Revenue eligible khi shipped, delivered hay hết return window? Đề xuất delivered + configurable hold.
- [ ] 7. Refund điều chỉnh settlement mở hay carry-forward kỳ sau nếu đã approved/paid?
- [ ] 8. Partial refund trong MVP? Đề xuất deferred; thiết kế line-level để không chặn tương lai.
- [ ] 9. Settlement theo tuần hay tháng, timezone/cutoff nào?
- [ ] 10. Payout thủ công hay tự động? Đề xuất manual mark-paid trong MVP.
- [ ] 11. Tài liệu/hợp đồng pháp lý nào bắt buộc theo quốc gia/category?
- [ ] 12. Partner suspended: offers ẩn ngay; đơn đang xử lý cho tiếp tục, admin takeover hay cancel?
- [ ] 13. Có AI trong MVP không? Đề xuất chỉ category/duplicate suggestion hoặc deferred.
- [ ] 14. AI chỉ cảnh báo hay auto reject? Đề xuất **chỉ cảnh báo/human decision**.
- [ ] 15. Retention, encryption, object storage region, access log và deletion policy cho documents/audit/PII?
- [ ] 16. Partner có được thay đổi price mà không re-review? Threshold/cooldown?
- [ ] 17. Khi một PartnerOrder reject, cancel toàn Order hay partial fulfillment/refund?
- [ ] 18. Tax/VAT và shipping share nằm trong partner payable/commission base thế nào?
- [ ] 19. Legacy SELLER được auto-approved hay phải hoàn tất verification trong grace period?
- [ ] 20. Một partner có nhiều offer cho cùng Product không?

## 20. Risk register

| Risk | Xác suất/ảnh hưởng | Mitigation | Trigger/owner |
|---|---|---|---|
| Sai lineage khi backfill seller/product/order | Cao/Cao | Mapping table, quarantine, không settlement order không chứng minh được | Reconciliation mismatch; Data owner |
| Double reservation/oversell khi chuyển stock sang offer | TB/Cao | Lock order, conditional update, version/check constraints, concurrency tests | Negative/over-reserved metric; Inventory owner |
| Double charge/refund do V1/V2 coexistence | TB/Rất cao | Feature-disable V1, stable keys, inbox/outbox, reconciliation | Duplicate external IDs; Payment owner |
| Cross-tenant IDOR | Cao/Rất cao | Central policy + tenant-scoped query + security tests | Forbidden access alert; Security owner |
| Commission/discount policy mơ hồ | Cao/Cao | Owner decisions, immutable funding/rule snapshot, golden examples | Settlement disputes; Finance/Product |
| Refund sau settlement paid | Cao/Cao | Carry-forward reversal lines; no history rewrite | Negative payable; Finance owner |
| Notification false success/duplicate | Cao/TB | Durable send worker, propagate errors, UNKNOWN/manual retry | SMTP timeout; Notification owner |
| Partner suspension làm kẹt orders | TB/Cao | Explicit policy/admin takeover, state-specific authorization | Open orders at suspend; Operations |
| Marketplace B migration complexity | Cao/TB | 1 legacy product → 1 catalog + 1 offer; phased cart/order cutover | Unmapped cart/order lines; Platform owner |
| AI false positive/bias | TB/TB | Advisory only, confidence/explanation, human review/monitoring | Override/complaint rate; AI/Product |
| Documents/PII leakage | TB/Rất cao | Object storage encryption, signed URL, least privilege, retention/access audit | Access anomaly; Security/Legal |
| Test baseline che giấu regression | Cao/Cao | Phase 0 green gate; MySQL/contract/security/concurrency suites | Any failing required suite; Engineering |
| Settlement worker duplication | TB/Cao | Period business uniqueness, line keys, locks/idempotency | Duplicate line/period; Finance Engineering |
| Event contract drift giữa modules | TB/Cao | Shared schema/compatibility tests/version rules | Consumer deserialization/DLQ; Platform |

## 21. Implementation summary (2026-07-12)

### Domain Classification (verified against source code)

| Domain | Status | Key Evidence |
|--------|--------|-------------|
| Partner application | **IMPLEMENTED** | V7, Partner entity, PartnerService, AdminPartnerController |
| PartnerMember    | **IMPLEMENTED** | V7, PartnerMember entity, role/permission checks |
| PartnerOffer     | **IMPLEMENTED** | V8, PartnerOffer entity/service, CartItem.addOfferToCart |
| Cart integration | **IMPLEMENTED** | V11 (offer_id/partner_id), CartItem entity, CartItemController POST |
| Checkout/PartnerOrder | **IMPLEMENTED** | V9 (partner_orders), V12 (AWAITING_PAYMENT), `createPartnerOrdersAtCheckout` |
| Inventory reservation | **IMPLEMENTED** | V4+V12+V13, inventory_source_type, inventory_source_key |
| Commission       | **IMPLEMENTED** | V10, CommissionRule entity, CommissionServiceImpl |
| Settlement       | **IMPLEMENTED** | V10, Settlement/SettlementLine entities, SettlementServiceImpl |
| Refund           | **PARTIAL** | V2 consumer exists, settlement reversal added in V13 fix |
| Fulfillment state machine | **IMPLEMENTED** | PartnerOrderServiceImpl with full transitions |
| Audit logging    | **IMPLEMENTED** | V13 `partner_order_audit` table, written on every transition |
| Idempotency      | **PARTIAL** | Checkout uses idempotency-key + request_hash; PartnerOrder uses status idempotency |
| End-to-end test  | **IMPLEMENTED** | Phase2MigrationMySqlTest.endToEndPartnerWorkflow() |

### Phase A — Security & Application Flow

- **`PartnerAuthorizationService`**: Replaced `ordinal()`-based role comparison with explicit permission methods (`requireOfferWrite`, `requireOrderFulfillment`, `requireInventoryWrite`, `requireSettlementRead`, `requireMemberManagement`, `requireOfferRead`, `requireOrderRead`). Each method checks active membership + partner active + role in allowed set.
- **`SecurityConfig`**: Added `.authenticated()` matchers for `/api/partners/applications`, `/api/partners/me/**`, and `/api/partners/me` so authenticated non-PARTNER users can access application/self-service endpoints.
- **`PartnerServiceImpl.approvePartner`**: Grants system `PARTNER` role to applicant; prevents duplicate `OWNER` member creation; flushes role changes immediately.
- **`SettlementServiceImpl`**: Replaced `findAll()` with targeted `findByPartnerIdAndStatusAndDeliveredAtBetween()` query. All partner-scoped methods use `requireSettlementRead`.
- **V11 migration**: Added `offer_id` and `partner_id` columns to `cart_items` with foreign keys.

### Phase B — Cart/Checkout Integration

- **`CartItem` model**: Added `offerId` (Long, nullable) and `partnerId` fields.
- **`CheckoutService` cart query**: Updated to LEFT JOIN `partner_offers`; uses `COALESCE(po.price, p.price)` for pricing; checks offer stock when `offer_id` is present.
- **Inventory reservation**: Reserves offer stock (`partner_offers.reserved_quantity`) for offer items, product stock for regular items. Uses `inventory_source_type` and `inventory_source_key` for proper unique constraint.
- **Order item snapshot**: Includes `offer_id`, `partner_id`, and `currency` in INSERT.
- **`createPartnerOrdersAtCheckout`**: Creates `PartnerOrder(AWAITING_PAYMENT)` records during checkout, grouped by partner, with commission calculation and `partnerPayableAmount` snapshot. Payment success transitions `AWAITING_PAYMENT → NEW` via `activatePartnerOrdersAfterPayment`. Payment failure/expiry transitions `AWAITING_PAYMENT → CANCELLED` via `cancelAwaitingPartnerOrders`.

### Phase C — PartnerOrder Workflow

- **Full state machine implemented**: `AWAITING_PAYMENT → NEW → ACCEPTED → PACKING → READY_TO_SHIP → SHIPPED → DELIVERED` (happy path); `NEW → REJECTED`; `ACCEPTED/PACKING/READY_TO_SHIP → CANCELLED`; `DELIVERED → RETURN_REQUESTED → RETURNED`.
- **Audit logging**: Every transition writes to `partner_order_audit` with actor, from/to status, reason, and idempotency key.
- **Outbox events**: Every transition publishes via `outbox_events`.

### Phase D+E — Commission & Settlement

- **Commission calculation**: Uses `commission_rules` table with specificity/priority ordering (product > category > partner > global). Filters by currency. Corrected from `max()` to `min()` specificity comparator to ensure most-specific rule wins.
- **Settlement service**: Idempotent calculation with unique period+partner+currency key. Transition chain: CALCULATED → UNDER_REVIEW → APPROVED → PAID. Refund creates carry-forward reversal lines when settlement is APPROVED/PAID, or adjusts current OPEN/CALCULATED settlement.
- **All admin endpoints** (`approveSettlement`, `markPaid`, `addAdjustment`) gated by `ADMIN` role in `SecurityConfig`.

### Key Bug Fixes (V13+)

1. **P0 — `offer_id = 0` sentinel**: V12 used sentinel `0` for legacy products, conflicting with FK constraints. V13 reverts to `NULL` semantics: `offer_id IS NULL` → legacy Product, `offer_id IS NOT NULL` → PartnerOffer.
2. **P0 — CartItem `long offerId`**: Changed to `Long offerId` (nullable wrapper). Repository method signature updated accordingly.
3. **P0 — PartnerOrder created after payment**: Moved to checkout phase with `AWAITING_PAYMENT` status. Payment success → `NEW`, payment failure/expiry → `CANCELLED`.
4. **P0 — Commission specificity ordering**: `findBestMatch` used `max()` which selected global rules over product rules. Fixed to `min()` with reversed priority.
5. **P0 — Settlement non-idempotent**: `doCalculateSettlement` used `DELETE+recreate` on lines. Fixed to only recalculate for OPEN/CALCULATED, reject APPROVED/PAID.
6. **P1 — Commission currency filter**: Added `AND (currency IS NULL OR currency=?)` to rule query.
7. **P1 — Missing stock CHECK constraints**: Added `chk_product_stock` and `chk_offer_stock` constraints.
8. **P1 — Missing `inventory_source_key`**: Added generated column `CONCAT(inventory_source_type, ':', COALESCE(offer_id, product_id))` with unique `(order_id, inventory_source_key)`.
9. **P1 — Missing `order_items.currency`**: Added column.
10. **P1 — Refund settlement connection**: Added settlement reversal/carry-forward logic to `RefundResultV2Consumer.applyFinancialAndLoyaltyReversal`.
11. **P1 — CheckoutExpiryJob PartnerOrder**: Added cancellation of `AWAITING_PAYMENT` PartnerOrders on checkout expiry.
12. **P2 — Audit logging**: Added `partner_order_audit` table and writes on every PartnerOrder transition.
13. **P2 — Phase2MigrationMySqlTest**: Fixed `applicant_id` → `applicant_user_id`, removed `partner_offers.name` column reference.

### Tests

- 104 unit tests + 1 end-to-end MySQL 8 integration test across test classes.
- Phase2MigrationMySqlTest validates V12/V13 migrations, schema constraints, inventory reservations, and the full PartnerOrder lifecycle.

## 22. Tóm tắt thay đổi so với Smart Cart cũ

### 21.1 Những điểm đã thay đổi

- Trọng tâm chuyển từ tối ưu checkout/loyalty/payment sang quản lý Partner, membership, offer, partner fulfillment, commission và settlement.
- Checkout, inventory reservation, payment/refund, loyalty, promotion, idempotency, outbox/inbox và Kafka V2 được giữ lại như shared transaction foundation và được cập nhật theo source mới nhất.
- Nhận định cũ “chưa có checkout/reservation/refund/outbox/inbox” không còn đúng; commit `43b2f0d` đã có source/migrations, nhưng còn partial/cutover debt.
- Bổ sung mô hình Product catalog + PartnerOffer, PartnerOrder split, immutable commission/payable snapshot và auditable SettlementLine.
- Bổ sung tenant authorization hai lớp, migration SELLER, partner/admin frontend, AI advisory và risk register.

### 21.2 Những lỗi hiện tại cần sửa trước

1. Public registration có thể chọn ADMIN.
2. Seller xóa product không ownership check.
3. `User.products mappedBy = "id"` sai; phải là `"user"` trước khi loại legacy mapping.
4. Order/payment V1 client-authoritative/thiếu ownership và chạy song song V2.
5. Refund consumer dùng `refund.version` để update Order.
6. Notification sender swallow lỗi, V2 chưa durable send worker; payment V2 chưa phát notification.
7. Product public query không filter active/moderation; product owner nullable; category DTO over-posting.
8. Main test baseline fail 15 `ProductControllerTest`; Payment test runtime có 22 Mockito attach errors; Notification Service chạy riêng 36/36 pass.

### 21.3 Quyết định kiến trúc quan trọng nhất

Chọn **Product catalog + PartnerOffer (Phương án B)** ngay từ MVP, với backfill bảo thủ 1:1 cho legacy product. Đây là quyết định chi phối cart, inventory, OrderItem snapshot, promotion funding, commission và frontend; trì hoãn sẽ làm migration các bảng giao dịch khó hơn nhiều.

### 21.4 Phạm vi MVP đề xuất

Partner application/approval/suspend, multi-member RBAC, documents metadata, offer moderation/inventory, multi-partner checkout + PartnerOrder, fulfillment state machine, commission snapshot, settlement calculation/review/approve/manual mark-paid, audit/outbox/inbox và notification durable. Full refund được nối vào settlement; partial refund, multi-warehouse, automatic payout và AI risk automation deferred.

### 21.5 Câu hỏi còn chờ chủ dự án

Các câu hỏi blocking nhất là: xác nhận model B; multi-partner membership; promotion funding; commission base; fulfillment/settlement eligibility; refund carry-forward; split-order rejection policy; settlement cycle; legacy seller verification; suspension behavior; document retention; và AI có nằm trong MVP hay không. Checklist đầy đủ ở mục 19.
