package com.yashmerino.ecommerce.repositories;

import com.yashmerino.ecommerce.model.settlement.Settlement;
import com.yashmerino.ecommerce.model.settlement.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Page<Settlement> findByPartnerId(Long partnerId, Pageable pageable);

    Optional<Settlement> findByIdAndPartnerId(Long id, Long partnerId);

    Page<Settlement> findByStatus(SettlementStatus status, Pageable pageable);
}
