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
import java.util.Set;

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
            List<RuleRow> rules = loadCandidateRules(item.productId(), item.categoryIds(), item.partnerId(), item.currency());
            RuleRow best = findBestMatch(rules, item.productId(), item.categoryIds(), item.partnerId());

            if (best == null) {
                results.add(new CommissionResult(
                        item.productId(), item.offerId(),
                        null, ZERO, ZERO, ZERO, item.lineTotal()
                ));
            } else {
                BigDecimal lineNet = item.lineTotal().max(ZERO);
                BigDecimal calculated = lineNet.multiply(best.rate()).add(best.fixedFee())
                        .setScale(2, RoundingMode.HALF_UP);
                // Commission must not exceed line net amount
                BigDecimal commissionAmount = calculated.min(lineNet);
                BigDecimal partnerPayable = lineNet.subtract(commissionAmount)
                        .setScale(2, RoundingMode.HALF_UP);

                results.add(new CommissionResult(
                        item.productId(), item.offerId(),
                        best.id(), best.rate(), best.fixedFee(), commissionAmount, partnerPayable
                ));
            }
        }

        return results;
    }

    private List<RuleRow> loadCandidateRules(Long productId, Set<Long> categoryIds, Long partnerId, String currency) {
        StringBuilder sql = new StringBuilder(
                "SELECT id,rate,fixed_fee,partner_id,category_id,product_id,priority,valid_from " +
                "FROM commission_rules WHERE status='ACTIVE' " +
                "AND (currency IS NULL OR currency=?) " +
                "AND (valid_from IS NULL OR valid_from<=CURRENT_TIMESTAMP(6)) " +
                "AND (valid_to IS NULL OR valid_to>=CURRENT_TIMESTAMP(6)) " +
                "AND (product_id=?");
        List<Object> params = new ArrayList<>();
        params.add(currency != null ? currency : "USD");
        params.add(productId);

        if (categoryIds != null && !categoryIds.isEmpty()) {
            for (int i = 0; i < categoryIds.size(); i++) {
                sql.append(" OR category_id=?");
            }
            params.addAll(categoryIds);
        }
        if (partnerId != null) {
            sql.append(" OR partner_id=?");
            params.add(partnerId);
        }
        sql.append(" OR (partner_id IS NULL AND category_id IS NULL AND product_id IS NULL)");
        sql.append(")");

        return jdbc.query(sql.toString(), (rs, n) -> mapRule(rs), params.toArray());
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

    private RuleRow findBestMatch(List<RuleRow> rules, long productId, Set<Long> categoryIds, Long partnerId) {
        return rules.stream()
                .filter(r -> isMatch(r, productId, categoryIds, partnerId))
                .min(Comparator
                        .comparingInt((RuleRow r) -> specificity(r, productId, categoryIds, partnerId))
                        .thenComparing(Comparator.comparingInt(RuleRow::priority).reversed())
                        .thenComparing(r -> r.validFrom(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparingLong(RuleRow::id))
                .orElse(null);
    }

    private boolean isMatch(RuleRow rule, long productId, Set<Long> categoryIds, Long partnerId) {
        if (rule.productId() != null && rule.productId() != productId) return false;
        if (rule.categoryId() != null && (categoryIds == null || !categoryIds.contains(rule.categoryId()))) return false;
        if (rule.partnerId() != null && (partnerId == null || !rule.partnerId().equals(partnerId))) return false;
        return true;
    }

    private int specificity(RuleRow rule, long productId, Set<Long> categoryIds, Long partnerId) {
        if (rule.productId() != null && rule.productId() == productId) return 0;
        if (rule.categoryId() != null && categoryIds != null && categoryIds.contains(rule.categoryId())) return 1;
        if (rule.partnerId() != null && partnerId != null && rule.partnerId().equals(partnerId)) return 2;
        return 3;
    }

    private record RuleRow(long id, BigDecimal rate, BigDecimal fixedFee, Long partnerId, Long categoryId, Long productId, int priority, java.time.LocalDateTime validFrom) {}
}
