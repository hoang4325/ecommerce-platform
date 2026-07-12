package com.yashmerino.ecommerce.services.interfaces;

import com.yashmerino.ecommerce.model.dto.offer.PartnerOfferRequest;
import com.yashmerino.ecommerce.model.dto.offer.PartnerOfferResponse;
import com.yashmerino.ecommerce.model.partner.PartnerMemberRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PartnerOfferService {

    PartnerOfferResponse createOffer(Long partnerId, PartnerOfferRequest request);

    PartnerOfferResponse updateOffer(Long partnerId, Long offerId, PartnerOfferRequest request);

    PartnerOfferResponse getOffer(Long partnerId, Long offerId);

    Page<PartnerOfferResponse> getOffers(Long partnerId, Pageable pageable);

    PartnerOfferResponse submitOffer(Long partnerId, Long offerId);

    PartnerOfferResponse archiveOffer(Long partnerId, Long offerId);

    PartnerOfferResponse adjustInventory(Long partnerId, Long offerId, int delta, String reason);

    PartnerOfferResponse approveOffer(Long offerId, String reason);

    PartnerOfferResponse rejectOffer(Long offerId, String reason);

    PartnerOfferResponse suspendOffer(Long offerId, String reason);

    Page<PartnerOfferResponse> getAllOffers(Pageable pageable);

    Page<PartnerOfferResponse> getOffersByStatus(String status, Pageable pageable);
}
