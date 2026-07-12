package com.yashmerino.ecommerce.repositories;

import com.yashmerino.ecommerce.model.partner.PartnerDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartnerDocumentRepository extends JpaRepository<PartnerDocument, Long> {
    List<PartnerDocument> findByPartnerId(Long partnerId);
}
