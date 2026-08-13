package com.adam.restaurantoperations.payments;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentReconciliationRepository
        extends JpaRepository<PaymentReconciliationEntity, Long> {
    Optional<PaymentReconciliationEntity> findByPaymentId(Long paymentId);
}
