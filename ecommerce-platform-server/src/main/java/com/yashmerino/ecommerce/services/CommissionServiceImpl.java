package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.services.interfaces.CommissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommissionServiceImpl implements CommissionService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final JdbcTemplate jdbc;

    @Override
    public List<CommissionResult> resolveOrderItemCommissions(List<CommissionRequest> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<CommissionResult> results = new ArrayList<>(items.size());
        for (CommissionRequest item : items) {
            List<RuleRow> rules = loadRules(item.partnerId(), item.currency());
            RuleRow best = findBestMatch(rules, item.productId(), item.categoryId(), item.partnerId());

            if (best == null) {
                results.add(new CommissionResult(
                        item.productId(), item.offerId(),
                        null, ZERO, ZERO, ZERO, item.lineTotal()
                ));
            } else {
                BigDecimal commissionAmount = item.lineTotal().multiply(best.rate()).add(best.fixedFee())
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal partnerPayable = item.lineTotal().subtract(commissionAmount).max(ZERO)
                        .setScale(2, RoundingMode.HALF_UP);

                results.add(new CommissionResult(
                        item.productId(), item.offerId(),
                        best.id(), best.rate(), best.fixedFee(), commissionAmount, partnerPayable
                ));
            }
        }

        return results;
    }

    private List<RuleRow> loadRules(Long partnerId, String currency) {
        return jdbc.query(
                "SELECT id,rate,fixed_fee,partner_id,category_id,product_id,priority,valid_from " +
                "FROM commission_rules WHERE status='ACTIVE' " +
                "AND (partner_id IS NULL OR partner_id=?) " +
                "AND (currency IS NULL OR currency=?) " +
                "AND (valid_from IS NULL OR valid_from<=CURRENT_TIMESTAMP(6)) " +
                "AND (valid_to IS NULL OR valid_to>=CURRENT_TIMESTAMP(6))",
                (rs, n) -> mapRule(rs),
                partnerId != null ? partnerId : 0L,
                currency != null ? currency : "USD");
    }

    private RuleRow mapRule(ResultSet rs) throws SQLException {
        return new RuleRow(
                rs.getLong("id"),
                rs.getBigDecimal("rate"),
                rs.getBigDecimal("fixed_fee") != null ? rs.getBigDecimal("fixed_fee") : ZERO,
                rs.getObject("partner_id", Long.class),
                rs.getObject("category_id", Long.class),
                rs.getObject("product_id", Long.class),
                rs.getInt("priority"),
                rs.getTimestamp("valid_from") != null ? rs.getTimestamp("valid_from").toLocalDateTime() : null
        );
    }

    private RuleRow findBestMatch(List<RuleRow> rules, long productId, Long categoryId, Long partnerId) {
        return rules.stream()
                .filter(r -> isMatch(r, productId, categoryId, partnerId))
                .min(Comparator.comparingInt((RuleRow r) -> specificity(r, productId, categoryId, partnerId))
                        .thenComparing(Comparator.comparingInt(RuleRow::priority).reversed())
                        .thenComparing(r -> r.validFrom(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingLong(RuleRow::id))
                .orElse(null);
    }

    private boolean isMatch(RuleRow rule, long productId, Long categoryId, Long partnerId) {
        if (rule.productId() != null && rule.productId() != productId) return false;
        if (rule.categoryId() != null && (categoryId == null || !rule.categoryId().equals(categoryId))) return false;
        if (rule.partnerId() != null && (partnerId == null || !rule.partnerId().equals(partnerId))) return false;
        return true;
    }

    private int specificity(RuleRow rule, long productId, Long categoryId, Long partnerId) {
        if (rule.productId() != null && rule.productId() == productId) return 0;
        if (rule.categoryId() != null && categoryId != null && rule.categoryId().equals(categoryId)) return 1;
        if (rule.partnerId() != null && partnerId != null && rule.partnerId().equals(partnerId)) return 2;
        return 3;
    }

    private record RuleRow(long id, BigDecimal rate, BigDecimal fixedFee, Long partnerId, Long categoryId, Long productId, int priority, java.time.LocalDateTime validFrom) {}
}
