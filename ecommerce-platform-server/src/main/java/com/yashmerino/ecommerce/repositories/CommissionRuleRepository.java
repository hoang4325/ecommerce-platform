package com.yashmerino.ecommerce.repositories;

import com.yashmerino.ecommerce.model.commission.CommissionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommissionRuleRepository extends JpaRepository<CommissionRule, Long> {

    @Query("SELECT r FROM CommissionRule r WHERE r.status = 'ACTIVE' AND (r.validFrom IS NULL OR r.validFrom <= CURRENT_TIMESTAMP) AND (r.validTo IS NULL OR r.validTo >= CURRENT_TIMESTAMP) ORDER BY " +
           "CASE WHEN r.productId = :productId THEN 0 WHEN r.categoryId = :categoryId THEN 1 WHEN r.partnerId = :partnerId THEN 2 ELSE 3 END, " +
           "r.priority DESC, r.validFrom DESC, r.id")
    List<CommissionRule> findApplicableRules(@Param("partnerId") Long partnerId,
                                              @Param("categoryId") Long categoryId,
                                              @Param("productId") Long productId);
}
