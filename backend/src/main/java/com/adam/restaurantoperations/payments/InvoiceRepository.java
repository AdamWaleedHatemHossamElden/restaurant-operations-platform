package com.adam.restaurantoperations.payments;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InvoiceRepository
        extends JpaRepository<InvoiceEntity, Long>, JpaSpecificationExecutor<InvoiceEntity> {
    boolean existsByInvoiceNumber(String invoiceNumber);
    Optional<InvoiceEntity> findByOrderId(Long orderId);
}
