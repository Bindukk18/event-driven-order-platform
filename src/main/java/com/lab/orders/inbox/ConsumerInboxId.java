package com.lab.orders.inbox;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ConsumerInboxId implements Serializable {

	private String consumerName;
	private UUID eventId;

	public ConsumerInboxId() {
	}

	public ConsumerInboxId(String consumerName, UUID eventId) {
		this.consumerName = consumerName;
		this.eventId = eventId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ConsumerInboxId that)) {
			return false;
		}
		return Objects.equals(consumerName, that.consumerName) && Objects.equals(eventId, that.eventId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(consumerName, eventId);
	}
}
