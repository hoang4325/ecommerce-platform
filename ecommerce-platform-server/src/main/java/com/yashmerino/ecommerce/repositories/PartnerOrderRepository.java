package com.yashmerino.ecommerce.repositories;

import com.yashmerino.ecommerce.model.order.PartnerOrder;
import com.yashmerino.ecommerce.model.order.PartnerOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerOrderRepository extends JpaRepository<PartnerOrder, Long> {

    Page<PartnerOrder> findByPartnerId(Long partnerId, Pageable pageable);

    Optional<PartnerOrder> findByIdAndPartnerId(Long id, Long partnerId);

    List<PartnerOrder> findByOrderId(Long orderId);
}
