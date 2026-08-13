package com.adam.restaurantoperations.payments;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository
        extends JpaRepository<PaymentEntity, Long>, JpaSpecificationExecutor<PaymentEntity> {
    boolean existsByPaymentNumber(String paymentNumber);

    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentEntity> findByExternalReference(String externalReference);

    List<PaymentEntity> findByOrderIdOrderByReceivedAtAscIdAsc(Long orderId);

    @Query(value = """
            SELECT COALESCE(SUM(amount), 0.00)
            FROM payments
            WHERE order_id = :orderId AND status = 'SUCCEEDED'
            """, nativeQuery = true)
    BigDecimal successfulTotal(@Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from PaymentEntity payment where payment.id = :id")
    Optional<PaymentEntity> findByIdForUpdate(@Param("id") Long id);
}
