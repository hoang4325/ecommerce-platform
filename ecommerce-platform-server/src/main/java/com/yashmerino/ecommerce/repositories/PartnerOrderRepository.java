package com.yashmerino.ecommerce.repositories;

import com.yashmerino.ecommerce.model.order.PartnerOrder;
import com.yashmerino.ecommerce.model.order.PartnerOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PartnerOrderRepository extends JpaRepository<PartnerOrder, Long> {

    Page<PartnerOrder> findByPartnerId(Long partnerId, Pageable pageable);

    Optional<PartnerOrder> findByIdAndPartnerId(Long id, Long partnerId);

    List<PartnerOrder> findByOrderId(Long orderId);

    List<PartnerOrder> findByPartnerIdAndStatusAndDeliveredAtBetween(
            Long partnerId, PartnerOrderStatus status, LocalDateTime start, LocalDateTime end);

    @Query("SELECT po FROM PartnerOrder po WHERE po.partner.id = :partnerId AND po.status = :status AND po.deliveredAt >= :periodStart AND po.deliveredAt < :periodEnd")
    List<PartnerOrder> findByPartnerIdAndStatusAndDeliveredAtInRange(
            @Param("partnerId") Long partnerId,
            @Param("status") PartnerOrderStatus status,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT po FROM PartnerOrder po WHERE po.partner.id = :partnerId AND po.status = :status AND po.settlementStatus = 'UNSETTLED' AND po.deliveredAt >= :periodStart AND po.deliveredAt < :periodEnd")
    List<PartnerOrder> findByPartnerIdAndStatusAndDeliveredAtInRangeAndUnsettledForUpdate(
            @Param("partnerId") Long partnerId,
            @Param("status") PartnerOrderStatus status,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd);

    @Modifying
    @Query("UPDATE PartnerOrder po SET po.settlementStatus = 'SETTLED', po.settlementId = :settlementId WHERE po.id IN :orderIds AND po.settlementStatus = 'UNSETTLED'")
    int markAsSettled(@Param("settlementId") Long settlementId, @Param("orderIds") List<Long> orderIds);
}
