package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.exceptions.ConflictException;
import com.yashmerino.ecommerce.exceptions.InvalidInputException;
import com.yashmerino.ecommerce.model.Product;
import com.yashmerino.ecommerce.model.User;
import com.yashmerino.ecommerce.model.dto.offer.PartnerOfferRequest;
import com.yashmerino.ecommerce.model.offer.PartnerOffer;
import com.yashmerino.ecommerce.model.offer.PartnerOfferStatus;
import com.yashmerino.ecommerce.model.partner.Partner;
import com.yashmerino.ecommerce.repositories.PartnerOfferRepository;
import com.yashmerino.ecommerce.repositories.ProductRepository;
import com.yashmerino.ecommerce.security.PartnerAuthorizationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerOfferServiceImplTest {

    @Mock
    private PartnerOfferRepository offerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PartnerAuthorizationService authz;

    @InjectMocks
    private PartnerOfferServiceImpl offerService;

    private static final Long PARTNER_ID = 1L;
    private static final Long OFFER_ID = 100L;
    private static final Long PRODUCT_ID = 50L;

    private Partner partner;
    private Product product;
    private PartnerOffer offer;
    private PartnerOfferRequest offerRequest;

    @BeforeEach
    void setUp() {
        partner = new Partner();
        partner.setId(PARTNER_ID);

        product = new Product();
        product.setId(PRODUCT_ID);

        offer = new PartnerOffer();
        offer.setId(OFFER_ID);
        offer.setPartner(partner);
        offer.setProduct(product);
        offer.setPartnerSku("SKU-001");
        offer.setPrice(new BigDecimal("29.99"));
        offer.setCurrency("USD");
        offer.setOnHandQuantity(100);
        offer.setStatus(PartnerOfferStatus.DRAFT);

        offerRequest = new PartnerOfferRequest(
                PRODUCT_ID, "SKU-001", new BigDecimal("29.99"), "USD", 100);
    }

    @Test
    void createOffer_Success() {
        lenient().doNothing().when(authz).requireOfferWrite(PARTNER_ID);
        lenient().when(offerRepository.existsByPartnerIdAndPartnerSku(PARTNER_ID, "SKU-001")).thenReturn(false);
        lenient().when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        lenient().when(offerRepository.save(any(PartnerOffer.class))).thenAnswer(invocation -> {
            PartnerOffer saved = invocation.getArgument(0);
            saved.setId(OFFER_ID);
            return saved;
        });

        var response = offerService.createOffer(PARTNER_ID, offerRequest);

        assertNotNull(response);
        assertEquals("SKU-001", response.partnerSku());
        assertEquals(PartnerOfferStatus.DRAFT, response.status());
        verify(offerRepository).save(any(PartnerOffer.class));
    }

    @Test
    void createOffer_PartnerNotApproved_ThrowsAccessDenied() {
        doThrow(new AccessDeniedException("partner_not_active"))
                .when(authz).requireOfferWrite(PARTNER_ID);

        assertThrows(AccessDeniedException.class, () -> offerService.createOffer(PARTNER_ID, offerRequest));
        verify(offerRepository, never()).save(any(PartnerOffer.class));
    }

    @Test
    void createOffer_DuplicateSku_ThrowsConflict() {
        lenient().doNothing().when(authz).requireOfferWrite(PARTNER_ID);
        when(offerRepository.existsByPartnerIdAndPartnerSku(PARTNER_ID, "SKU-001")).thenReturn(true);

        assertThrows(ConflictException.class, () -> offerService.createOffer(PARTNER_ID, offerRequest));
        verify(offerRepository, never()).save(any(PartnerOffer.class));
    }

    @Test
    void createOffer_ProductNotFound_ThrowsEntityNotFound() {
        lenient().doNothing().when(authz).requireOfferWrite(PARTNER_ID);
        lenient().when(offerRepository.existsByPartnerIdAndPartnerSku(anyLong(), anyString())).thenReturn(false);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> offerService.createOffer(PARTNER_ID, offerRequest));
    }

    @Test
    void updateOffer_Success() {
        offer.setStatus(PartnerOfferStatus.DRAFT);
        lenient().doNothing().when(authz).requireOfferWrite(PARTNER_ID);
        lenient().when(offerRepository.findByIdAndPartnerId(OFFER_ID, PARTNER_ID)).thenReturn(Optional.of(offer));
        lenient().when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        lenient().when(offerRepository.save(any(PartnerOffer.class))).thenReturn(offer);

        var updatedRequest = new PartnerOfferRequest(
                PRODUCT_ID, "SKU-002", new BigDecimal("39.99"), "EUR", 200);

        var response = offerService.updateOffer(PARTNER_ID, OFFER_ID, updatedRequest);

        assertNotNull(response);
        verify(offerRepository).save(offer);
        assertEquals("SKU-002", offer.getPartnerSku());
    }

    @Test
    void updateOffer_NotEditableStatus_ThrowsInvalidInput() {
        offer.setStatus(PartnerOfferStatus.APPROVED);
        lenient().doNothing().when(authz).requireOfferWrite(PARTNER_ID);
        lenient().when(offerRepository.findByIdAndPartnerId(OFFER_ID, PARTNER_ID)).thenReturn(Optional.of(offer));

        assertThrows(InvalidInputException.class,
                () -> offerService.updateOffer(PARTNER_ID, OFFER_ID, offerRequest));
    }

    @Test
    void updateOffer_RejectedStatus_ResetsToDraft() {
        offer.setStatus(PartnerOfferStatus.REJECTED);
        offer.setRejectionReason("not good enough");
        lenient().doNothing().when(authz).requireOfferWrite(PARTNER_ID);
        lenient().when(offerRepository.findByIdAndPartnerId(OFFER_ID, PARTNER_ID)).thenReturn(Optional.of(offer));
        lenient().when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        lenient().when(offerRepository.save(any(PartnerOffer.class))).thenReturn(offer);

        var response = offerService.updateOffer(PARTNER_ID, OFFER_ID, offerRequest);

        assertEquals(PartnerOfferStatus.DRAFT, response.status());
        assertNull(offer.getRejectionReason());
    }

    @Test
    void submitOffer_Success() {
        offer.setStatus(PartnerOfferStatus.DRAFT);
        lenient().doNothing().when(authz).requireOfferWrite(PARTNER_ID);
        lenient().when(offerRepository.findByIdAndPartnerId(OFFER_ID, PARTNER_ID)).thenReturn(Optional.of(offer));
        lenient().when(offerRepository.save(any(PartnerOffer.class))).thenReturn(offer);

        var response = offerService.submitOffer(PARTNER_ID, OFFER_ID);

        assertEquals(PartnerOfferStatus.PENDING_REVIEW, response.status());
        assertNotNull(offer.getSubmittedAt());
        verify(offerRepository).save(offer);
    }

    @Test
    void submitOffer_NotDraftOrRejected_ThrowsInvalidInput() {
        offer.setStatus(PartnerOfferStatus.APPROVED);
        lenient().doNothing().when(authz).requireOfferWrite(PARTNER_ID);
        lenient().when(offerRepository.findByIdAndPartnerId(OFFER_ID, PARTNER_ID)).thenReturn(Optional.of(offer));

        assertThrows(InvalidInputException.class, () -> offerService.submitOffer(PARTNER_ID, OFFER_ID));
    }

    @Test
    void approveOffer_Success() {
        offer.setStatus(PartnerOfferStatus.PENDING_REVIEW);
        User admin = new User();
        admin.setId(2L);
        lenient().when(authz.getCurrentUser()).thenReturn(admin);
        lenient().when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));
        lenient().when(offerRepository.save(any(PartnerOffer.class))).thenReturn(offer);

        var response = offerService.approveOffer(OFFER_ID, "Looks good");

        assertEquals(PartnerOfferStatus.APPROVED, response.status());
        assertNotNull(offer.getApprovedAt());
        assertNotNull(offer.getApprovedBy());
        assertNull(offer.getRejectionReason());
        verify(offerRepository).save(offer);
    }

    @Test
    void approveOffer_NotPendingReview_ThrowsInvalidInput() {
        offer.setStatus(PartnerOfferStatus.DRAFT);
        lenient().when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));

        assertThrows(InvalidInputException.class, () -> offerService.approveOffer(OFFER_ID, "reason"));
    }

    @Test
    void getOffers_ReturnsPagedResults() {
        lenient().doNothing().when(authz).requireOfferRead(PARTNER_ID);
        PageRequest pageable = PageRequest.of(0, 10);
        Page<PartnerOffer> offerPage = new PageImpl<>(List.of(offer));
        lenient().when(offerRepository.findByPartnerId(PARTNER_ID, pageable)).thenReturn(offerPage);

        var response = offerService.getOffers(PARTNER_ID, pageable);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        verify(authz).requireOfferRead(PARTNER_ID);
    }

    @Test
    void archiveOffer_Success() {
        offer.setStatus(PartnerOfferStatus.APPROVED);
        lenient().doNothing().when(authz).requireOfferWrite(PARTNER_ID);
        lenient().when(offerRepository.findByIdAndPartnerId(OFFER_ID, PARTNER_ID)).thenReturn(Optional.of(offer));
        lenient().when(offerRepository.save(any(PartnerOffer.class))).thenReturn(offer);

        var response = offerService.archiveOffer(PARTNER_ID, OFFER_ID);

        assertEquals(PartnerOfferStatus.ARCHIVED, response.status());
        verify(offerRepository).save(offer);
    }

    @Test
    void archiveOffer_AlreadyArchived_ThrowsInvalidInput() {
        offer.setStatus(PartnerOfferStatus.ARCHIVED);
        lenient().doNothing().when(authz).requireOfferWrite(PARTNER_ID);
        lenient().when(offerRepository.findByIdAndPartnerId(OFFER_ID, PARTNER_ID)).thenReturn(Optional.of(offer));

        assertThrows(InvalidInputException.class, () -> offerService.archiveOffer(PARTNER_ID, OFFER_ID));
    }

    @Test
    void adjustInventory_Success() {
        offer.setOnHandQuantity(100);
        lenient().doNothing().when(authz).requireInventoryWrite(PARTNER_ID);
        lenient().when(offerRepository.findByIdAndPartnerId(OFFER_ID, PARTNER_ID)).thenReturn(Optional.of(offer));
        lenient().when(offerRepository.save(any(PartnerOffer.class))).thenReturn(offer);

        var response = offerService.adjustInventory(PARTNER_ID, OFFER_ID, 50, "restock");

        assertEquals(Integer.valueOf(150), response.onHandQuantity());
        verify(offerRepository).save(offer);
    }

    @Test
    void adjustInventory_NegativeDeltaAllowed() {
        offer.setOnHandQuantity(100);
        lenient().doNothing().when(authz).requireInventoryWrite(PARTNER_ID);
        lenient().when(offerRepository.findByIdAndPartnerId(OFFER_ID, PARTNER_ID)).thenReturn(Optional.of(offer));
        lenient().when(offerRepository.save(any(PartnerOffer.class))).thenReturn(offer);

        var response = offerService.adjustInventory(PARTNER_ID, OFFER_ID, -30, "sale");

        assertEquals(Integer.valueOf(70), response.onHandQuantity());
    }

    @Test
    void adjustInventory_InsufficientStock_ThrowsInvalidInput() {
        offer.setOnHandQuantity(10);
        lenient().doNothing().when(authz).requireInventoryWrite(PARTNER_ID);
        lenient().when(offerRepository.findByIdAndPartnerId(OFFER_ID, PARTNER_ID)).thenReturn(Optional.of(offer));

        assertThrows(InvalidInputException.class,
                () -> offerService.adjustInventory(PARTNER_ID, OFFER_ID, -20, "oversold"));
    }

    @Test
    void adjustInventory_OfferNotFound_ThrowsEntityNotFound() {
        lenient().doNothing().when(authz).requireInventoryWrite(PARTNER_ID);
        when(offerRepository.findByIdAndPartnerId(OFFER_ID, PARTNER_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> offerService.adjustInventory(PARTNER_ID, OFFER_ID, 10, "test"));
    }
}
