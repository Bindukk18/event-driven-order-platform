package com.lab.orders.outbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

	@Id
	@Column(name = "event_id", nullable = false)
	private UUID eventId;

	@Column(name = "topic", nullable = false, length = 64)
	private String topic;

	@Column(name = "aggregate_type", nullable = false, length = 64)
	private String aggregateType;

	@Column(name = "aggregate_id", nullable = false)
	private UUID aggregateId;

	@Column(name = "aggregate_version", nullable = false)
	private int aggregateVersion;

	@Column(name = "event_type", nullable = false, length = 64)
	private String eventType;

	@Column(name = "event_version", nullable = false)
	private int eventVersion;

	@Column(name = "correlation_id", nullable = false, length = 128)
	private String correlationId;

	@Column(name = "causation_id")
	private UUID causationId;

	@Column(name = "payload", nullable = false, columnDefinition = "TEXT")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private OutboxStatus status;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;

	@Column(name = "claimed_until")
	private Instant claimedUntil;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "last_error", length = 512)
	private String lastError;

	protected OutboxEvent() {
	}

	public OutboxEvent(
			UUID eventId,
			String topic,
			String aggregateType,
			UUID aggregateId,
			int aggregateVersion,
			String eventType,
			int eventVersion,
			String correlationId,
			UUID causationId,
			String payload,
			OutboxStatus status,
			int attemptCount,
			Instant nextAttemptAt,
			Instant createdAt) {
		this.eventId = eventId;
		this.topic = topic;
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.aggregateVersion = aggregateVersion;
		this.eventType = eventType;
		this.eventVersion = eventVersion;
		this.correlationId = correlationId;
		this.causationId = causationId;
		this.payload = payload;
		this.status = status;
		this.attemptCount = attemptCount;
		this.nextAttemptAt = nextAttemptAt;
		this.createdAt = createdAt;
	}

	public UUID getEventId() {
		return eventId;
	}

	public String getTopic() {
		return topic;
	}

	public String getPayload() {
		return payload;
	}

	public OutboxStatus getStatus() {
		return status;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getEventType() {
		return eventType;
	}

	public void markInFlight(Instant claimedUntil, int attemptCount) {
		this.status = OutboxStatus.IN_FLIGHT;
		this.claimedUntil = claimedUntil;
		this.attemptCount = attemptCount;
		this.lastError = null;
	}

	public void markPublished(Instant publishedAt) {
		this.status = OutboxStatus.PUBLISHED;
		this.publishedAt = publishedAt;
		this.claimedUntil = null;
		this.lastError = null;
	}

	public void markRetry(Instant nextAttemptAt, String lastError) {
		this.status = OutboxStatus.PENDING;
		this.nextAttemptAt = nextAttemptAt;
		this.claimedUntil = null;
		this.lastError = truncate(lastError);
	}

	public void markFailed(String lastError) {
		this.status = OutboxStatus.FAILED;
		this.claimedUntil = null;
		this.lastError = truncate(lastError);
	}

	private static String truncate(String error) {
		if (error == null) {
			return null;
		}
		return error.length() <= 512 ? error : error.substring(0, 512);
	}
}
