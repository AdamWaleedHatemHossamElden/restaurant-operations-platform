package com.adam.restaurantoperations.payments;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItemEntity, Long> {
    List<InvoiceItemEntity> findByInvoiceIdOrderByDisplayOrderAscIdAsc(Long invoiceId);
}
