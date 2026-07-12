package com.yashmerino.ecommerce.repositories;

import com.yashmerino.ecommerce.model.settlement.SettlementLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementLineRepository extends JpaRepository<SettlementLine, Long> {
    List<SettlementLine> findBySettlementId(Long settlementId);
}
