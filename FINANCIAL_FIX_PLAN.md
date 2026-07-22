# FINANCIAL FIX PLAN

## Commit: 8009b299cf984a6242c04587d2792fef3a1a5ea5

---

## Confirmed Bugs

| # | Bug | File:Line | Root Cause | Impact |
|---|-----|-----------|------------|--------|
| 1 | Partial refund uses full `partnerPayableAmount` per PartnerOrder | `RefundResultV2Consumer.java:122` | Allocates `po.partnerPayableAmount()` to every partner order — treats every partner as if they owe the full refund | Cross-partner financial error, wrong partner charged for refund |
| 2 | Loyalty reversal reverses ALL points on order | `RefundResultV2Consumer.java:176-222` | Zeroes all lots, restores all reservations per refund, not per partial amount | Double/multiple reversal of points, points can go negative |
| 3 | PARTIALLY_APPLIED adjustments lost in next period | `SettlementServiceImpl.java:121` | Queries only `status='PENDING'`, not `IN ('PENDING','PARTIALLY_APPLIED')` | Carry-forward debt silently disappears |
| 4 | `request_hash` uses literal placeholder | `RefundResultV2Consumer.java:142,174,206` | Hardcoded `"sha256-placeholder"` string | Idempotency check by hash is meaningless |
| 5 | Operations stuck PENDING forever | `V18` migration schema | No lifecycle cols (attempt_count, last_error, SKIPPED/FAILED state) | Orphaned operations block reconciliation |
| 6 | Event status not validated | `RefundResultV2Consumer.java:66-89` | Only checks SUCCEEDED/FAILED via else-if; unknown status falls through to markProcessed | Invalid events silently marked as processed |
| 7 | Settlement lock not PESSIMISTIC_WRITE | `SettlementServiceImpl.java:76` | `findById()` without `@Lock(PESSIMISTIC_WRITE)` after atomic upsert | Two transactions can both proceed concurrently |
| 8 | addAdjustment can make payable negative | `SettlementServiceImpl.java:280` | Direct `payable + adjustment` without `max(payable, 0)` guard | Settlement payable can go below zero |

---

## Schema Changes (V19)

### 1. `refund_items`
Per-OrderItem refund tracking for partial refund allocation.

```sql
CREATE TABLE refund_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    partner_order_id BIGINT NOT NULL,
    partner_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    gross_refund_amount DECIMAL(19,2) NOT NULL,
    discount_refund_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    commission_reversal_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    partner_payable_reversal_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ri_refund FOREIGN KEY (refund_id) REFERENCES refunds(id),
    CONSTRAINT fk_ri_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id),
    CONSTRAINT fk_ri_partner_order FOREIGN KEY (partner_order_id) REFERENCES partner_orders(id),
    INDEX idx_ri_refund (refund_id),
    INDEX idx_ri_order_item (order_item_id),
    INDEX idx_ri_partner_order (partner_order_id)
) ENGINE=InnoDB;
```

### 2. `loyalty_refund_allocations`
Tracks per-refund-item loyalty adjustments to prevent double reversal.

```sql
CREATE TABLE loyalty_refund_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_id BIGINT NOT NULL,
    refund_item_id BIGINT,
    point_lot_id BIGINT,
    reservation_id BIGINT,
    allocation_type VARCHAR(20) NOT NULL,
    points INT NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_lra_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_lra_refund FOREIGN KEY (refund_id) REFERENCES refunds(id),
    INDEX idx_lra_refund (refund_id),
    INDEX idx_lra_lot (point_lot_id),
    INDEX idx_lra_reservation (reservation_id)
) ENGINE=InnoDB;
```

### 3. Extend `refund_financial_operations`
Add lifecycle columns.

```sql
ALTER TABLE refund_financial_operations
    ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_error_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_error_at DATETIME(6),
    ADD COLUMN IF NOT EXISTS lease_until DATETIME(6),
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
```

### 4. `SettlementAdjustmentRequest` DTO change
Add `idempotencyKey` field.

---

## Service Changes

### RefundResultV2Consumer.java
1. Add event status allowlist validation (SUCCEEDED, FAILED only)
2. Replace `"sha256-placeholder"` with `FinancialOperationHasher.hash()`
3. Replace full-PartnerOrder allocation with per-RefundItem allocation
4. Make loyalty reversal proportional to refund (use refund_items ratio)
5. Add proper lifecycle: SKIPPED/FAILED for missing accounts

### SettlementServiceImpl.java
1. Query `status IN ('PENDING','PARTIALLY_APPLIED')` for pending adjustments
2. Use `PESSIMISTIC_WRITE` lock on settlement after atomic upsert
3. `addAdjustment`: guard payable against negative, create residual carry-forward
4. Use `remaining_amount` correctly for PARTIALLY_APPLIED rows

### New: FinancialOperationHasher.java
SHA-256 hasher with canonical ordering of fields.

### New: LoyaltyRefundService.java
Proportional loyalty reversal based on refund ratio.

---

## Test Changes

1. RefundSettlementIntegrationTest → use production beans, not SQL simulation
2. SettlementConcurrencyIntegrationTest → verify PESSIMISTIC_WRITE
3. Add partial refund allocation tests
4. Add loyalty proportional reversal tests

### Acceptance Criteria

```
✓ Partial refund allocated per RefundItem
✓ Multi-partner not cross-adjusted  
✓ Commission reversal uses snapshot
✓ Loyalty reversal proportional to refund
✓ No double points reversal
✓ PARTIALLY_APPLIED continues next period
✓ request_hash is real SHA-256
✓ No operation stuck PENDING forever
✓ Integration tests use real production beans
✓ Failsafe actually runs IT tests
✓ Maven verify passes
✓ Flyway migrate passes
```
