package com.lab.orders.payment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_attempt")
public class PaymentAttempt {

	@Id
	@Column(name = "attempt_id", nullable = false)
	private UUID attemptId;

	@Column(name = "order_id", nullable = false)
	private UUID orderId;

	@Column(name = "source_event_id", nullable = false)
	private UUID sourceEventId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private PaymentAttemptStatus status;

	@Column(name = "last_error", length = 200)
	private String lastError;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PaymentAttempt() {
	}

	public PaymentAttempt(
			UUID attemptId,
			UUID orderId,
			UUID sourceEventId,
			PaymentAttemptStatus status,
			String lastError,
			Instant createdAt) {
		this.attemptId = attemptId;
		this.orderId = orderId;
		this.sourceEventId = sourceEventId;
		this.status = status;
		this.lastError = lastError;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

	public UUID getAttemptId() {
		return attemptId;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public PaymentAttemptStatus getStatus() {
		return status;
	}

	public String getLastError() {
		return lastError;
	}
}
