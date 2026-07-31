package com.skala.userservice.point.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "point_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_point_transactions_request_id",
                columnNames = "request_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @NotBlank
    @Size(max = 100)
    @Column(name = "request_id", nullable = false, unique = true, length = 100)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointTransactionType type;

    @Positive
    @Column(nullable = false)
    private long amount;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private PointTransaction(
            Long customerId,
            String requestId,
            PointTransactionType type,
            long amount,
            long balanceAfter
    ) {
        this.customerId = customerId;
        this.requestId = requestId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    public static PointTransaction create(
            Long customerId,
            String requestId,
            PointTransactionType type,
            long amount,
            long balanceAfter
    ) {
        return new PointTransaction(customerId, requestId, type, amount, balanceAfter);
    }

    public boolean matches(Long customerId, PointTransactionType type, long amount) {
        return this.customerId.equals(customerId) && this.type == type && this.amount == amount;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
