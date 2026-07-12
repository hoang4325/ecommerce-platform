package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.exceptions.InvalidInputException;
import com.yashmerino.ecommerce.model.dto.order.PartnerOrderResponse;
import com.yashmerino.ecommerce.model.order.PartnerOrder;
import com.yashmerino.ecommerce.model.order.PartnerOrderStatus;
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

@Service
@RequiredArgsConstructor
public class PartnerOrderServiceImpl implements PartnerOrderService {

    private final PartnerOrderRepository partnerOrderRepository;
    private final PartnerAuthorizationService authz;

    @Override
    @Transactional(readOnly = true)
    public Page<PartnerOrderResponse> getPartnerOrders(Long partnerId, Pageable pageable) {
        authz.requireOrderRead(partnerId);
        return partnerOrderRepository.findByPartnerId(partnerId, pageable).map(PartnerOrderResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerOrderResponse getPartnerOrder(Long partnerId, Long partnerOrderId) {
        authz.requireOrderRead(partnerId);
        PartnerOrder po = partnerOrderRepository.findByIdAndPartnerId(partnerOrderId, partnerId)
                .orElseThrow(() -> new EntityNotFoundException("partner_order_not_found"));
        return PartnerOrderResponse.from(po);
    }

    @Override
    @Transactional
    public PartnerOrderResponse acceptOrder(Long partnerId, Long partnerOrderId) {
        authz.requireOrderFulfillment(partnerId);
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
        authz.requireOrderFulfillment(partnerId);
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
        authz.requireOrderFulfillment(partnerId);
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
        authz.requireOrderFulfillment(partnerId);
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
        authz.requireOrderFulfillment(partnerId);
        PartnerOrder po = findOwned(partnerId, partnerOrderId);

        if (po.getStatus() != PartnerOrderStatus.READY_TO_SHIP) {
            throw new InvalidInputException("cannot_ship_in_current_status");
        }
        po.setStatus(PartnerOrderStatus.SHIPPED);
        po.setShippedAt(LocalDateTime.now());
        return PartnerOrderResponse.from(partnerOrderRepository.save(po));
    }

    @Override
    @Transactional
    public PartnerOrderResponse deliverOrder(Long partnerId, Long partnerOrderId) {
        authz.requireOrderFulfillment(partnerId);
        PartnerOrder po = findOwned(partnerId, partnerOrderId);

        if (po.getStatus() != PartnerOrderStatus.SHIPPED) {
            throw new InvalidInputException("cannot_deliver_in_current_status");
        }
        po.setStatus(PartnerOrderStatus.DELIVERED);
        po.setDeliveredAt(LocalDateTime.now());
        return PartnerOrderResponse.from(partnerOrderRepository.save(po));
    }

    @Override
    @Transactional
    public PartnerOrderResponse cancelOrder(Long partnerId, Long partnerOrderId, String reason) {
        authz.requireOrderFulfillment(partnerId);
        PartnerOrder po = findOwned(partnerId, partnerOrderId);

        if (po.getStatus() != PartnerOrderStatus.NEW
                && po.getStatus() != PartnerOrderStatus.ACCEPTED) {
            throw new InvalidInputException("cannot_cancel_in_current_status");
        }
        po.setStatus(PartnerOrderStatus.CANCELLED);
        po.setCancelledAt(LocalDateTime.now());
        po.setCancelReason(reason);
        return PartnerOrderResponse.from(partnerOrderRepository.save(po));
    }

    @Override
    @Transactional
    public PartnerOrderResponse requestReturn(Long partnerId, Long partnerOrderId, String reason) {
        authz.requireOrderFulfillment(partnerId);
        PartnerOrder po = findOwned(partnerId, partnerOrderId);

        if (po.getStatus() != PartnerOrderStatus.DELIVERED) {
            throw new InvalidInputException("cannot_request_return_in_current_status");
        }
        po.setStatus(PartnerOrderStatus.RETURN_REQUESTED);
        po.setCancelReason(reason);
        return PartnerOrderResponse.from(partnerOrderRepository.save(po));
    }

    @Override
    @Transactional
    public PartnerOrderResponse approveReturn(Long partnerId, Long partnerOrderId) {
        authz.requireOrderFulfillment(partnerId);
        PartnerOrder po = findOwned(partnerId, partnerOrderId);

        if (po.getStatus() != PartnerOrderStatus.RETURN_REQUESTED) {
            throw new InvalidInputException("cannot_approve_return_in_current_status");
        }
        po.setStatus(PartnerOrderStatus.RETURNED);
        return PartnerOrderResponse.from(partnerOrderRepository.save(po));
    }

    private PartnerOrder findOwned(Long partnerId, Long partnerOrderId) {
        return partnerOrderRepository.findByIdAndPartnerId(partnerOrderId, partnerId)
                .orElseThrow(() -> new EntityNotFoundException("partner_order_not_found"));
    }
}
