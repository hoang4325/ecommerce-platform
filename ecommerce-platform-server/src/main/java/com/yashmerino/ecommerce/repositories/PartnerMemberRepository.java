package com.yashmerino.ecommerce.repositories;

import com.yashmerino.ecommerce.model.partner.PartnerMember;
import com.yashmerino.ecommerce.model.partner.PartnerMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerMemberRepository extends JpaRepository<PartnerMember, Long> {
    Optional<PartnerMember> findByPartnerIdAndUserId(Long partnerId, Long userId);
    Optional<PartnerMember> findByIdAndPartnerId(Long id, Long partnerId);
    List<PartnerMember> findByPartnerId(Long partnerId);
    List<PartnerMember> findByUserIdAndStatus(Long userId, PartnerMemberStatus status);
}
