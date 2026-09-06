package com.lab.orders.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * One consumer-group delivery of one event. LAB / TEST BROKER state.
 */
public class BrokerDelivery {

	private final UUID deliveryId;
	private final String topic;
	private final String consumerName;
	private final EventEnvelope event;
	private int attemptCount;
	private Instant nextAttemptAt;
	private DeliveryStatus status;
	private String lastError;

	public BrokerDelivery(
			UUID deliveryId,
			String topic,
			String consumerName,
			EventEnvelope event,
			Instant nextAttemptAt) {
		this.deliveryId = deliveryId;
		this.topic = topic;
		this.consumerName = consumerName;
		this.event = event;
		this.attemptCount = 0;
		this.nextAttemptAt = nextAttemptAt;
		this.status = DeliveryStatus.PENDING;
	}

	public UUID getDeliveryId() {
		return deliveryId;
	}

	public String getTopic() {
		return topic;
	}

	public String getConsumerName() {
		return consumerName;
	}

	public EventEnvelope getEvent() {
		return event;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public Instant getNextAttemptAt() {
		return nextAttemptAt;
	}

	public DeliveryStatus getStatus() {
		return status;
	}

	public String getLastError() {
		return lastError;
	}

	public void incrementAttempt() {
		this.attemptCount++;
	}

	public void scheduleRetry(Instant nextAttemptAt, String lastError) {
		this.status = DeliveryStatus.PENDING;
		this.nextAttemptAt = nextAttemptAt;
		this.lastError = lastError;
	}

	public void ack() {
		this.status = DeliveryStatus.ACKED;
		this.lastError = null;
	}

	public void deadLetter(String lastError) {
		this.status = DeliveryStatus.DEAD_LETTERED;
		this.lastError = lastError;
	}
}
