package com.ticketing.domain.repository;

import com.ticketing.domain.entity.Order;
import com.ticketing.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByUserId(UUID userId);

    @Query("SELECT o FROM Order o JOIN FETCH o.seat JOIN FETCH o.user WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") UUID id);

    List<Order> findByStatus(OrderStatus status);
}
