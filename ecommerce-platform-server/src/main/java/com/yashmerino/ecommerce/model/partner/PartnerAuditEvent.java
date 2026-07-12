package com.yashmerino.ecommerce.model.partner;

import com.yashmerino.ecommerce.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "partner_audit_events")
@Getter
@Setter
@NoArgsConstructor
public class PartnerAuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(length = 30)
    private String fromState;

    @Column(length = 30)
    private String toState;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(length = 1000)
    private String reason;

    @Column(length = 100)
    private String correlationId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    void setOccurredAt() {
        occurredAt = LocalDateTime.now();
    }
}

