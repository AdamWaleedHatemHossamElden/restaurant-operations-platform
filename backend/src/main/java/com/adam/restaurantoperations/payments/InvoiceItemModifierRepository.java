package com.adam.restaurantoperations.payments;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceItemModifierRepository extends JpaRepository<InvoiceItemModifierEntity, Long> {
    List<InvoiceItemModifierEntity> findByInvoiceItemIdOrderByDisplayOrderAscIdAsc(Long invoiceItemId);
}
