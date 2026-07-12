package com.yashmerino.ecommerce.services.interfaces;

import com.yashmerino.ecommerce.model.dto.partner.PartnerApplicationRequest;
import com.yashmerino.ecommerce.model.dto.partner.PartnerDecisionRequest;
import com.yashmerino.ecommerce.model.dto.partner.PartnerProfileUpdateRequest;
import com.yashmerino.ecommerce.model.dto.partner.PartnerResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PartnerService {

    PartnerResponse createApplication(PartnerApplicationRequest request);

    PartnerResponse getMyProfile();

    PartnerResponse updateMyProfile(PartnerProfileUpdateRequest request);

    PartnerResponse getMyStatus();

    PartnerResponse submitApplication();

    PartnerResponse getPartner(Long partnerId);

    Page<PartnerResponse> getAllPartners(Pageable pageable);

    Page<PartnerResponse> getPartnersByStatus(String status, Pageable pageable);

    PartnerResponse approvePartner(Long partnerId, PartnerDecisionRequest request);

    PartnerResponse rejectPartner(Long partnerId, PartnerDecisionRequest request);

    PartnerResponse suspendPartner(Long partnerId, PartnerDecisionRequest request);

    PartnerResponse restorePartner(Long partnerId, PartnerDecisionRequest request);

    PartnerResponse terminatePartner(Long partnerId, PartnerDecisionRequest request);
}
