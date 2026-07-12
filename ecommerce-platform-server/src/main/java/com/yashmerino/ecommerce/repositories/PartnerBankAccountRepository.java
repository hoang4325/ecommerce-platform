package com.yashmerino.ecommerce.repositories;

import com.yashmerino.ecommerce.model.partner.PartnerBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerBankAccountRepository extends JpaRepository<PartnerBankAccount, Long> {
    List<PartnerBankAccount> findByPartnerId(Long partnerId);
    Optional<PartnerBankAccount> findByIdAndPartnerId(Long id, Long partnerId);
}
