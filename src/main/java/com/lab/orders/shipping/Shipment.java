package com.lab.orders.shipping;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "shipment")
public class Shipment {

	@Id
	@Column(name = "shipment_id", nullable = false)
	private UUID shipmentId;

	@Column(name = "order_id", nullable = false)
	private UUID orderId;

	@Column(name = "status", nullable = false, length = 32)
	private String status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Shipment() {
	}

	public Shipment(UUID shipmentId, UUID orderId, String status, Instant createdAt) {
		this.shipmentId = shipmentId;
		this.orderId = orderId;
		this.status = status;
		this.createdAt = createdAt;
	}

	public UUID getShipmentId() {
		return shipmentId;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public String getStatus() {
		return status;
	}
}
