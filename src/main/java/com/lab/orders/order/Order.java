package com.lab.orders.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {

	@Id
	@Column(name = "order_id", nullable = false)
	private UUID orderId;

	@Column(name = "customer_id", nullable = false, length = 128)
	private String customerId;

	@Column(name = "sku", nullable = false, length = 64)
	private String sku;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	@Column(name = "amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private OrderStatus status;

	@Column(name = "last_applied_version", nullable = false)
	private int lastAppliedVersion;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Order() {
	}

	public Order(
			UUID orderId,
			String customerId,
			String sku,
			int quantity,
			BigDecimal amount,
			OrderStatus status,
			int lastAppliedVersion,
			Instant createdAt) {
		this.orderId = orderId;
		this.customerId = customerId;
		this.sku = sku;
		this.quantity = quantity;
		this.amount = amount;
		this.status = status;
		this.lastAppliedVersion = lastAppliedVersion;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public String getCustomerId() {
		return customerId;
	}

	public String getSku() {
		return sku;
	}

	public int getQuantity() {
		return quantity;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public int getLastAppliedVersion() {
		return lastAppliedVersion;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void apply(OrderStatus status, int version, Instant now) {
		this.status = status;
		this.lastAppliedVersion = version;
		this.updatedAt = now;
	}
}
