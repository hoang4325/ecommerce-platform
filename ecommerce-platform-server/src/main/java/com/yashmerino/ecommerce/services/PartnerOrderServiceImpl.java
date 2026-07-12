package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.exceptions.InvalidInputException;
import com.yashmerino.ecommerce.model.dto.order.PartnerOrderResponse;
import com.yashmerino.ecommerce.model.order.PartnerOrder;
import com.yashmerino.ecommerce.model.order.PartnerOrderStatus;
import com.yashmerino.ecommerce.model.partner.PartnerMemberRole;
import com.yashmerino.ecommerce.repositories.PartnerOrderRepository;
import com.yashmerino.ecommerce.security.PartnerAuthorizationService;
import com.yashmerino.ecommerce.services.interfaces.PartnerOrderService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PartnerOrderServiceImpl implements PartnerOrderService {

    private final PartnerOrderRepository partnerOrderRepository;
    private final PartnerAuthorizationService authz;

    private static final Set<PartnerMemberRole> FULFILLMENT_ROLES = Set.of(
            PartnerMemberRole.OWNER, PartnerMemberRole.MANAGER, PartnerMemberRole.ORDER_STAFF);

    @Override
    @Transactional(readOnly = true)
    public Page<PartnerOrderResponse> getPartnerOrders(Long partnerId, Pageable pageable) {
        authz.requirePartnerActive(partnerId);
        return partnerOrderRepository.findByPartnerId(partnerId, pageable).map(PartnerOrderResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerOrderResponse getPartnerOrder(Long partnerId, Long partnerOrderId) {
        authz.requirePartnerActive(partnerId);
        PartnerOrder po = partnerOrderRepository.findByIdAndPartnerId(partnerOrderId, partnerId)
                .orElseThrow(() -> new EntityNotFoundException("partner_order_not_found"));
        return PartnerOrderResponse.from(po);
    }

    @Override
    @Transactional
    public PartnerOrderResponse acceptOrder(Long partnerId, Long partnerOrderId) {
        authz.requireAllowsCommand(partnerId, FULFILLMENT_ROLES);
        PartnerOrder po = findOwned(partnerId, partnerOrderId);

        if (po.getStatus() != PartnerOrderStatus.NEW) {
            throw new InvalidInputException("cannot_accept_in_current_status");
        }
        po.setStatus(PartnerOrderStatus.ACCEPTED);
        po.setAcceptedAt(LocalDateTime.now());
        return PartnerOrderResponse.from(partnerOrderRepository.save(po));
    }

    @Override
    @Transactional
    public PartnerOrderResponse rejectOrder(Long partnerId, Long partnerOrderId, String reason) {
        authz.requireAllowsCommand(partnerId, FULFILLMENT_ROLES);
        PartnerOrder po = findOwned(partnerId, partnerOrderId);

        if (po.getStatus() != PartnerOrderStatus.NEW) {
            throw new InvalidInputException("cannot_reject_in_current_status");
        }
        po.setStatus(PartnerOrderStatus.REJECTED);
        po.setRejectedAt(LocalDateTime.now());
        po.setRejectionReason(reason);
        return PartnerOrderResponse.from(partnerOrderRepository.save(po));
    }

    @Override
    @Transactional
    public PartnerOrderResponse markPacking(Long partnerId, Long partnerOrderId) {
        authz.requireAllowsCommand(partnerId, FULFILLMENT_ROLES);
        PartnerOrder po = findOwned(partnerId, partnerOrderId);

        if (po.getStatus() != PartnerOrderStatus.ACCEPTED) {
            throw new InvalidInputException("cannot_mark_packing_in_current_status");
        }
        po.setStatus(PartnerOrderStatus.PACKING);
        po.setPackedAt(LocalDateTime.now());
        return PartnerOrderResponse.from(partnerOrderRepository.save(po));
    }

    @Override
    @Transactional
    public PartnerOrderResponse markReadyToShip(Long partnerId, Long partnerOrderId) {
        authz.requireAllowsCommand(partnerId, FULFILLMENT_ROLES);
        PartnerOrder po = findOwned(partnerId, partnerOrderId);

        if (po.getStatus() != PartnerOrderStatus.PACKING) {
            throw new InvalidInputException("cannot_mark_ready_to_ship_in_current_status");
        }
        po.setStatus(PartnerOrderStatus.READY_TO_SHIP);
        po.setReadyToShipAt(LocalDateTime.now());
        return PartnerOrderResponse.from(partnerOrderRepository.save(po));
    }

    @Override
    @Transactional
    public PartnerOrderResponse shipOrder(Long partnerId, Long partnerOrderId) {
        authz.requireAllowsCommand(partnerId, FULFILLMENT_ROLES);
        PartnerOrder po = findOwned(partnerId, partnerOrderId);

        if (po.getStatus() != PartnerOrderStatus.READY_TO_SHIP) {
            throw new InvalidInputException("cannot_ship_in_current_status");
        }
        po.setStatus(PartnerOrderStatus.SHIPPED);
        po.setShippedAt(LocalDateTime.now());
        return PartnerOrderResponse.from(partnerOrderRepository.save(po));
    }

    private PartnerOrder findOwned(Long partnerId, Long partnerOrderId) {
        return partnerOrderRepository.findByIdAndPartnerId(partnerOrderId, partnerId)
                .orElseThrow(() -> new EntityNotFoundException("partner_order_not_found"));
    }
}
