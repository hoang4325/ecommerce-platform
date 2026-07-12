package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.exceptions.ConflictException;
import com.yashmerino.ecommerce.exceptions.InvalidInputException;
import com.yashmerino.ecommerce.model.Product;
import com.yashmerino.ecommerce.model.User;
import com.yashmerino.ecommerce.model.dto.offer.PartnerOfferRequest;
import com.yashmerino.ecommerce.model.dto.offer.PartnerOfferResponse;
import com.yashmerino.ecommerce.model.offer.PartnerOffer;
import com.yashmerino.ecommerce.model.offer.PartnerOfferStatus;
import com.yashmerino.ecommerce.model.partner.Partner;
import com.yashmerino.ecommerce.repositories.PartnerOfferRepository;
import com.yashmerino.ecommerce.repositories.ProductRepository;
import com.yashmerino.ecommerce.security.PartnerAuthorizationService;
import com.yashmerino.ecommerce.services.interfaces.PartnerOfferService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PartnerOfferServiceImpl implements PartnerOfferService {

    private final PartnerOfferRepository offerRepository;
    private final ProductRepository productRepository;
    private final PartnerAuthorizationService authz;

    @Override
    @Transactional
    public PartnerOfferResponse createOffer(Long partnerId, PartnerOfferRequest request) {
        authz.requireOfferWrite(partnerId);

        if (offerRepository.existsByPartnerIdAndPartnerSku(partnerId, request.partnerSku())) {
            throw new ConflictException("partner_sku_already_exists");
        }

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new EntityNotFoundException("product_not_found"));

        Partner partner = new Partner();
        partner.setId(partnerId);

        PartnerOffer offer = new PartnerOffer();
        offer.setPartner(partner);
        offer.setProduct(product);
        offer.setPartnerSku(request.partnerSku());
        offer.setPrice(request.price());
        offer.setCurrency(request.currency() != null ? request.currency() : "USD");
        offer.setOnHandQuantity(request.onHandQuantity());
        offer.setStatus(PartnerOfferStatus.DRAFT);
        return PartnerOfferResponse.from(offerRepository.save(offer));
    }

    @Override
    @Transactional
    public PartnerOfferResponse updateOffer(Long partnerId, Long offerId, PartnerOfferRequest request) {
        authz.requireOfferWrite(partnerId);

        PartnerOffer offer = offerRepository.findByIdAndPartnerId(offerId, partnerId)
                .orElseThrow(() -> new EntityNotFoundException("offer_not_found"));

        if (offer.getStatus() != PartnerOfferStatus.DRAFT
                && offer.getStatus() != PartnerOfferStatus.REJECTED
                && offer.getStatus() != PartnerOfferStatus.SUSPENDED) {
            throw new InvalidInputException("cannot_update_in_current_status");
        }

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new EntityNotFoundException("product_not_found"));

        offer.setProduct(product);
        offer.setPartnerSku(request.partnerSku());
        offer.setPrice(request.price());
        offer.setCurrency(request.currency() != null ? request.currency() : "USD");
        offer.setOnHandQuantity(request.onHandQuantity());

        if (offer.getStatus() == PartnerOfferStatus.REJECTED || offer.getStatus() == PartnerOfferStatus.SUSPENDED) {
            offer.setStatus(PartnerOfferStatus.DRAFT);
            offer.setRejectionReason(null);
        }

        return PartnerOfferResponse.from(offerRepository.save(offer));
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerOfferResponse getOffer(Long partnerId, Long offerId) {
        authz.requireOfferRead(partnerId);
        PartnerOffer offer = offerRepository.findByIdAndPartnerId(offerId, partnerId)
                .orElseThrow(() -> new EntityNotFoundException("offer_not_found"));
        return PartnerOfferResponse.from(offer);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PartnerOfferResponse> getOffers(Long partnerId, Pageable pageable) {
        authz.requireOfferRead(partnerId);
        return offerRepository.findByPartnerId(partnerId, pageable).map(PartnerOfferResponse::from);
    }

    @Override
    @Transactional
    public PartnerOfferResponse submitOffer(Long partnerId, Long offerId) {
        authz.requireOfferWrite(partnerId);

        PartnerOffer offer = offerRepository.findByIdAndPartnerId(offerId, partnerId)
                .orElseThrow(() -> new EntityNotFoundException("offer_not_found"));

        if (offer.getStatus() != PartnerOfferStatus.DRAFT
                && offer.getStatus() != PartnerOfferStatus.REJECTED) {
            throw new InvalidInputException("cannot_submit_in_current_status");
        }

        offer.setStatus(PartnerOfferStatus.PENDING_REVIEW);
        offer.setSubmittedAt(LocalDateTime.now());
        return PartnerOfferResponse.from(offerRepository.save(offer));
    }

    @Override
    @Transactional
    public PartnerOfferResponse archiveOffer(Long partnerId, Long offerId) {
        authz.requireOfferWrite(partnerId);

        PartnerOffer offer = offerRepository.findByIdAndPartnerId(offerId, partnerId)
                .orElseThrow(() -> new EntityNotFoundException("offer_not_found"));

        if (offer.getStatus() == PartnerOfferStatus.ARCHIVED) {
            throw new InvalidInputException("already_archived");
        }

        offer.setStatus(PartnerOfferStatus.ARCHIVED);
        return PartnerOfferResponse.from(offerRepository.save(offer));
    }

    @Override
    @Transactional
    public PartnerOfferResponse adjustInventory(Long partnerId, Long offerId, int delta, String reason) {
        authz.requireInventoryWrite(partnerId);

        PartnerOffer offer = offerRepository.findByIdAndPartnerId(offerId, partnerId)
                .orElseThrow(() -> new EntityNotFoundException("offer_not_found"));

        int newOnHand = offer.getOnHandQuantity() + delta;
        if (newOnHand < 0) {
            throw new InvalidInputException("insufficient_stock");
        }
        offer.setOnHandQuantity(newOnHand);
        return PartnerOfferResponse.from(offerRepository.save(offer));
    }

    @Override
    @Transactional
    public PartnerOfferResponse approveOffer(Long offerId, String reason) {
        PartnerOffer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new EntityNotFoundException("offer_not_found"));

        if (offer.getStatus() != PartnerOfferStatus.PENDING_REVIEW) {
            throw new InvalidInputException("cannot_approve_in_current_status");
        }

        User admin = authz.getCurrentUser();
        offer.setStatus(PartnerOfferStatus.APPROVED);
        offer.setApprovedAt(LocalDateTime.now());
        offer.setApprovedBy(admin);
        offer.setRejectionReason(null);
        return PartnerOfferResponse.from(offerRepository.save(offer));
    }

    @Override
    @Transactional
    public PartnerOfferResponse rejectOffer(Long offerId, String reason) {
        PartnerOffer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new EntityNotFoundException("offer_not_found"));

        if (offer.getStatus() != PartnerOfferStatus.PENDING_REVIEW) {
            throw new InvalidInputException("cannot_reject_in_current_status");
        }

        offer.setStatus(PartnerOfferStatus.REJECTED);
        offer.setRejectionReason(reason);
        return PartnerOfferResponse.from(offerRepository.save(offer));
    }

    @Override
    @Transactional
    public PartnerOfferResponse suspendOffer(Long offerId, String reason) {
        PartnerOffer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new EntityNotFoundException("offer_not_found"));

        if (offer.getStatus() != PartnerOfferStatus.APPROVED) {
            throw new InvalidInputException("cannot_suspend_in_current_status");
        }

        offer.setStatus(PartnerOfferStatus.SUSPENDED);
        offer.setRejectionReason(reason);
        return PartnerOfferResponse.from(offerRepository.save(offer));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PartnerOfferResponse> getAllOffers(Pageable pageable) {
        return offerRepository.findAll(pageable).map(PartnerOfferResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PartnerOfferResponse> getOffersByStatus(String status, Pageable pageable) {
        PartnerOfferStatus offerStatus;
        try {
            offerStatus = PartnerOfferStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("invalid_status");
        }
        return offerRepository.findByStatus(offerStatus, pageable).map(PartnerOfferResponse::from);
    }
}
