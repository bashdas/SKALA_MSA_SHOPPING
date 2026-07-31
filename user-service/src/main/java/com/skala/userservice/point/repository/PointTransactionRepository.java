package com.skala.userservice.point.repository;

import com.skala.userservice.point.domain.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    Optional<PointTransaction> findByRequestId(String requestId);
}
