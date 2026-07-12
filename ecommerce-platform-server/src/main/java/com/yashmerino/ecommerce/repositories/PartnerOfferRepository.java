package com.yashmerino.ecommerce.repositories;

import com.yashmerino.ecommerce.model.offer.PartnerOffer;
import com.yashmerino.ecommerce.model.offer.PartnerOfferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PartnerOfferRepository extends JpaRepository<PartnerOffer, Long> {

    Page<PartnerOffer> findByPartnerId(Long partnerId, Pageable pageable);

    Optional<PartnerOffer> findByIdAndPartnerId(Long id, Long partnerId);

    List<PartnerOffer> findByProductId(Long productId);

    Page<PartnerOffer> findByStatus(PartnerOfferStatus status, Pageable pageable);

    @Query("SELECT o FROM PartnerOffer o WHERE o.status = 'APPROVED' AND o.partner.status = 'APPROVED' AND o.onHandQuantity > o.reservedQuantity")
    List<PartnerOffer> findSellableOffers();

    @Query("SELECT o FROM PartnerOffer o WHERE o.id = :id AND o.status = 'APPROVED' AND o.partner.status = 'APPROVED'")
    Optional<PartnerOffer> findSellableById(@Param("id") Long id);

    boolean existsByPartnerIdAndPartnerSku(Long partnerId, String partnerSku);
}
