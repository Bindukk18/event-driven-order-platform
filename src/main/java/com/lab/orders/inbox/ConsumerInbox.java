package com.lab.orders.inbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "consumer_inbox")
@IdClass(ConsumerInboxId.class)
public class ConsumerInbox {

	@Id
	@Column(name = "consumer_name", nullable = false, length = 128)
	private String consumerName;

	@Id
	@Column(name = "event_id", nullable = false)
	private UUID eventId;

	@Column(name = "processed_at", nullable = false)
	private Instant processedAt;

	protected ConsumerInbox() {
	}

	public ConsumerInbox(String consumerName, UUID eventId, Instant processedAt) {
		this.consumerName = consumerName;
		this.eventId = eventId;
		this.processedAt = processedAt;
	}
}
