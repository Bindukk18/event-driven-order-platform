package com.lab.orders.shipping;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

	Optional<Shipment> findByOrderId(UUID orderId);

	long countByOrderId(UUID orderId);
}
