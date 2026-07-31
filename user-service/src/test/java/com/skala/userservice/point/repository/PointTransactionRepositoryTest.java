package com.skala.userservice.point.repository;

import com.skala.userservice.point.domain.PointTransaction;
import com.skala.userservice.point.domain.PointTransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class PointTransactionRepositoryTest {

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    @Test
    @DisplayName("requestId로 포인트 처리 이력을 조회한다")
    void findByRequestId() {
        PointTransaction transaction = transaction(1L, "REQUEST-1");
        pointTransactionRepository.saveAndFlush(transaction);

        assertThat(pointTransactionRepository.findByRequestId("REQUEST-1"))
                .contains(transaction);
    }

    @Test
    @DisplayName("requestId에는 DB 유니크 제약조건이 적용된다")
    void requestIdIsUnique() {
        pointTransactionRepository.saveAndFlush(transaction(1L, "REQUEST-1"));

        assertThatThrownBy(() -> pointTransactionRepository.saveAndFlush(transaction(2L, "REQUEST-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private PointTransaction transaction(Long customerId, String requestId) {
        return PointTransaction.create(
                customerId, requestId, PointTransactionType.DEDUCT, 1_000L, 9_000L
        );
    }
}
