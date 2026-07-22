package com.yashmerino.ecommerce.controllers;

import com.yashmerino.ecommerce.model.commission.CommissionRule;
import com.yashmerino.ecommerce.model.dto.commission.CommissionRuleResponse;
import com.yashmerino.ecommerce.model.dto.commission.CreateCommissionRuleRequest;
import com.yashmerino.ecommerce.model.dto.commission.UpdateCommissionRuleRequest;
import com.yashmerino.ecommerce.repositories.CommissionRuleRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;

@RestController
@RequestMapping("/api/admin/commission-rules")
@RequiredArgsConstructor
public class AdminCommissionRuleController {

    private final CommissionRuleRepository commissionRuleRepository;

    @GetMapping
    public ResponseEntity<Page<CommissionRuleResponse>> list(Pageable pageable) {
        return ResponseEntity.ok(commissionRuleRepository.findAll(pageable).map(CommissionRuleResponse::from));
    }

    @GetMapping("/{ruleId}")
    public ResponseEntity<CommissionRuleResponse> get(@PathVariable Long ruleId) {
        return ResponseEntity.ok(CommissionRuleResponse.from(findRule(ruleId)));
    }

    @PostMapping
    public ResponseEntity<CommissionRuleResponse> create(@Valid @RequestBody CreateCommissionRuleRequest request) {
        CommissionRule rule = new CommissionRule();
        rule.setPartnerId(request.partnerId());
        rule.setCategoryId(request.categoryId());
        rule.setProductId(request.productId());
        rule.setRate(normalizeRate(request.rate()));
        rule.setFixedFee(request.fixedFee() != null ? request.fixedFee() : BigDecimal.ZERO);
        rule.setCurrency(request.currency() != null ? request.currency() : "VND");
        rule.setPriority(request.priority() != null ? request.priority() : 0);
        rule.setValidFrom(request.validFrom());
        rule.setValidTo(request.validTo());
        rule.setStatus("DRAFT");
        return ResponseEntity.ok(CommissionRuleResponse.from(commissionRuleRepository.save(rule)));
    }

    @PutMapping("/{ruleId}")
    public ResponseEntity<CommissionRuleResponse> update(@PathVariable Long ruleId,
                                                         @Valid @RequestBody UpdateCommissionRuleRequest request) {
        CommissionRule rule = findRule(ruleId);
        if (request.rate() != null) {
            rule.setRate(normalizeRate(request.rate()));
        }
        if (request.fixedFee() != null) {
            rule.setFixedFee(request.fixedFee());
        }
        if (request.currency() != null) {
            rule.setCurrency(request.currency());
        }
        if (request.priority() != null) {
            rule.setPriority(request.priority());
        }
        if (request.validFrom() != null) {
            rule.setValidFrom(request.validFrom());
        }
        rule.setValidTo(request.validTo());
        return ResponseEntity.ok(CommissionRuleResponse.from(commissionRuleRepository.save(rule)));
    }

    @PostMapping("/{ruleId}/activate")
    public ResponseEntity<CommissionRuleResponse> activate(@PathVariable Long ruleId) {
        CommissionRule rule = findRule(ruleId);
        rule.setStatus("ACTIVE");
        return ResponseEntity.ok(CommissionRuleResponse.from(commissionRuleRepository.save(rule)));
    }

    @PostMapping("/{ruleId}/deactivate")
    public ResponseEntity<CommissionRuleResponse> deactivate(@PathVariable Long ruleId) {
        CommissionRule rule = findRule(ruleId);
        rule.setStatus("INACTIVE");
        return ResponseEntity.ok(CommissionRuleResponse.from(commissionRuleRepository.save(rule)));
    }

    @PostMapping("/{ruleId}/expire")
    public ResponseEntity<CommissionRuleResponse> expire(@PathVariable Long ruleId) {
        CommissionRule rule = findRule(ruleId);
        rule.setStatus("EXPIRED");
        return ResponseEntity.ok(CommissionRuleResponse.from(commissionRuleRepository.save(rule)));
    }

    private CommissionRule findRule(Long ruleId) {
        return commissionRuleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException("commission_rule_not_found"));
    }

    private BigDecimal normalizeRate(BigDecimal rate) {
        if (rate == null) {
            return BigDecimal.ZERO;
        }
        if (rate.compareTo(BigDecimal.ONE) > 0) {
            return rate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        }
        return rate;
    }
}
