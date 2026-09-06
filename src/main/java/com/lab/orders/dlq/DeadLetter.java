package com.lab.orders.dlq;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dead_letter")
public class DeadLetter {

	@Id
	@Column(name = "dead_letter_id", nullable = false)
	private UUID deadLetterId;

	@Column(name = "event_id", nullable = false)
	private UUID eventId;

	@Column(name = "consumer_name", nullable = false, length = 128)
	private String consumerName;

	@Column(name = "original_event", nullable = false, columnDefinition = "TEXT")
	private String originalEvent;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "reason", nullable = false, length = 200)
	private String reason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected DeadLetter() {
	}

	public DeadLetter(
			UUID deadLetterId,
			UUID eventId,
			String consumerName,
			String originalEvent,
			int attemptCount,
			String reason,
			Instant createdAt) {
		this.deadLetterId = deadLetterId;
		this.eventId = eventId;
		this.consumerName = consumerName;
		this.originalEvent = originalEvent;
		this.attemptCount = attemptCount;
		this.reason = reason;
		this.createdAt = createdAt;
	}

	public UUID getEventId() {
		return eventId;
	}

	public String getConsumerName() {
		return consumerName;
	}

	public String getOriginalEvent() {
		return originalEvent;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public String getReason() {
		return reason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
