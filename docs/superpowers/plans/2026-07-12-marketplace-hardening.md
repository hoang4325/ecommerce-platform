# Marketplace Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden marketplace checkout, refund, settlement, idempotency, supplier workflows, migrations, CI, and analysis documentation.

**Architecture:** Make the smallest safe changes around the existing Spring Boot/JDBC/JPA implementation. Preserve current schema lineage with additive Flyway migrations and focused tests that fail before each fix.

**Tech Stack:** Java 17, Spring Boot 3.0.4, Maven, Flyway, MySQL 8 Testcontainers, JUnit 5, Mockito, GitHub Actions.

## Global Constraints

- Status labels are `IMPLEMENTED`, `PARTIAL`, `BROKEN`, `MISSING`, `SCHEMA_ONLY`.
- Production code changes require a failing test first.
- Do not claim CI is green without successful workflow evidence.
- Do not implement frontend, AI, procurement ERP, automatic payout, FX conversion, or multi-warehouse.
- Do not store or log raw bank/document secrets.

---

### Task 1: Checkout PartnerOrder Insert

**Files:**
- Modify: `ecommerce-platform-server/src/main/java/com/yashmerino/ecommerce/services/CheckoutService.java`
- Test: `ecommerce-platform-server/src/test/java/com/yashmerino/ecommerce/services/CheckoutServiceTest.java`
- Test: `ecommerce-platform-server/src/test/java/com/yashmerino/ecommerce/migration/Phase2MigrationMySqlTest.java`

**Interfaces:**
- Consumes: `CheckoutService.checkout(UUID, CheckoutRequestDTO)`.
- Produces: valid `partner_orders` row with `AWAITING_PAYMENT`, `UNSETTLED`, and exact monetary snapshots.

- [ ] Write a failing unit test that captures the `INSERT INTO partner_orders` SQL and asserts it has no placeholder for `settlement_status`.
- [ ] Run `mvn -q -pl ecommerce-platform-server -Dtest=CheckoutServiceTest test` and verify the test fails on the current SQL.
- [ ] Fix the insert values to `VALUES (?,?,'AWAITING_PAYMENT',?,?,0,?,?,?,'UNSETTLED',0,NOW(),NOW())`.
- [ ] Add/extend a MySQL Testcontainers test executing that exact insert shape against migrated schema.
- [ ] Run `mvn -q -pl ecommerce-platform-server -Dtest=CheckoutServiceTest,Phase2MigrationMySqlTest test`.

### Task 2: Authoritative Offer Checkout And Locks

**Files:**
- Modify: `CheckoutService.java`
- Test: app-level MySQL Testcontainers checkout test under `ecommerce-platform-server/src/test/java/com/yashmerino/ecommerce/integration/`

**Interfaces:**
- Consumes: cart lines with selected `offer_id`.
- Produces: order item snapshots derived from `partner_offers`, `products`, and `partners`, not client payload.

- [ ] Write failing integration test for stale/mismatched cart offer/product data.
- [ ] Write failing concurrency test for two checkouts competing for one offer's stock.
- [ ] Change checkout query/validation to lock selected offer rows deterministically by offer ID and use offer price/currency/partner/product as authoritative.
- [ ] Run the focused integration tests.

### Task 3: Discount Funding And Deterministic Allocation

**Files:**
- Modify: `CheckoutService.java`
- Test: `CheckoutServiceTest.java` and integration checkout test.

**Interfaces:**
- Produces: customer discount allocation on order items; partner payable is not reduced by platform-funded discounts.

- [ ] Write failing test proving platform coupon does not reduce partner payable.
- [ ] Write failing test proving rounding remainder follows persisted `order_items.id` order.
- [ ] Allocate discounts from persisted item rows ordered by ID.
- [ ] Keep partner payable formula as `subtotal - commission` for current platform-funded coupon/points.
- [ ] Run focused checkout tests.

### Task 4: Refund Full-Only And Partner Snapshot Reversal

**Files:**
- Modify: `RefundService.java`
- Modify: `RefundResultV2Consumer.java`
- Test: `RefundServiceTest.java`
- Test: `RefundResultV2ConsumerTest.java`

**Interfaces:**
- Consumes: full refund request with reason only.
- Produces: partner financial reversal equal to stored partner payable snapshots.

- [ ] Add failing test that refund event amount equals order total.
- [ ] Add failing consumer test where customer refund amount differs from partner payable and reversal uses partner payable.
- [ ] Change reversal loop to use each `partner_payable_amount` snapshot for full refunds.
- [ ] Run focused refund tests.

### Task 5: Settlement Immutability And Refund Idempotency

**Files:**
- Modify: `RefundResultV2Consumer.java`
- Test: `RefundResultV2ConsumerTest.java`

**Interfaces:**
- Produces: open/calculated settlements can be adjusted; approved/paid settlements are only corrected by pending carry-forward.

- [ ] Write failing test that APPROVED/PAID settlement headers are not updated during refund reversal.
- [ ] Write failing replay test for duplicate refund financial event.
- [ ] Split open/calculated behavior from approved/paid behavior.
- [ ] Add duplicate-safe inserts for spend/settlement/pending/loyalty rows.
- [ ] Run focused refund tests.

### Task 6: Settlement Claim, Creation, And Residual Debt

**Files:**
- Modify: `PartnerOrderRepository.java`
- Modify: `SettlementServiceImpl.java`
- Test: `SettlementServiceImplTest.java`
- Test: integration settlement test.

**Interfaces:**
- Consumes: partner ID, period, currency.
- Produces: one calculated settlement per partner/period/currency, claiming only matching-currency unsettled orders.

- [ ] Write failing unit test that EUR partner order is excluded from USD settlement.
- [ ] Write failing duplicate-calculate integration test with one resulting settlement.
- [ ] Add currency predicate to claim query and method signature.
- [ ] Handle unique-key collision by loading existing row and returning conflict/current status as appropriate.
- [ ] Verify residual debt rows are applied once and residual remainder is carried once.

### Task 7: Partner Command Idempotency, Correlation, Audit, Outbox

**Files:**
- Modify: `PartnerOrderController.java`
- Modify: `PartnerOrderService.java`
- Modify: `PartnerOrderServiceImpl.java`
- Test: `PartnerOrderServiceImplTest.java`

**Interfaces:**
- Consumes: `Idempotency-Key`, optional `X-Correlation-Id`, command payload fields.
- Produces: payload-aware idempotency hash and correlated audit/outbox rows.

- [ ] Write failing test for same idempotency key with different reject reason.
- [ ] Write failing test that correlation ID reaches audit/outbox payload.
- [ ] Include command payload in canonical hash.
- [ ] Pass correlation ID through controller/service and audit/outbox writes.

### Task 8: Supplier Bank Lifecycle

**Files:**
- Modify: `PartnerBankAccountService.java`
- Modify: bank DTOs/controllers/repository as needed.
- Add migration for lifecycle/status metadata if needed.
- Test: `PartnerBankAccountServiceTest.java`

**Interfaces:**
- Produces: non-raw bank data lifecycle: create, verify, reject/deactivate/default if implemented minimally.

- [ ] Write failing tests for raw number not stored in plaintext and encrypted/tokenized field present.
- [ ] Write failing tests for verify/reject state transitions.
- [ ] Implement minimal encrypted/tokenized storage using environment-provided key or explicit non-storage policy.
- [ ] Keep responses masked only.

### Task 9: Member Invitation Workflow

**Files:**
- Modify: `PartnerMemberManagementService.java`
- Modify: member DTOs/controllers/repository/migrations as needed.
- Test: `PartnerMemberManagementServiceTest.java`

**Interfaces:**
- Produces: invitation token/expiry and invitee acceptance; no admin direct activation except explicit admin path if retained.

- [ ] Write failing tests for invite token creation, wrong-user acceptance failure, and expired token failure.
- [ ] Add token/expiry fields or invitation table.
- [ ] Implement accept workflow and remove manager-driven activation for normal invites.

### Task 10: Secure Document Upload Workflow

**Files:**
- Modify: `PartnerDocumentService.java`
- Modify: document DTOs/controllers/repository/migrations as needed.
- Test: document service/controller tests.

**Interfaces:**
- Produces: server-generated upload intent and metadata confirmation; rejects client-chosen object keys.

- [ ] Write failing tests rejecting direct object key submission and invalid content type/size.
- [ ] Add upload-intent DTO/service with server-generated object key.
- [ ] Add confirm endpoint that validates checksum/size/type policy.

### Task 11: MySQL Migration And App Integration Verification

**Files:**
- Modify: migration tests.
- Add: focused integration tests.
- Modify: `pom.xml` if Failsafe naming/config is required.

**Interfaces:**
- Produces: required Docker-backed MySQL migration/app tests under `clean verify`.

- [ ] Remove `disabledWithoutDocker = true` from required migration/integration tests.
- [ ] Add assertions for V16/V17 idempotency and nullable residual debt FKs.
- [ ] Add focused app-level tests for checkout/refund/settlement blockers.
- [ ] Run `mvn -B -pl ecommerce-platform-server -am clean verify`.

### Task 12: CI And Documentation

**Files:**
- Modify: `.github/workflows/backend-verify.yml`
- Rewrite: `ANALYSIS_AND_PLAN.md`

**Interfaces:**
- Produces: CI running `clean verify`, uploads Surefire/Failsafe, and documentation matching verified implementation.

- [ ] Change workflow command to `mvn -B -pl ecommerce-platform-server -am clean verify`.
- [ ] Upload Surefire and Failsafe report artifacts.
- [ ] Add skip gate for required Testcontainers tests.
- [ ] Rewrite `ANALYSIS_AND_PLAN.md` with current statuses and remove stale claims.
- [ ] Run local verification and report exact command/output.
