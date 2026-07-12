package com.yashmerino.ecommerce.repositories;

import com.yashmerino.ecommerce.model.settlement.Settlement;
import com.yashmerino.ecommerce.model.settlement.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import java.time.LocalDateTime;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Page<Settlement> findByPartnerId(Long partnerId, Pageable pageable);

    Page<Settlement> findByPartnerIdAndCurrency(Long partnerId, String currency, Pageable pageable);

    Optional<Settlement> findByIdAndPartnerId(Long id, Long partnerId);

    Page<Settlement> findByStatus(SettlementStatus status, Pageable pageable);

    Optional<Settlement> findByPartnerIdAndPeriodStartAndPeriodEndAndCurrency(
            Long partnerId, LocalDateTime periodStart, LocalDateTime periodEnd, String currency);
}
