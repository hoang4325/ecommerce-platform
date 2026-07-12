package com.yashmerino.ecommerce.repositories;

import com.yashmerino.ecommerce.model.settlement.SettlementLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

public interface SettlementLineRepository extends JpaRepository<SettlementLine, Long> {
    List<SettlementLine> findBySettlementId(Long settlementId);

    @Transactional
    void deleteBySettlementId(Long settlementId);

    @Transactional
    void deleteBySettlementIdAndLineType(Long settlementId, String lineType);
}
