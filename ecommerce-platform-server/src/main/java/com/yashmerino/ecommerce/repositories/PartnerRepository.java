package com.yashmerino.ecommerce.repositories;

import com.yashmerino.ecommerce.model.partner.Partner;
import com.yashmerino.ecommerce.model.partner.PartnerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PartnerRepository extends JpaRepository<Partner, Long> {
    boolean existsByTaxCode(String taxCode);
    boolean existsByApplicantIdAndStatusNot(Long applicantId, PartnerStatus status);
    Optional<Partner> findFirstByApplicantIdOrderByCreatedAtDesc(Long applicantId);
    Page<Partner> findByStatus(PartnerStatus status, Pageable pageable);
}

