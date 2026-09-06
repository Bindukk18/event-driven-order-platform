package com.lab.orders.inventory;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_reservation")
public class InventoryReservation {

	@Id
	@Column(name = "reservation_id", nullable = false)
	private UUID reservationId;

	@Column(name = "order_id", nullable = false)
	private UUID orderId;

	@Column(name = "sku", nullable = false, length = 64)
	private String sku;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private ReservationStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected InventoryReservation() {
	}

	public InventoryReservation(
			UUID reservationId,
			UUID orderId,
			String sku,
			int quantity,
			ReservationStatus status,
			Instant createdAt) {
		this.reservationId = reservationId;
		this.orderId = orderId;
		this.sku = sku;
		this.quantity = quantity;
		this.status = status;
		this.createdAt = createdAt;
	}

	public UUID getReservationId() {
		return reservationId;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public ReservationStatus getStatus() {
		return status;
	}
}
