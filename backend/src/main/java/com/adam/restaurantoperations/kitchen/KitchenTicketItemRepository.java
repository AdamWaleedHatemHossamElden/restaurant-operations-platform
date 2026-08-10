package com.adam.restaurantoperations.kitchen;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KitchenTicketItemRepository extends JpaRepository<KitchenTicketItemEntity, Long> {
    @Query("""
            select kitchenItem
            from KitchenTicketItemEntity kitchenItem
            where kitchenItem.ticket.id = :ticketId
            order by kitchenItem.orderItem.displayOrder, kitchenItem.id
            """)
    List<KitchenTicketItemEntity> findOrderedByTicketId(@Param("ticketId") Long ticketId);

    Optional<KitchenTicketItemEntity> findByIdAndTicketId(Long id, Long ticketId);
}
