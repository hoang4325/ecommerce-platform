package com.yashmerino.ecommerce.repository;

import com.yashmerino.ecommerce.model.operations.PaymentOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOperationRepository extends JpaRepository<PaymentOperation, Long> {
    Optional<PaymentOperation> findByMainPaymentId(Long mainPaymentId);

    @Query(value = "SELECT * FROM payment_operations WHERE status='RECEIVED' OR (status IN ('UNKNOWN','PROCESSING') AND claimed_at < :leaseExpiry) ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<PaymentOperation> claimReceivedOperations(@Param("leaseExpiry") LocalDateTime leaseExpiry, @Param("limit") int limit);
}
