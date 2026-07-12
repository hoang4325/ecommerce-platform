package com.yashmerino.ecommerce.repositories;

import com.yashmerino.ecommerce.model.partner.PartnerAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerAuditEventRepository extends JpaRepository<PartnerAuditEvent, Long> {
}

