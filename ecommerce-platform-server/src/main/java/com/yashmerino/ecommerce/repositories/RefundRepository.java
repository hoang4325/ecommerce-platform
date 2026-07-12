package com.yashmerino.ecommerce.repositories;

import com.yashmerino.ecommerce.model.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    boolean existsByRequestIdempotencyKey(String requestIdempotencyKey);
    java.util.Optional<Refund> findByRequestIdempotencyKey(String requestIdempotencyKey);
}
